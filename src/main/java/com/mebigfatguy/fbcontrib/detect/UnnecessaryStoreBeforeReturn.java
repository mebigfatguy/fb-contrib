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

import java.util.BitSet;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.CodeException;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
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
 * Looks for methods that store the return result in a local variable, and then
 * immediately returns that local variable.
 */
@CustomUserValue
public class UnnecessaryStoreBeforeReturn extends BytecodeScanningDetector {
    enum State {
        SEEN_NOTHING, SEEN_STORE, SEEN_LOAD
    }

    private static final BitSet branchInstructions = new BitSet();
    private static final BitSet binaryOps = new BitSet();

    static {
        branchInstructions.set(Const.GOTO);
        branchInstructions.set(Const.GOTO_W);
        branchInstructions.set(Const.IFEQ);
        branchInstructions.set(Const.IFNE);
        branchInstructions.set(Const.IFLT);
        branchInstructions.set(Const.IFGE);
        branchInstructions.set(Const.IFGT);
        branchInstructions.set(Const.IFLE);
        branchInstructions.set(Const.IF_ICMPEQ);
        branchInstructions.set(Const.IF_ICMPNE);
        branchInstructions.set(Const.IF_ICMPLT);
        branchInstructions.set(Const.IF_ICMPGE);
        branchInstructions.set(Const.IF_ICMPGT);
        branchInstructions.set(Const.IF_ICMPLE);
        branchInstructions.set(Const.IF_ACMPEQ);
        branchInstructions.set(Const.IF_ACMPNE);
        branchInstructions.set(Const.IFNULL);
        branchInstructions.set(Const.IFNONNULL);

        binaryOps.set(Const.IADD);
        binaryOps.set(Const.LADD);
        binaryOps.set(Const.FADD);
        binaryOps.set(Const.DADD);
        binaryOps.set(Const.ISUB);
        binaryOps.set(Const.LSUB);
        binaryOps.set(Const.FSUB);
        binaryOps.set(Const.DSUB);
        binaryOps.set(Const.IMUL);
        binaryOps.set(Const.LMUL);
        binaryOps.set(Const.FMUL);
        binaryOps.set(Const.DMUL);
        binaryOps.set(Const.IDIV);
        binaryOps.set(Const.LDIV);
        binaryOps.set(Const.FDIV);
        binaryOps.set(Const.DDIV);
        binaryOps.set(Const.IREM);
        binaryOps.set(Const.LREM);
        binaryOps.set(Const.FREM);
        binaryOps.set(Const.DREM);
        binaryOps.set(Const.IOR);
        binaryOps.set(Const.LOR);
        binaryOps.set(Const.IAND);
        binaryOps.set(Const.LAND);
        binaryOps.set(Const.IXOR);
        binaryOps.set(Const.LXOR);
        binaryOps.set(Const.ISHL);
        binaryOps.set(Const.LSHL);
        binaryOps.set(Const.ISHR);
        binaryOps.set(Const.LSHR);
        binaryOps.set(Const.IUSHR);
        binaryOps.set(Const.LUSHR);
    }

    private final BugReporter bugReporter;
    private BitSet branchTargets;
    private BitSet catchTargets;
    private OpcodeStack stack;
    private State state;
    private int storeReg;

    /**
     * constructs a USBR detector given the reporter to report bugs on
     *
     * @param bugReporter the sync of bug reports
     */
    public UnnecessaryStoreBeforeReturn(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to create and clear the branchTargets
     *
     * @param classContext the context object for the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            branchTargets = new BitSet();
            catchTargets = new BitSet();
            stack = new OpcodeStack();
            super.visitClassContext(classContext);
        } finally {
            branchTargets = null;
            catchTargets = null;
            stack = null;
        }
    }

    /**
     * implements the visitor to make sure method returns a value, and then clears
     * the targets
     *
     * @param obj the context object of the currently parsed code block
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
                    catchTargets.set(ce.getHandlerPC());
                }
            }
            super.visitCode(obj);
        }
    }

    /**
     * implements the visitor to look for store of registers immediately before
     * returns of that register
     *
     * @param seen the opcode of the currently parsed instruction
     */
    @Override
    public void sawOpcode(int seen) {
        int lhsReg = -1;
        try {
            stack.precomputation(this);

            switch (state) {
            case SEEN_NOTHING:
                if (!catchTargets.get(getPC()) && lookForStore(seen) && (stack.getStackDepth() >= 1)) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    Integer reg = (Integer) item.getUserValue();
                    if ((reg == null) || (reg.intValue() != storeReg)) {
                        state = State.SEEN_STORE;
                    }
                }
                break;

            case SEEN_STORE:
                if (branchTargets.get(getPC())) {
                    state = State.SEEN_NOTHING;
                    break;
                }

                state = lookForLoad(seen) ? State.SEEN_LOAD : State.SEEN_NOTHING;
                break;

            case SEEN_LOAD:
                if ((seen >= Const.IRETURN) && (seen <= Const.ARETURN)) {
                    bugReporter.reportBug(
                            new BugInstance(this, BugType.USBR_UNNECESSARY_STORE_BEFORE_RETURN.name(), NORMAL_PRIORITY)
                                    .addClass(this).addMethod(this).addSourceLine(this));
                }
                state = State.SEEN_NOTHING;
                break;
            }

            if (branchInstructions.get(seen)) {
                branchTargets.set(getBranchTarget());
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
     * @param seen the opcode of the currently parsed instruction
     * @return if a store was seen
     */
    private boolean lookForStore(int seen) {
        if (!OpcodeUtils.isStore(seen)) {
            return false;
        }

        storeReg = RegisterUtils.getStoreReg(this, seen);
        return true;
    }

    /**
     * looks for a load of the register that was just stored
     *
     * @param seen the opcode of the currently parsed instruction
     * @return if the load was seen
     */
    private boolean lookForLoad(int seen) {
        int loadReg;

        if (!OpcodeUtils.isLoad(seen)) {
            return false;
        }

        loadReg = RegisterUtils.getLoadReg(this, seen);
        return (storeReg == loadReg);
    }

    /**
     * looks for instructions that are binary operators, and if it is saves the left
     * hand side register (if it exists) in the userValue.
     *
     * @param seen the opcode of the currently parsed instruction
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
