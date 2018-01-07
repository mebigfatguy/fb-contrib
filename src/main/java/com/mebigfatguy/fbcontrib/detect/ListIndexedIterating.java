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

import java.util.BitSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XField;
import edu.umd.cs.findbugs.ba.XMethod;

/**
 * looks for for loops that iterate over a java.util.List using an integer index, and get, rather than using an Iterator. An iterator may perform better
 * depending List implementation, but more importantly will allow the code to be converted to other collections type.
 */
public class ListIndexedIterating extends BytecodeScanningDetector {
    enum State {
        SAW_NOTHING, SAW_IINC
    }

    enum LoopState {
        LOOP_NOT_STARTED, LOOP_INDEX_LOADED_FOR_TEST, LOOP_IN_BODY, LOOP_IN_BODY_WITH_GET
    }

    enum Stage {
        FIND_LOOP_STAGE, FIND_BUG_STAGE
    }

    private BugReporter bugReporter;
    private OpcodeStack stack;
    private Set<ForLoop> possibleForLoops;
    private Stage stage;
    private State state;
    private int loopReg;
    private boolean sawListSize;

    /**
     * constructs a LII detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public ListIndexedIterating(final BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * overrides the interface to create and clear the stack and loops tracker
     *
     * @param classContext
     *            the context object for the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            possibleForLoops = new HashSet<>();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            possibleForLoops = null;
        }
    }

    /**
     * looks for methods that contain a IINC and GOTO or GOTO_W opcodes
     *
     * @param method
     *            the context object of the current method
     * @return if the class uses synchronization
     */
    private boolean prescreen(Method method) {
        BitSet bytecodeSet = getClassContext().getBytecodeSet(method);
        return (bytecodeSet != null) && (bytecodeSet.get(Constants.IINC)) && (bytecodeSet.get(Constants.GOTO) || bytecodeSet.get(Constants.GOTO_W));
    }

    /**
     * overrides the visitor to reset the opcode stack
     *
     * @param obj
     *            the code object for the currently parsed Code
     */
    @Override
    public void visitCode(final Code obj) {
        Method m = getMethod();
        if (prescreen(m)) {
            sawListSize = false;

            stack.resetForMethodEntry(this);
            state = State.SAW_NOTHING;
            stage = Stage.FIND_LOOP_STAGE;
            super.visitCode(obj);

            if (sawListSize && !possibleForLoops.isEmpty()) {
                stack.resetForMethodEntry(this);
                state = State.SAW_NOTHING;
                stage = Stage.FIND_BUG_STAGE;
                super.visitCode(obj);
            }
        }
    }

    /**
     * overrides the visitor to find list indexed iterating
     *
     * @param seen
     *            the currently parsed opcode
     */
    @Override
    public void sawOpcode(final int seen) {
        if (stage == Stage.FIND_LOOP_STAGE) {
            sawOpcodeLoop(seen);
        } else {
            sawOpcodeBug(seen);
        }
    }

    /**
     * the first pass of the method opcode to collet for loops information
     *
     * @param seen
     *            the currently parsed opcode
     */
    private void sawOpcodeLoop(final int seen) {
        try {
            stack.mergeJumps(this);

            switch (state) {
                case SAW_NOTHING:
                    if ((seen == IINC) && (getIntConstant() == 1)) {
                        loopReg = getRegisterOperand();
                        state = State.SAW_IINC;
                    }
                break;

                case SAW_IINC:
                    if ((seen == GOTO) || (seen == GOTO_W)) {
                        int branchTarget = getBranchTarget();
                        int pc = getPC();
                        if (branchTarget < pc) {
                            possibleForLoops.add(new ForLoop(branchTarget, pc, loopReg));
                        }
                    }
                    state = State.SAW_NOTHING;
                break;
            }

            if ((seen == INVOKEINTERFACE) && Values.SLASHED_JAVA_UTIL_LIST.equals(getClassConstantOperand()) && "size".equals(getNameConstantOperand())
                    && SignatureBuilder.SIG_VOID_TO_INT.equals(getSigConstantOperand())) {
                sawListSize = true;
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    /**
     * the second pass to look for get methods on the for loop reg
     *
     * @param seen
     *            the currently parsed opcode
     */
    private void sawOpcodeBug(final int seen) {
        try {
            stack.precomputation(this);

            Iterator<ForLoop> it = possibleForLoops.iterator();
            while (it.hasNext()) {
                ForLoop fl = it.next();
                switch (fl.getLoopState()) {
                    case LOOP_NOT_STARTED:
                        if (getPC() == fl.getLoopStart()) {
                            if (OpcodeUtils.isILoad(seen) && (RegisterUtils.getLoadReg(this, seen) == fl.getLoopReg())) {
                                fl.setLoopState(LoopState.LOOP_INDEX_LOADED_FOR_TEST);
                                continue;
                            }

                            it.remove();
                        }
                    break;

                    case LOOP_INDEX_LOADED_FOR_TEST:
                        if (getPC() >= fl.getLoopEnd()) {
                            it.remove();
                            continue;
                        }
                        if (seen == IF_ICMPGE) {
                            if (stack.getStackDepth() > 1) {
                                OpcodeStack.Item itm = stack.getStackItem(0);
                                if (itm.getConstant() != null) {
                                    it.remove();
                                    continue;
                                }
                                XMethod constantSource = itm.getReturnValueOf();
                                if (constantSource != null) {
                                    if (!"size".equals(constantSource.getMethodDescriptor().getName())) {
                                        it.remove();
                                        continue;
                                    }
                                } else if (getPrevOpcode(1) != ARRAYLENGTH) {
                                    it.remove();
                                    continue;
                                }
                            }
                            int branchTarget = getBranchTarget();
                            if ((branchTarget >= (fl.getLoopEnd() + 3)) && (branchTarget <= (fl.getLoopEnd() + 5))) {
                                fl.setLoopState(LoopState.LOOP_IN_BODY);
                                continue;
                            }
                        }
                    break;

                    case LOOP_IN_BODY:
                    case LOOP_IN_BODY_WITH_GET:
                        if ((getPC() == fl.getLoopEnd()) && (fl.getLoopState() == LoopState.LOOP_IN_BODY_WITH_GET)) {
                            bugReporter.reportBug(new BugInstance(this, "LII_LIST_INDEXED_ITERATING", NORMAL_PRIORITY).addClass(this).addMethod(this)
                                    .addSourceLineRange(this, fl.getLoopStart(), fl.getLoopEnd()));
                            it.remove();
                        }
                        if (getPC() > fl.getLoopEnd()) {
                            it.remove();
                        }

                        if (OpcodeUtils.isILoad(seen)) {
                            loopReg = RegisterUtils.getLoadReg(this, seen);
                            if (loopReg == fl.getLoopReg()) {
                                fl.setLoopRegLoaded(true);
                            }
                        } else if (fl.getLoopRegLoaded()) {
                            boolean sawGet = ((seen == INVOKEINTERFACE) && Values.SLASHED_JAVA_UTIL_LIST.equals(getClassConstantOperand())
                                    && "get".equals(getNameConstantOperand()) && SignatureBuilder.SIG_INT_TO_OBJECT.equals(getSigConstantOperand()));
                            if (!sawGet) {
                                it.remove();
                            } else {
                                fl.setLoopState(LoopState.LOOP_IN_BODY_WITH_GET);
                                if (stack.getStackDepth() > 1) {
                                    OpcodeStack.Item itm = stack.getStackItem(0);
                                    if (!itm.couldBeZero()) {
                                        it.remove();
                                    } else {
                                        itm = stack.getStackItem(1);
                                        if (fl.isSecondItem(itm)) {
                                            it.remove();
                                        }
                                    }
                                }
                                fl.setLoopRegLoaded(false);
                            }
                        }
                    break;
                }
            }

        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    /**
     * represents a for loop
     */
    static class ForLoop {
        private int loopStart;
        private int loopEnd;
        private int loopReg;
        private LoopState loopState;
        private boolean loopRegLoaded;
        private OpcodeStack.Item loopCollectionItem;

        /**
         * constructs a for loop information block
         *
         * @param start
         *            the start of the for loop
         * @param end
         *            the end of the for loop
         * @param reg
         *            the loop register
         */
        public ForLoop(final int start, final int end, final int reg) {
            loopStart = start;
            loopEnd = end;
            loopReg = reg;
            loopState = LoopState.LOOP_NOT_STARTED;
            loopRegLoaded = false;
            loopCollectionItem = null;
        }

        /**
         * get the start pc of the loop
         *
         * @return the start pc of the loop
         */
        public int getLoopStart() {
            return loopStart;
        }

        /**
         * get the end pc of the loop
         *
         * @return the end pc of the loop
         */
        public int getLoopEnd() {
            return loopEnd;
        }

        /**
         * get the loop register
         *
         * @return the loop register
         */
        public int getLoopReg() {
            return loopReg;
        }

        /**
         * sets the current state of the for loop
         *
         * @param state
         *            the new state
         */
        public void setLoopState(final LoopState state) {
            loopState = state;
        }

        /**
         * get the current phase of the for loop
         *
         * @return the current state
         */
        public LoopState getLoopState() {
            return loopState;
        }

        /**
         * mark that the loop register has been loaded with an iload instruction
         *
         * @param loaded
         *            the flag of whether the loop register is loaded
         */
        public void setLoopRegLoaded(final boolean loaded) {
            loopRegLoaded = loaded;
        }

        /**
         * returns whether the loop register is on the top of the stack
         *
         * @return whether the loop register is on the top of the stack
         */
        public boolean getLoopRegLoaded() {
            return loopRegLoaded;
        }

        /**
         * returns whether this is the second time the loop register is found
         *
         * @param itm
         *            the item on the stack
         *
         * @return whether this is the second time the loop register is found
         */
        public boolean isSecondItem(OpcodeStack.Item itm) {
            if (loopCollectionItem == null) {
                loopCollectionItem = itm;
                return false;
            }

            int seenReg = loopCollectionItem.getRegisterNumber();
            if (seenReg >= 0) {
                if (itm.getXField() != null) {
                    return true;
                }
                int newReg = itm.getRegisterNumber();
                if ((newReg >= 0) && (seenReg != newReg)) {
                    return true;
                }
            } else {
                XField newField = itm.getXField();
                if (newField == null) {
                    return true;
                }

                XField seenField = loopCollectionItem.getXField();
                if (seenField == null) {
                    return true;
                }

                if (itm.getRegisterNumber() >= 0) {
                    return true;
                }

                if ((loopCollectionItem.getFieldLoadedFromRegister() != itm.getFieldLoadedFromRegister()) || (itm.getFieldLoadedFromRegister() == -1)
                        || (!newField.getName().equals(seenField.getName()))) {
                    return true;
                }
            }

            loopCollectionItem = itm;
            return false;
        }
    }
}
