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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.LocalVariable;
import org.apache.bcel.classfile.LocalVariableTable;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for loops where an equality check is made and a variable is set because of it. It would seem once the item is found, the loop can be terminated,
 * however the code continues on, looking for more matches. It is possible the code is looking for the last match, but if this we case, a reverse iterator might
 * be more effective.
 */
public class SuspiciousLoopSearch extends BytecodeScanningDetector {

    enum State {
        SAW_NOTHING, SAW_EQUALS, SAW_IFEQ
    };

    private BugReporter bugReporter;
    private OpcodeStack stack;
    private State state;
    private List<IfBlock> ifBlocks;
    private Map<Integer, Integer> loadedRegs;
    private BitSet loopLocations;

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
            ifBlocks = new ArrayList<>();
            loadedRegs = new HashMap<>();
            loopLocations = new BitSet();
            stack = new OpcodeStack();
            super.visitClassContext(classContext);
        } finally {
            ifBlocks = null;
            loadedRegs = null;
            loopLocations = null;
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
            ifBlocks.clear();
            loadedRegs.clear();
            loopLocations.clear();
            stack.resetForMethodEntry(this);
            state = State.SAW_NOTHING;
            super.visitCode(obj);
        }
    }

    /**
     * implements the visitor to find continuations after finding a search result in a loop.
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
                    sawOpcodeAfterBranch(seen);
                break;
            }

            processLoad(seen);
            processLoop(seen);
        } finally {
            stack.sawOpcode(this, seen);
        }

    }

    private void sawOpcodeAfterNothing(int seen) {
        if ((seen == Const.INVOKEVIRTUAL) && "equals".equals(getNameConstantOperand())
                && SignatureBuilder.SIG_OBJECT_TO_BOOLEAN.equals(getSigConstantOperand())) {
            state = State.SAW_EQUALS;
        } else if (seen == Const.IF_ICMPNE) {
            if (getBranchOffset() > 0) {
                state = State.SAW_IFEQ;
                int target = getBranchTarget();
                ifBlocks.add(new IfBlock(getPC(), target));
            } else {
                state = State.SAW_NOTHING;
            }
        }
    }

    private void sawOpcodeAfterEquals(int seen) {
        if (seen == Const.IFEQ) {
            if (getBranchOffset() > 0) {
                state = State.SAW_IFEQ;
                int target = getBranchTarget();
                ifBlocks.add(new IfBlock(getPC(), target));
            } else {
                state = State.SAW_NOTHING;
            }
        } else {
            state = State.SAW_NOTHING;
        }
    }

    private void sawOpcodeAfterBranch(int seen) {
        if (!ifBlocks.isEmpty()) {
            IfBlock block = ifBlocks.get(ifBlocks.size() - 1);
            if (OpcodeUtils.isStore(seen)) {
                int reg = RegisterUtils.getStoreReg(this, seen);
                if (!loadedRegs.containsKey(reg)) {
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
                    if (!Values.SIG_PRIMITIVE_BOOLEAN.equals(sig) && !Values.SIG_JAVA_LANG_BOOLEAN.equals(sig)) {
                        block.storeRegs.put(Integer.valueOf(RegisterUtils.getStoreReg(this, seen)), Integer.valueOf(getPC()));
                    }
                }
            } else if (OpcodeUtils.isReturn(seen)) {
                copyStoredIntoLoadedforBlock(block);
            }

            if (block.end <= getPC()) {
                state = State.SAW_NOTHING;
            }
        }
    }

    private void processLoad(int seen) {
        if (OpcodeUtils.isLoad(seen)) {
            int reg = RegisterUtils.getLoadReg(this, seen);
            loadedRegs.put(reg, getPC());
        }
    }

    private void processLoop(int seen) {
        if (isBranch(seen) && (getBranchOffset() < 0)) {
            loopLocations.set(getPC());
            List<IfBlock> blocksInLoop = new ArrayList<>(4);

            Iterator<IfBlock> it = ifBlocks.iterator();
            int target = getBranchTarget();
            while (it.hasNext()) {
                IfBlock block = it.next();
                if ((target <= block.start) && (getPC() >= block.end)) {
                    if (block.storeRegs.size() == 1) {
                        blocksInLoop.add(block);
                    }
                    it.remove();
                }
            }

            int loopPos = loopLocations.nextSetBit(0);
            while (loopPos >= 0) {
                if ((loopPos > target) && (loopPos < getPC())) {
                    state = State.SAW_NOTHING;
                    return;
                }
                loopPos = loopLocations.nextSetBit(loopPos + 1);
            }

            if (blocksInLoop.size() == 1) {
                IfBlock block = blocksInLoop.get(0);
                Integer pc = loadedRegs.get(block.storeRegs.entrySet().iterator().next().getKey());
                if ((pc == null) || (pc.intValue() < target)) {

                    bugReporter.reportBug(new BugInstance(this, BugType.SLS_SUSPICIOUS_LOOP_SEARCH.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                            .addSourceLine(this, blocksInLoop.get(0).storeRegs.values().iterator().next().intValue()));
                }
            }

            Iterator<Map.Entry<Integer, Integer>> loadedIt = loadedRegs.entrySet().iterator();
            while (loadedIt.hasNext()) {
                if (loadedIt.next().getValue() >= target) {
                    loadedIt.remove();
                }
            }
            state = State.SAW_NOTHING;
        } else if ((seen == Const.GOTO) || (seen == Const.GOTO_W))

        {
            if (!ifBlocks.isEmpty()) {
                IfBlock block = ifBlocks.get(ifBlocks.size() - 1);
                copyStoredIntoLoadedforBlock(block);
            }
            state = State.SAW_NOTHING;
        }
    }

    private void copyStoredIntoLoadedforBlock(IfBlock block) {
        if (block.end >= getPC()) {
            for (Map.Entry<Integer, Integer> storeEntry : block.storeRegs.entrySet()) {
                loadedRegs.put(storeEntry.getKey(), storeEntry.getValue());
            }
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
        return (bytecodeSet != null) && bytecodeSet.get(Const.GOTO);
    }

    /**
     * represents an if block and what registers are stored inside the block
     */
    static class IfBlock {
        int start;
        int end;
        final Map<Integer, Integer> storeRegs;

        public IfBlock(int start, int end) {
            this.start = start;
            this.end = end;
            storeRegs = new HashMap<>(4);
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }
}
