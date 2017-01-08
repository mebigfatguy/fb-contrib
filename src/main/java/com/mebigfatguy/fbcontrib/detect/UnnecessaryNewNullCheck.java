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
import java.util.HashSet;
import java.util.Set;

import org.apache.bcel.Constants;
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
 * looks for construction of new objects, and then the immediate testing whether
 * the object is null or not. As the new operator will always succeed, or
 * through an exception, this test is unnecessary and represents a
 * misunderstanding as to how the jvm works.
 */
@CustomUserValue
public class UnnecessaryNewNullCheck extends BytecodeScanningDetector {
    private final BugReporter bugReporter;
    private OpcodeStack stack;
    private BitSet allocationRegs;
    private Set<Integer> transitionPoints;

    public UnnecessaryNewNullCheck(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            allocationRegs = new BitSet();
            transitionPoints = new HashSet<Integer>();
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
                    transitionPoints.add(Integer.valueOf(element.getEndPC()));
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

            if (transitionPoints.contains(Integer.valueOf(getPC()))) {
                allocationRegs.clear();
                int depth = stack.getStackDepth();
                for (int i = 0; i < depth; i++) {
                    OpcodeStack.Item item = stack.getStackItem(i);
                    item.setUserValue(null);
                }
            }

            switch (seen) {
            case NEW:
            case ANEWARRAY:
            case MULTIANEWARRAY:
                sawAlloc = true;
                break;

            case INVOKESPECIAL:
                if (Values.CONSTRUCTOR.equals(getNameConstantOperand())) {
                    sawAlloc = true;
                }
                break;

            case ASTORE:
            case ASTORE_0:
            case ASTORE_1:
            case ASTORE_2:
            case ASTORE_3:
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

            case ALOAD:
            case ALOAD_0:
            case ALOAD_1:
            case ALOAD_2:
            case ALOAD_3:
                int reg = RegisterUtils.getALoadReg(this, seen);
                sawAlloc = (allocationRegs.get(reg));
                break;

            case IFNONNULL:
            case IFNULL:
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    if ((item.getUserValue() != null) && AttributesUtils.isValidLineNumber(getCode(), getPC())) {
                        bugReporter.reportBug(new BugInstance(this, BugType.UNNC_UNNECESSARY_NEW_NULL_CHECK.name(), NORMAL_PRIORITY).addClass(this)
                                .addMethod(this).addSourceLine(this));
                    }
                }
                transitionPoints.add(Integer.valueOf(getBranchTarget()));
                allocationRegs.clear();
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
            case GOTO:
            case GOTO_W:
                transitionPoints.add(Integer.valueOf(getBranchTarget()));
                allocationRegs.clear();
                break;

            case TABLESWITCH:
            case LOOKUPSWITCH:
            case IRETURN:
            case LRETURN:
            case FRETURN:
            case DRETURN:
            case ARETURN:
            case RETURN:
            case ATHROW:
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
        return (bytecodeSet != null) && (bytecodeSet.get(Constants.NEW) || bytecodeSet.get(Constants.ANEWARRAY) || bytecodeSet.get(Constants.MULTIANEWARRAY));
    }
}
