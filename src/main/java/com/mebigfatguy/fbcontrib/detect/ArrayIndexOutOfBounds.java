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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantInteger;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for usage of arrays with statically known indices where it can be determined that the index is out of bounds based on how the array was allocated. This
 * delector is obviously limited to a small subset of out of bounds exceptions that can be statically determined, and not the large family of problems that can
 * occur at runtime.
 */
@CustomUserValue
public class ArrayIndexOutOfBounds extends BytecodeScanningDetector {

    private BugReporter bugReporter;
    private OpcodeStack stack;
    private BitSet initializedRegs;
    private BitSet modifyRegs;
    private Map<Integer, Integer> nullStoreToLocation;

    /**
     * constructs an AIOB detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public ArrayIndexOutOfBounds(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            initializedRegs = new BitSet();
            modifyRegs = new BitSet();
            nullStoreToLocation = new HashMap<Integer, Integer>();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            initializedRegs = null;
            modifyRegs = null;
            nullStoreToLocation = null;
        }
    }

    /**
     * overrides the visitor to collect parameter registers
     *
     * @param obj
     *            the code block of the currently parsed method
     */
    @Override
    public void visitCode(Code obj) {
        Method m = getMethod();
        stack.resetForMethodEntry(this);
        initializedRegs.clear();
        modifyRegs.clear();
        Type[] argTypes = m.getArgumentTypes();
        int arg = m.isStatic() ? 0 : 1;
        for (Type argType : argTypes) {
            String argSig = argType.getSignature();
            initializedRegs.set(arg);
            arg += SignatureUtils.getSignatureSize(argSig);
        }
        nullStoreToLocation.clear();
        super.visitCode(obj);

        for (Integer pc : nullStoreToLocation.values()) {
            bugReporter.reportBug(new BugInstance(this, BugType.AIOB_ARRAY_STORE_TO_NULL_REFERENCE.name(), HIGH_PRIORITY).addClass(this).addMethod(this)
                    .addSourceLine(this, pc.intValue()));
        }
    }

    /**
     * overrides the visitor to look for stores to arrays that can be statically determined to be outside the bounds of the initialized array
     *
     * @param seen
     *            the currently parsed opcode
     */
    @Override
    public void sawOpcode(int seen) {
        Integer size = null;
        boolean sizeSet = false;
        try {
            stack.precomputation(this);

            switch (seen) {
                case Const.ICONST_0:
                case Const.ICONST_1:
                case Const.ICONST_2:
                case Const.ICONST_3:
                case Const.ICONST_4:
                case Const.ICONST_5:
                    size = Integer.valueOf(seen - Const.ICONST_0);
                    sizeSet = true;
                break;

                case Const.ILOAD:
                case Const.ILOAD_0:
                case Const.ILOAD_1:
                case Const.ILOAD_2:
                case Const.ILOAD_3: {
                    int reg = RegisterUtils.getLoadReg(this, seen);
                    if (modifyRegs.get(reg)) {
                        modifyRegs.clear(reg);
                        sizeSet = true;
                    }
                }
                break;

                case Const.BIPUSH:
                case Const.SIPUSH:
                    size = Integer.valueOf(getIntConstant());
                    sizeSet = true;
                break;

                case Const.IINC:
                    modifyRegs.set(getRegisterOperand());
                break;

                case Const.IADD:
                case Const.ISUB:
                case Const.IMUL:
                case Const.IDIV:
                case Const.F2I:
                case Const.D2I:
                case Const.L2I:
                    sizeSet = true;
                break;

                case Const.ISTORE:
                case Const.ISTORE_0:
                case Const.ISTORE_1:
                case Const.ISTORE_2:
                case Const.ISTORE_3:
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item item = stack.getStackItem(0);
                        if (item.getUserValue() == null) {
                            modifyRegs.set(getRegisterOperand());
                        }
                    }
                break;

                case Const.LDC:
                    Constant c = getConstantRefOperand();
                    if (c instanceof ConstantInteger) {
                        size = Integer.valueOf(((ConstantInteger) c).getBytes());
                        sizeSet = true;
                    }
                break;

                case Const.NEWARRAY:
                case Const.ANEWARRAY:
                    if (stack.getStackDepth() >= 1) {
                        OpcodeStack.Item item = stack.getStackItem(0);
                        size = (Integer) item.getUserValue();
                        sizeSet = true;
                    }
                break;

                case Const.IASTORE:
                case Const.LASTORE:
                case Const.FASTORE:
                case Const.DASTORE:
                case Const.AASTORE:
                case Const.BASTORE:
                case Const.CASTORE:
                case Const.SASTORE:
                    processArrayStore();
                break;

                case Const.IALOAD:
                case Const.LALOAD:
                case Const.FALOAD:
                case Const.DALOAD:
                case Const.AALOAD:
                case Const.BALOAD:
                case Const.CALOAD:
                case Const.SALOAD:
                    processArrayLoad();
                break;

                case Const.ASTORE_0:
                case Const.ASTORE_1:
                case Const.ASTORE_2:
                case Const.ASTORE_3:
                case Const.ASTORE:
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item value = stack.getStackItem(0);
                        if (!value.isNull()) {
                            initializedRegs.set(getRegisterOperand());
                        }
                    } else {
                        initializedRegs.set(getRegisterOperand());
                    }
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
                    int branchTarget = getBranchTarget();
                    Iterator<Map.Entry<Integer, Integer>> it = nullStoreToLocation.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<Integer, Integer> entry = it.next();
                        int pc = entry.getValue().intValue();
                        if ((branchTarget < pc) && initializedRegs.get(entry.getKey().intValue())) {
                            it.remove();
                        }
                    }
                break;
            }

        } finally {
            stack.sawOpcode(this, seen);
            if (sizeSet && (stack.getStackDepth() >= 1)) {
                OpcodeStack.Item item = stack.getStackItem(0);
                item.setUserValue(size);
            }
        }
    }

    private void processArrayLoad() {
        if (stack.getStackDepth() >= 2) {
            OpcodeStack.Item indexItem = stack.getStackItem(0);
            Integer index = (Integer) indexItem.getConstant();
            if (index != null) {
                OpcodeStack.Item arrayItem = stack.getStackItem(1);
                Integer sz = (Integer) arrayItem.getUserValue();
                if ((sz != null) && (index.intValue() >= sz.intValue())) {
                    bugReporter.reportBug(new BugInstance(this, BugType.AIOB_ARRAY_INDEX_OUT_OF_BOUNDS.name(), HIGH_PRIORITY).addClass(this).addMethod(this)
                            .addSourceLine(this));
                }
            }
        }
    }

    private void processArrayStore() {
        if (stack.getStackDepth() >= 3) {
            OpcodeStack.Item indexItem = stack.getStackItem(1);
            Number index = (Number) indexItem.getConstant();
            if (index != null) {
                OpcodeStack.Item arrayItem = stack.getStackItem(2);
                Integer sz = (Integer) arrayItem.getUserValue();
                if ((sz != null) && (index.intValue() >= sz.intValue())) {
                    bugReporter.reportBug(new BugInstance(this, BugType.AIOB_ARRAY_INDEX_OUT_OF_BOUNDS.name(), HIGH_PRIORITY).addClass(this).addMethod(this)
                            .addSourceLine(this));
                }

                int reg = arrayItem.getRegisterNumber();
                if ((reg >= 0) && !initializedRegs.get(reg)) {
                    nullStoreToLocation.put(Integer.valueOf(reg), Integer.valueOf(getPC()));
                }
            }
        }
    }
}
