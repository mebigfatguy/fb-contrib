/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2018 Dave Brosius
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

import org.apache.bcel.Const;
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
                case Const.INVOKESPECIAL:
                case Const.INVOKESTATIC:
                case Const.INVOKEINTERFACE:
                case Const.INVOKEVIRTUAL:
                    String signature = getSigConstantOperand();
                    if (Values.SIG_VOID.equals(SignatureUtils.getReturnSignature(signature))) {
                        sawMethodWeight = 0;
                        return;
                    }

                    String clsName = getClassConstantOperand();
                    MethodInfo mi = Statistics.getStatistics().getMethodStatistics(clsName, getNameConstantOperand(), signature);
                    if ((mi == null) || (mi.getNumBytes() == 0)) {
                        userValue = Values.ONE;
                    } else {
                        userValue = Integer.valueOf(mi.getNumBytes());
                    }
                break;

                case Const.LCMP:
                case Const.FCMPL:
                case Const.FCMPG:
                case Const.DCMPL:
                case Const.DCMPG:
                case Const.IAND:
                case Const.IOR:
                case Const.IXOR:
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

                case Const.IF_ICMPEQ:
                case Const.IF_ICMPNE:
                case Const.IF_ICMPLT:
                case Const.IF_ICMPGE:
                case Const.IF_ICMPGT:
                case Const.IF_ICMPLE:
                case Const.IF_ACMPEQ:
                case Const.IF_ACMPNE:
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

                case Const.IFEQ:
                case Const.IFNE:
                case Const.IFLT:
                case Const.IFGE:
                case Const.IFGT:
                case Const.IFLE:
                case Const.IFNULL:
                case Const.IFNONNULL:
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

                case Const.ISTORE:
                case Const.LSTORE:
                case Const.FSTORE:
                case Const.DSTORE:
                case Const.ASTORE:
                case Const.ISTORE_0:
                case Const.ISTORE_1:
                case Const.ISTORE_2:
                case Const.ISTORE_3:
                case Const.LSTORE_0:
                case Const.LSTORE_1:
                case Const.LSTORE_2:
                case Const.LSTORE_3:
                case Const.FSTORE_0:
                case Const.FSTORE_1:
                case Const.FSTORE_2:
                case Const.FSTORE_3:
                case Const.DSTORE_0:
                case Const.DSTORE_1:
                case Const.DSTORE_2:
                case Const.DSTORE_3:
                case Const.ASTORE_0:
                case Const.ASTORE_1:
                case Const.ASTORE_2:
                case Const.ASTORE_3:
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item itm = stack.getStackItem(0);
                        itm.setUserValue(null);
                    }
                    sawMethodWeight = 0;
                    conditionalTarget = -1;
                break;

                case Const.ATHROW:
                case Const.POP:
                case Const.POP2:
                case Const.GOTO:
                case Const.GOTO_W:
                case Const.PUTFIELD:
                case Const.PUTSTATIC:
                case Const.IINC:
                case Const.INSTANCEOF:
                case Const.RETURN:
                case Const.ARETURN:
                case Const.IRETURN:
                case Const.LRETURN:
                case Const.FRETURN:
                case Const.DRETURN:
                    sawMethodWeight = 0;
                    conditionalTarget = -1;
                break;

                case Const.ARRAYLENGTH:
                case Const.CHECKCAST:
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item itm = stack.getStackItem(0);
                        userValue = (Integer) itm.getUserValue();
                    }
                break;

                case Const.GETFIELD:
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
