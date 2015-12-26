package com.mebigfatguy.fbcontrib.detect;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.generic.Type;

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

    enum SEOUserValue { METHOD };
    
    private BugReporter bugReporter;
    private OpcodeStack stack;
    private int conditionalTarget;
    private boolean sawMethod;
    
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
        sawMethod = false;
        super.visitCode(obj);
    }
    
    @Override
    public void sawOpcode(int seen) {
        SEOUserValue userValue = null;
        
        if ((conditionalTarget != -1) && (getPC() >= conditionalTarget)) {
            conditionalTarget = -1;
            sawMethod = false;
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
                        sawMethod = false;
                        return;
                    }

                    userValue = SEOUserValue.METHOD;
                    break;
                    
                case LCMP:
                case FCMPL:
                case FCMPG:
                case DCMPL:
                case DCMPG:
                    if (stack.getStackDepth() >= 2) {
                        OpcodeStack.Item itm = stack.getStackItem(0);
                        if (itm.getUserValue() == SEOUserValue.METHOD) {
                            userValue = SEOUserValue.METHOD;
                        } else {
                            itm = stack.getStackItem(1);
                            if (itm.getUserValue() == SEOUserValue.METHOD) {
                                userValue = SEOUserValue.METHOD;
                            }
                        }
                    } else {
                        sawMethod = false;
                    }
                    break;
                
                case IFEQ:
                case IFNE:
                case IFLT:
                case IFGE:
                case IFGT:
                case IFLE:
                case IF_ICMPEQ:
                case IF_ICMPNE:
                case IF_ICMPLT:
                case IF_ICMPGE:
                case IF_ICMPGT:
                case IF_ICMPLE:
                case IF_ACMPEQ:
                case IF_ACMPNE:
                case IFNULL:
                case IFNONNULL:
                    if (conditionalTarget < 0) {
                        conditionalTarget = getBranchTarget();
                    } else if (conditionalTarget != getBranchTarget()) {
                        conditionalTarget = -1;
                        sawMethod = false;
                        return;
                    }
                    
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item itm = stack.getStackItem(0);
                        
                        if (itm.getUserValue() == SEOUserValue.METHOD) {
                            sawMethod = true;
                        } else if (sawMethod) {
                            bugReporter.reportBug(new BugInstance(this, BugType.SEO_SUBOPTIMAL_EXPRESSION_ORDER.name(), NORMAL_PRIORITY)
                                    .addClass(this)
                                    .addMethod(this)
                                    .addSourceLine(this));
                            sawMethod = false;
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
                    sawMethod = false;
                    conditionalTarget = -1;
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
    
    private boolean isMethodInvolved(int seen) {
        return false;
    }
}
