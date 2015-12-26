package com.mebigfatguy.fbcontrib.detect;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.collect.MethodInfo;
import com.mebigfatguy.fbcontrib.collect.Statistics;
import com.mebigfatguy.fbcontrib.utils.BugType;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for conditional expressions where both simple local variable (in)equalities are used
 * along with method calls, where the method calls are done first. By placing the simple local 
 * checks first, you eliminate potentially costly calls in some cases. This assumes that the methods
 * called won't have side-effects that are desired. At present it only looks for simple sequences
 * of 'and' based conditions.
 */
@CustomUserValue
public class SuboptimalExpressionOrder extends BytecodeScanningDetector {

    private static final int NORMAL_WEIGHT_LIMIT = 50;
    
    private BugReporter bugReporter;
    private OpcodeStack stack;
    private int conditionalTarget;
    private int sawMethodWeight;
    
    /**
    * constructs a SEO detector given the reporter to report bugs on
    * 
    * @param bugReporter
    *            the sync of bug reports
    */
    public SuboptimalExpressionOrder(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }
    
    @Override
    public void visitClassContext(ClassContext clsContext) {
        try {
            stack = new OpcodeStack();
            super.visitClassContext(clsContext);
        } finally {
            stack = null;
        }
    }
    
    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        conditionalTarget = -1;
        sawMethodWeight = 0;
        super.visitCode(obj);
    }
    
    @Override
    public void sawOpcode(int seen) {
        Integer userValue = null;
        
        if ((conditionalTarget != -1) && (getPC() >= conditionalTarget)) {
            conditionalTarget = -1;
            sawMethodWeight = 0;
        }
        
        try {
            switch (seen) {
                case INVOKESPECIAL:
                case INVOKESTATIC:
                case INVOKEINTERFACE:
                case INVOKEVIRTUAL:
                    String signature = getSigConstantOperand();
                    Type t = Type.getReturnType(signature);
                    if (t == Type.VOID) {
                        sawMethodWeight = 0;
                        return;
                    }

                    MethodInfo mi = Statistics.getStatistics().getMethodStatistics(getClassConstantOperand(), getNameConstantOperand(), signature);
                    if ((mi == null) || (mi.getNumBytes() == 0)) {
                        userValue = Integer.valueOf(NORMAL_WEIGHT_LIMIT);
                    } else {
                        userValue = Integer.valueOf(mi.getNumBytes());
                    }
                    break;
                    
                case LCMP:
                case FCMPL:
                case FCMPG:
                case DCMPL:
                case DCMPG:
                    if (stack.getStackDepth() >= 2) {
                        for (int i = 0; i <= 1; i++) {
                            OpcodeStack.Item itm = stack.getStackItem(i);
                            userValue = (Integer) itm.getUserValue();
                            if (userValue != null) {
                                break;
                            }
                        }
                    } else {
                        sawMethodWeight = 0;
                    }
                    break;
                    
                case IF_ICMPEQ:
                case IF_ICMPNE:
                case IF_ICMPLT:
                case IF_ICMPGE:
                case IF_ICMPGT:
                case IF_ICMPLE:
                case IF_ACMPEQ:
                case IF_ACMPNE:
                    if (conditionalTarget < 0) {
                        conditionalTarget = getBranchTarget();
                    } else if (conditionalTarget != getBranchTarget()) {
                        conditionalTarget = -1;
                        sawMethodWeight = 0;
                        return;
                    }
                    
                    if (stack.getStackDepth() >= 2) {
                        int expWeight = 0;
                        for (int i = 0; i <= 1; i++) {
                            OpcodeStack.Item itm = stack.getStackItem(i);
                        
                            Integer uv = (Integer) itm.getUserValue();
                            if (uv != null) {
                                expWeight = Math.max(uv.intValue(), expWeight);
                            }
                        }
                        
                        if ((expWeight == 0) && sawMethodWeight > 0) {
                            bugReporter.reportBug(new BugInstance(this, BugType.SEO_SUBOPTIMAL_EXPRESSION_ORDER.name(), sawMethodWeight >= NORMAL_WEIGHT_LIMIT ? NORMAL_PRIORITY : LOW_PRIORITY)
                                    .addClass(this)
                                    .addMethod(this)
                                    .addSourceLine(this));
                            sawMethodWeight = 0;
                            conditionalTarget = Integer.MAX_VALUE;
                        } else {
                            sawMethodWeight = Math.max(sawMethodWeight, expWeight);
                        }
                    }
                    break;
                
                case IFEQ:
                case IFNE:
                case IFLT:
                case IFGE:
                case IFGT:
                case IFLE:
                case IFNULL:
                case IFNONNULL:
                    if (conditionalTarget < 0) {
                        conditionalTarget = getBranchTarget();
                    } else if (conditionalTarget != getBranchTarget()) {
                        conditionalTarget = -1;
                        sawMethodWeight = 0;
                        return;
                    }
                    
                    if (stack.getStackDepth() >= 1) {
                        OpcodeStack.Item itm = stack.getStackItem(0);
                        
                        Integer uv = (Integer) itm.getUserValue();
                        if (uv != null) {
                            sawMethodWeight = Math.max(sawMethodWeight, uv.intValue());
                        } else if (sawMethodWeight > 0) {
                            bugReporter.reportBug(new BugInstance(this, BugType.SEO_SUBOPTIMAL_EXPRESSION_ORDER.name(), sawMethodWeight >= NORMAL_WEIGHT_LIMIT ? NORMAL_PRIORITY : LOW_PRIORITY)
                                    .addClass(this)
                                    .addMethod(this)
                                    .addSourceLine(this));
                            sawMethodWeight = 0;
                            conditionalTarget = Integer.MAX_VALUE;
                        }
                    }
                    break;

                case ISTORE:
                case LSTORE:
                case FSTORE:
                case DSTORE:
                case ASTORE:
                case ISTORE_0:
                case ISTORE_1:
                case ISTORE_2:
                case ISTORE_3:
                case LSTORE_0:
                case LSTORE_1:
                case LSTORE_2:
                case LSTORE_3:
                case FSTORE_0:
                case FSTORE_1:
                case FSTORE_2:
                case FSTORE_3:
                case DSTORE_0:
                case DSTORE_1:
                case DSTORE_2:
                case DSTORE_3:
                case ASTORE_0:
                case ASTORE_1:
                case ASTORE_2:
                case ASTORE_3:
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item itm = stack.getStackItem(0);
                        itm.setUserValue(null);
                    }
                    sawMethodWeight = 0;
                    conditionalTarget = -1;
                    break;
                    
                case INSTANCEOF:
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item itm = stack.getStackItem(0);
                        userValue = (Integer) itm.getUserValue();
                    }
                    break;
            }
        } finally {
            stack.sawOpcode(this, seen);
            if ((userValue != null) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item itm = stack.getStackItem(0);
                itm.setUserValue(userValue);
            }
        }
    }
}
