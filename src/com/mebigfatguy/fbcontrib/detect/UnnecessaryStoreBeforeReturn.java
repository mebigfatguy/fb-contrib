/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2016 Dave Brosius
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

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.CodeException;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * Looks for methods that store the return result in a local variable, and then immediately returns that local variable.
 */
@CustomUserValue
public class UnnecessaryStoreBeforeReturn extends BytecodeScanningDetector {
    enum State {
        SEEN_NOTHING, SEEN_STORE, SEEN_LOAD
    }

    private static final BitSet branchInstructions = new BitSet();
    private static final BitSet binaryOps = new BitSet();

    static {
        branchInstructions.set(GOTO);
        branchInstructions.set(GOTO_W);
        branchInstructions.set(IFEQ);
        branchInstructions.set(IFNE);
        branchInstructions.set(IFLT);
        branchInstructions.set(IFGE);
        branchInstructions.set(IFGT);
        branchInstructions.set(IFLE);
        branchInstructions.set(IF_ICMPEQ);
        branchInstructions.set(IF_ICMPNE);
        branchInstructions.set(IF_ICMPLT);
        branchInstructions.set(IF_ICMPGE);
        branchInstructions.set(IF_ICMPGT);
        branchInstructions.set(IF_ICMPLE);
        branchInstructions.set(IF_ACMPEQ);
        branchInstructions.set(IF_ACMPNE);
        branchInstructions.set(IFNULL);
        branchInstructions.set(IFNONNULL);

        binaryOps.set(IADD);
        binaryOps.set(LADD);
        binaryOps.set(FADD);
        binaryOps.set(DADD);
        binaryOps.set(ISUB);
        binaryOps.set(LSUB);
        binaryOps.set(FSUB);
        binaryOps.set(DSUB);
        binaryOps.set(IMUL);
        binaryOps.set(LMUL);
        binaryOps.set(FMUL);
        binaryOps.set(DMUL);
        binaryOps.set(IDIV);
        binaryOps.set(LDIV);
        binaryOps.set(FDIV);
        binaryOps.set(DDIV);
        binaryOps.set(IREM);
        binaryOps.set(LREM);
        binaryOps.set(FREM);
        binaryOps.set(DREM);
        binaryOps.set(IOR);
        binaryOps.set(LOR);
        binaryOps.set(IAND);
        binaryOps.set(LAND);
        binaryOps.set(IXOR);
        binaryOps.set(LXOR);
        binaryOps.set(ISHL);
        binaryOps.set(LSHL);
        binaryOps.set(ISHR);
        binaryOps.set(LSHR);
        binaryOps.set(IUSHR);
        binaryOps.set(LUSHR);
    }

    private final BugReporter bugReporter;
    private Set<Integer> branchTargets;
    private Set<Integer> catchTargets;
    private OpcodeStack stack;
    private State state;
    private int storeReg;

    /**
     * constructs a USBR detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public UnnecessaryStoreBeforeReturn(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to create and clear the branchTargets
     *
     * @param classContext
     *            the context object for the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            branchTargets = new HashSet<>();
            catchTargets = new HashSet<>();
            stack = new OpcodeStack();
            super.visitClassContext(classContext);
        } finally {
            branchTargets = null;
            catchTargets = null;
            stack = null;
        }
    }

    /**
     * implements the visitor to make sure method returns a value, and then clears the targets
     *
     * @param obj
     *            the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        Method m = getMethod();
        String sig = m.getSignature();
        if (!Values.SIG_VOID.equals(SignatureUtils.getReturnSignature(sig))) {
            state = State.SEEN_NOTHING;
            branchTargets.clear();
            CodeException[] ces = obj.getExceptionTable();
            catchTargets.clear();
            stack.resetForMethodEntry(this);
            for (CodeException ce : ces) {
                if (ce.getCatchType() != 0) {
                    catchTargets.add(Integer.valueOf(ce.getHandlerPC()));
                }
            }
            super.visitCode(obj);
        }
    }

    /**
     * implements the visitor to look for store of registers immediately before returns of that register
     *
     * @param seen
     *            the opcode of the currently parsed instruction
     */
    @Override
    public void sawOpcode(int seen) {
        int lhsReg = -1;
        try {
            stack.precomputation(this);

            switch (state) {
                case SEEN_NOTHING:
                    if (!catchTargets.contains(Integer.valueOf(getPC())) && lookForStore(seen) && (stack.getStackDepth() >= 1)) {
                        OpcodeStack.Item item = stack.getStackItem(0);
                        Integer reg = (Integer) item.getUserValue();
                        if ((reg == null) || (reg.intValue() != storeReg)) {
                            state = State.SEEN_STORE;
                        }
                    }
                break;

                case SEEN_STORE:
                    if (branchTargets.contains(Integer.valueOf(getPC()))) {
                        state = State.SEEN_NOTHING;
                        break;
                    }

                    state = lookForLoad(seen) ? State.SEEN_LOAD : State.SEEN_NOTHING;
                break;

                case SEEN_LOAD:
                    if ((seen >= IRETURN) && (seen <= ARETURN)) {
                        bugReporter.reportBug(new BugInstance(this, BugType.USBR_UNNECESSARY_STORE_BEFORE_RETURN.name(), NORMAL_PRIORITY).addClass(this)
                                .addMethod(this).addSourceLine(this));
                    }
                    state = State.SEEN_NOTHING;
                break;
            }

            if (branchInstructions.get(seen)) {
                branchTargets.add(Integer.valueOf(getBranchTarget()));
            }

            lhsReg = processBinOp(seen);

        } finally {
            TernaryPatcher.pre(stack, seen);
            stack.sawOpcode(this, seen);
            TernaryPatcher.post(stack, seen);
            if ((lhsReg > -1) && (stack.getStackDepth() >= 1)) {
                OpcodeStack.Item item = stack.getStackItem(0);
                item.setUserValue(Integer.valueOf(lhsReg));
            }
        }

    }

    /**
     * checks if the current opcode is a store, if so saves the register
     *
     * @param seen
     *            the opcode of the currently parsed instruction
     * @return if a store was seen
     */
    private boolean lookForStore(int seen) {
        if ((seen >= ISTORE) && (seen <= ASTORE)) {
            storeReg = getRegisterOperand();
        } else if ((seen >= ISTORE_0) && (seen <= ISTORE_3)) {
            storeReg = seen - ISTORE_0;
        } else if ((seen >= LSTORE_0) && (seen <= LSTORE_3)) {
            storeReg = seen - LSTORE_0;
        } else if ((seen >= FSTORE_0) && (seen <= FSTORE_3)) {
            storeReg = seen - FSTORE_0;
        } else if ((seen >= DSTORE_0) && (seen <= DSTORE_3)) {
            storeReg = seen - DSTORE_0;
        } else if ((seen >= ASTORE_0) && (seen <= ASTORE_3)) {
            storeReg = seen - ASTORE_0;
        } else {
            return false;
        }
        return true;
    }

    /**
     * looks for a load of the register that was just stored
     *
     * @param seen
     *            the opcode of the currently parsed instruction
     * @return if the load was seen
     */
    private boolean lookForLoad(int seen) {
        int loadReg;
        if ((seen >= ILOAD) && (seen <= ALOAD)) {
            loadReg = getRegisterOperand();
        } else if ((seen >= ILOAD_0) && (seen <= ILOAD_3)) {
            loadReg = seen - ILOAD_0;
        } else if ((seen >= LLOAD_0) && (seen <= LLOAD_3)) {
            loadReg = seen - LLOAD_0;
        } else if ((seen >= FLOAD_0) && (seen <= FLOAD_3)) {
            loadReg = seen - FLOAD_0;
        } else if ((seen >= DLOAD_0) && (seen <= DLOAD_3)) {
            loadReg = seen - DLOAD_0;
        } else if ((seen >= ALOAD_0) && (seen <= ALOAD_3)) {
            loadReg = seen - ALOAD_0;
        } else {
            return false;
        }

        return (storeReg == loadReg);
    }

    /**
     * looks for instructions that are binary operators, and if it is saves the left hand side register (if it exists) in the userValue.
     *
     * @param seen
     *            the opcode of the currently parsed instruction
     * @return the lhs register number if it exists or -1
     */
    private int processBinOp(int seen) {
        if (binaryOps.get(seen) && (stack.getStackDepth() >= 2)) {
            OpcodeStack.Item item = stack.getStackItem(1);
            return item.getRegisterNumber();
        }
        return -1;
    }
}
