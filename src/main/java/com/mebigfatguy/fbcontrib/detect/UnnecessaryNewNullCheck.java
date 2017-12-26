/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2017 Dave Brosius
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

import java.util.BitSet;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.CodeException;

import com.mebigfatguy.fbcontrib.utils.AttributesUtils;
import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for construction of new objects, and then the immediate testing whether the object is null or not. As the new operator will always succeed, or through
 * an exception, this test is unnecessary and represents a misunderstanding as to how the jvm works.
 */
@CustomUserValue
public class UnnecessaryNewNullCheck extends BytecodeScanningDetector {
    private final BugReporter bugReporter;
    private OpcodeStack stack;
    private BitSet allocationRegs;
    private BitSet transitionPoints;

    public UnnecessaryNewNullCheck(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            allocationRegs = new BitSet();
            transitionPoints = new BitSet();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            allocationRegs = null;
            transitionPoints = null;
        }
    }

    @Override
    public void visitCode(Code obj) {
        if (prescreen()) {
            stack.resetForMethodEntry(this);
            allocationRegs.clear();
            transitionPoints.clear();

            CodeException[] ce = obj.getExceptionTable();
            if (ce != null) {
                for (CodeException element : ce) {
                    transitionPoints.set(element.getEndPC());
                }
            }
            super.visitCode(obj);
        }
    }

    @Override
    public void sawOpcode(int seen) {
        boolean sawAlloc = false;
        try {
            stack.precomputation(this);

            if (transitionPoints.get(getPC())) {
                allocationRegs.clear();
                int depth = stack.getStackDepth();
                for (int i = 0; i < depth; i++) {
                    OpcodeStack.Item item = stack.getStackItem(i);
                    item.setUserValue(null);
                }
            }

            switch (seen) {
                case Const.NEW:
                case Const.ANEWARRAY:
                case Const.MULTIANEWARRAY:
                    sawAlloc = true;
                break;

                case Const.INVOKESPECIAL:
                    if (Values.CONSTRUCTOR.equals(getNameConstantOperand())) {
                        sawAlloc = true;
                    }
                break;

                case Const.ASTORE:
                case Const.ASTORE_0:
                case Const.ASTORE_1:
                case Const.ASTORE_2:
                case Const.ASTORE_3:
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item item = stack.getStackItem(0);
                        int reg = RegisterUtils.getAStoreReg(this, seen);
                        if (item.getUserValue() == null) {
                            allocationRegs.clear(reg);
                        } else {
                            allocationRegs.set(reg);
                        }
                    }
                break;

                case Const.ALOAD:
                case Const.ALOAD_0:
                case Const.ALOAD_1:
                case Const.ALOAD_2:
                case Const.ALOAD_3:
                    int reg = RegisterUtils.getALoadReg(this, seen);
                    sawAlloc = (allocationRegs.get(reg));
                break;

                case Const.IFNONNULL:
                case Const.IFNULL:
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item item = stack.getStackItem(0);
                        if ((item.getUserValue() != null) && AttributesUtils.isValidLineNumber(getCode(), getPC())) {
                            bugReporter.reportBug(new BugInstance(this, BugType.UNNC_UNNECESSARY_NEW_NULL_CHECK.name(), NORMAL_PRIORITY).addClass(this)
                                    .addMethod(this).addSourceLine(this));
                        }
                    }
                    transitionPoints.set(getBranchTarget());
                    allocationRegs.clear();
                break;

                case Const.IFEQ:
                case Const.IFNE:
                case Const.IFLT:
                case Const.IFGE:
                case Const.IFGT:
                case Const.IFLE:
                case Const.IF_ICMPEQ:
                case Const.IF_ICMPNE:
                case Const.IF_ICMPLT:
                case Const.IF_ICMPGE:
                case Const.IF_ICMPGT:
                case Const.IF_ICMPLE:
                case Const.IF_ACMPEQ:
                case Const.IF_ACMPNE:
                case Const.GOTO:
                case Const.GOTO_W:
                    transitionPoints.set(getBranchTarget());
                    allocationRegs.clear();
                break;

                case Const.TABLESWITCH:
                case Const.LOOKUPSWITCH:
                case Const.IRETURN:
                case Const.LRETURN:
                case Const.FRETURN:
                case Const.DRETURN:
                case Const.ARETURN:
                case Const.RETURN:
                case Const.ATHROW:
                    allocationRegs.clear();
                break;
                default:
                break;
            }
        } finally {
            TernaryPatcher.pre(stack, seen);
            stack.sawOpcode(this, seen);
            TernaryPatcher.post(stack, seen);

            if (stack.getStackDepth() > 0) {
                OpcodeStack.Item item = stack.getStackItem(0);
                item.setUserValue(sawAlloc ? Boolean.TRUE : null);
            }
        }
    }

    private boolean prescreen() {
        BitSet bytecodeSet = getClassContext().getBytecodeSet(getMethod());
        return (bytecodeSet != null) && (bytecodeSet.get(Const.NEW) || bytecodeSet.get(Const.ANEWARRAY) || bytecodeSet.get(Const.MULTIANEWARRAY));
    }
}
