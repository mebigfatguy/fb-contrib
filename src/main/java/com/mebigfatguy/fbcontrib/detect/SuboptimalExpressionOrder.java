/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2019 Dave Brosius
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.mebigfatguy.fbcontrib.detect;

import org.apache.bcel.classfile.Code;

import com.mebigfatguy.fbcontrib.collect.MethodInfo;
import com.mebigfatguy.fbcontrib.collect.Statistics;
import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for conditional expressions where both simple local variable (in)equalities are used along with method calls, where the method calls are done first. By
 * placing the simple local checks first, you eliminate potentially costly calls in some cases. This assumes that the methods called won't have side-effects
 * that are desired. At present it only looks for simple sequences of 'and' based conditions.
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

    /**
     * overrides the visitor to setup the opcode stack
     *
     * @param clsContext
     *            the context object of the currently parse class
     */
    @Override
    public void visitClassContext(ClassContext clsContext) {
        try {
            stack = new OpcodeStack();
            super.visitClassContext(clsContext);
        } finally {
            stack = null;
        }
    }

    /**
     * overrides the visitor to reset the opcode stack, and initialize vars
     *
     * @param obj
     *            the code object of the currently parsed method
     */
    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        conditionalTarget = -1;
        sawMethodWeight = 0;
        super.visitCode(obj);
    }

    /**
     * overrides the visitor to look for chains of expressions joined by 'and' that have method calls before simple local variable conditions
     *
     * @param seen
     *            the currently parse opcode
     */
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
                    if (Values.SIG_VOID.equals(SignatureUtils.getReturnSignature(signature))) {
                        sawMethodWeight = 0;
                        return;
                    }
                    
                    for (String parmSig : SignatureUtils.getParameterSignatures(signature)) {
                    	if (parmSig.charAt(0) == '[') {
                    		sawMethodWeight = 0;
                    		return;
                    	}
                    }

                    String clsName = getClassConstantOperand();
                    MethodInfo mi = Statistics.getStatistics().getMethodStatistics(clsName, getNameConstantOperand(), signature);
                    if ((mi == null) || (mi.getNumBytes() == 0)) {
                        userValue = Values.ONE;
                    } else {
                        userValue = Integer.valueOf(mi.getNumBytes());
                    }
                break;

                case LCMP:
                case FCMPL:
                case FCMPG:
                case DCMPL:
                case DCMPG:
                case IAND:
                case IOR:
                case IXOR:
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

                        if ((expWeight == 0) && (sawMethodWeight > 0)) {
                            bugReporter.reportBug(new BugInstance(this, BugType.SEO_SUBOPTIMAL_EXPRESSION_ORDER.name(),
                                    sawMethodWeight >= NORMAL_WEIGHT_LIMIT ? NORMAL_PRIORITY : LOW_PRIORITY).addClass(this).addMethod(this)
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
                        if (uv == null) {
                            if (sawMethodWeight > 0) {
                                bugReporter.reportBug(new BugInstance(this, BugType.SEO_SUBOPTIMAL_EXPRESSION_ORDER.name(),
                                        sawMethodWeight >= NORMAL_WEIGHT_LIMIT ? NORMAL_PRIORITY : LOW_PRIORITY).addClass(this).addMethod(this)
                                                .addSourceLine(this));
                                sawMethodWeight = 0;
                                conditionalTarget = Integer.MAX_VALUE;
                            }
                        } else {
                            sawMethodWeight = Math.max(sawMethodWeight, uv.intValue());
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

                case ATHROW:
                case POP:
                case POP2:
                case GOTO:
                case GOTO_W:
                case PUTFIELD:
                case PUTSTATIC:
                case IINC:
                case INSTANCEOF:
                case RETURN:
                case ARETURN:
                case IRETURN:
                case LRETURN:
                case FRETURN:
                case DRETURN:
                    sawMethodWeight = 0;
                    conditionalTarget = -1;
                break;

                case ARRAYLENGTH:
                case CHECKCAST:
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item itm = stack.getStackItem(0);
                        userValue = (Integer) itm.getUserValue();
                    }
                break;

                case GETFIELD:
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item itm = stack.getStackItem(0);
                        if (itm.getReturnValueOf() != null) {
                            sawMethodWeight = 0;
                            conditionalTarget = -1;
                        }
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
