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
import java.util.Map;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.LocalVariable;
import org.apache.bcel.classfile.LocalVariableTable;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for loops where an equality check is made and a variable is set because
 * of it. It would seem once the item is found, the loop can be terminated,
 * however the code continues on, looking for more matches. It is possible the
 * code is looking for the last match, but if this we case, a reverse iterator
 * might be more effective.
 */
public class SuspiciousLoopSearch extends BytecodeScanningDetector {

    enum State {
        SAW_NOTHING, SAW_EQUALS, SAW_IFEQ, SAW_ASSIGNMENT
    };

    private BugReporter bugReporter;
    private OpcodeStack stack;
    private Map<Integer, Integer> storeRegs;
    private BitSet loadRegs;
    private State state;
    private int equalsPos;
    private int ifeqBranchTarget;

    /**
     * constructs an SLS detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public SuspiciousLoopSearch(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * overrides the visitor to initialize and tear down the opcode stack
     *
     * @param classContext
     *            the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            storeRegs = new HashMap<Integer, Integer>();
            loadRegs = new BitSet();
            stack = new OpcodeStack();
            super.visitClassContext(classContext);
        } finally {
            loadRegs = null;
            storeRegs = null;
            stack = null;
        }
    }

    /**
     * overrides the visitor to reset the stack
     *
     * @param obj
     *            the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        if (prescreen(getMethod())) {
            storeRegs.clear();
            loadRegs.clear();
            stack.resetForMethodEntry(this);
            state = State.SAW_NOTHING;
            super.visitCode(obj);
        }
    }

    /**
     * implements the visitor to find continuations after finding a search
     * result in a loop.
     *
     * @param seen
     *            the currently visitor opcode
     */
    @Override
    public void sawOpcode(int seen) {
        try {
            switch (state) {
            case SAW_NOTHING:
                sawOpcodeAfterNothing(seen);
                break;

            case SAW_EQUALS:
                sawOpcodeAfterEquals(seen);
                break;

            case SAW_IFEQ:
            case SAW_ASSIGNMENT:
                sawOpcodeAfterAssignment(seen);
                break;
            }
        } finally {
            stack.sawOpcode(this, seen);
        }

    }

    private void sawOpcodeAfterNothing(int seen) {
        if ((seen == INVOKEVIRTUAL)
                && "equals".equals(getNameConstantOperand())
                && SignatureBuilder.SIG_OBJECT_TO_BOOLEAN.equals(getSigConstantOperand())) {
            state = State.SAW_EQUALS;
            equalsPos = getPC();
        }
    }


    private void sawOpcodeAfterEquals(int seen) {
        if (seen == IFEQ) {
            state = State.SAW_IFEQ;
            ifeqBranchTarget = getBranchTarget();
        } else {
            state = State.SAW_NOTHING;
        }
    }

    private void sawOpcodeAfterAssignment(int seen) {
        if (getPC() >= ifeqBranchTarget) {
            if ((seen == GOTO) && !storeRegs.isEmpty() && (getBranchTarget() < equalsPos)) {
                bugReporter.reportBug(new BugInstance(this, BugType.SLS_SUSPICIOUS_LOOP_SEARCH.name(), NORMAL_PRIORITY).addClass(this)
                        .addMethod(this).addSourceLine(this, storeRegs.values().iterator().next().intValue()));
            }
            storeRegs.clear();
            loadRegs.clear();
            state = State.SAW_NOTHING;
        } else if (OpcodeUtils.isBranch(seen) || OpcodeUtils.isReturn(seen)) {
            state = State.SAW_NOTHING;
        } else {
            if (OpcodeUtils.isStore(seen)) {
                int reg = RegisterUtils.getStoreReg(this, seen);
                if (!loadRegs.get(reg)) {
                    LocalVariableTable lvt = getMethod().getLocalVariableTable();
                    String sig = "";
                    if (lvt != null) {
                        LocalVariable lv = lvt.getLocalVariable(reg, getPC());
                        if (lv != null) {
                            sig = lv.getSignature();
                        }
                    }
                    // ignore boolean flag stores, as this is a
                    // relatively normal occurrence
                    if (!Values.SIG_PRIMITIVE_BOOLEAN.equals(sig)) {
                        storeRegs.put(Integer.valueOf(RegisterUtils.getStoreReg(this, seen)), Integer.valueOf(getPC()));
                    }
                }
            } else if (OpcodeUtils.isLoad(seen)) {
                int reg = RegisterUtils.getLoadReg(this, seen);
                storeRegs.remove(Integer.valueOf(reg));
                loadRegs.set(reg);
            }
            state = State.SAW_ASSIGNMENT;
        }
    }

    /**
     * looks for methods that contain a GOTO opcodes
     *
     * @param method
     *            the context object of the current method
     * @return if the class uses synchronization
     */
    private boolean prescreen(Method method) {
        BitSet bytecodeSet = getClassContext().getBytecodeSet(method);
        return (bytecodeSet != null) && bytecodeSet.get(Constants.GOTO);
    }

}
