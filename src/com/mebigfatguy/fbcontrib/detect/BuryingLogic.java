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

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.Iterator;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.CodeException;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.CollectionUtils;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.StopOpcodeParsingException;
import com.mebigfatguy.fbcontrib.utils.ToString;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for relatively large if blocks of code, where you unconditionally return from them, and then follow that with an unconditional return of a small block.
 * This places the bulk of the logic to the right indentation-wise, making it more difficult to read than needed. It would be better to invert the logic of the
 * if block, and immediately return, allowing the bulk of the logic to be move to the left, for easier reading.
 */
public class BuryingLogic extends BytecodeScanningDetector {

    private static final String BURY_LOGIC_LOW_RATIO_PROPERTY = "fb-contrib.bl.low_ratio";
    private static final String BURY_LOGIC_NORMAL_RATIO_PROPERTY = "fb-contrib.bl.normal_ratio";
    private static final double LOW_BUG_RATIO_LIMIT = 12.0;
    private static final double NORMAL_BUG_RATIO_LIMIT = 20.0;

    private BugReporter bugReporter;
    private OpcodeStack stack;
    private Deque<IfBlock> ifBlocks;
    private IfBlock activeUnconditional;
    private Deque<Integer> casePositions;
    private double lowBugRatioLimit;
    private double normalBugRatioLimit;
    private BitSet catchPCs;
    private BitSet gotoBranchPCs;
    private boolean lookingForResetOp;

    public BuryingLogic(BugReporter bugReporter) {
        this.bugReporter = bugReporter;

        String lowRatio = System.getProperty(BURY_LOGIC_LOW_RATIO_PROPERTY);
        try {
            if (lowRatio == null) {
                lowBugRatioLimit = LOW_BUG_RATIO_LIMIT;
            } else {
                lowBugRatioLimit = Double.parseDouble(lowRatio);
                if (lowBugRatioLimit <= 0) {
                    lowBugRatioLimit = LOW_BUG_RATIO_LIMIT;
                }
            }
        } catch (NumberFormatException e) {
            lowBugRatioLimit = LOW_BUG_RATIO_LIMIT;
        }

        String normalRatio = System.getProperty(BURY_LOGIC_NORMAL_RATIO_PROPERTY);
        try {
            if (normalRatio == null) {
                normalBugRatioLimit = NORMAL_BUG_RATIO_LIMIT;
            } else {
                normalBugRatioLimit = Double.parseDouble(normalRatio);
                if (normalBugRatioLimit <= 0) {
                    normalBugRatioLimit = NORMAL_BUG_RATIO_LIMIT;
                }
            }
        } catch (NumberFormatException e) {
            normalBugRatioLimit = NORMAL_BUG_RATIO_LIMIT;
        }
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            ifBlocks = new ArrayDeque<>();
            gotoBranchPCs = new BitSet();
            casePositions = new ArrayDeque<>();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            ifBlocks = null;
            catchPCs = null;
            gotoBranchPCs = null;
            casePositions = null;
        }
    }

    @Override
    public void visitCode(Code obj) {
        Method m = getMethod();
        if (m.getReturnType() == Type.VOID) {
            return;
        }

        stack.resetForMethodEntry(this);
        ifBlocks.clear();
        activeUnconditional = null;

        CodeException[] ces = obj.getExceptionTable();
        if (CollectionUtils.isEmpty(ces)) {
            catchPCs = null;
        } else {
            catchPCs = new BitSet();
            for (CodeException ce : ces) {
                catchPCs.set(ce.getHandlerPC());
            }
        }
        gotoBranchPCs.clear();
        casePositions.clear();
        lookingForResetOp = false;

        try {
            super.visitCode(obj);
        } catch (StopOpcodeParsingException e) {
            // reported an issue, so get out
        }
    }

    @Override
    public void sawOpcode(int seen) {

        try {

            int removed = 0;
            if (!ifBlocks.isEmpty()) {
                Iterator<IfBlock> it = ifBlocks.iterator();
                while (it.hasNext()) {
                    IfBlock block = it.next();
                    if ((getPC() >= block.getEnd())) {
                        it.remove();
                        removed++;
                    }
                }
            }
            if (removed > 1) {
                activeUnconditional = null;
            }

            if (!casePositions.isEmpty() && (casePositions.getFirst().intValue() == getPC())) {
                casePositions.removeFirst();
                activeUnconditional = null;
                lookingForResetOp = true;
            }

            if (lookingForResetOp) {
                if (isResetOp(seen)) {
                    lookingForResetOp = false;
                } else {
                    return;
                }
            }

            if (isBranch(seen)) {
                if (activeUnconditional != null) {
                    activeUnconditional = null;
                    if (!ifBlocks.isEmpty()) {
                        ifBlocks.removeLast();
                    }
                    lookingForResetOp = true;
                }

                int target = getBranchTarget();

                if (getBranchOffset() > 0) {
                    if ((seen == GOTO) || (seen == GOTO_W)) {
                        gotoBranchPCs.set(target);
                    } else if ((catchPCs == null) || !catchPCs.get(getNextPC())) {
                        ifBlocks.addLast(new IfBlock(getNextPC(), target));
                    }
                } else {
                    removeLoopBlocks(target);
                }
            } else if (isReturn(seen)) {
                if ((activeUnconditional != null) && !gotoBranchPCs.get(activeUnconditional.getEnd())) {

                    int ifSize = activeUnconditional.getEnd() - activeUnconditional.getStart();
                    int elseSize = getPC() - activeUnconditional.getEnd();

                    double ratio = (double) ifSize / (double) elseSize;
                    if (ratio > lowBugRatioLimit) {
                        bugReporter
                                .reportBug(new BugInstance(this, BugType.BL_BURYING_LOGIC.name(), ratio > normalBugRatioLimit ? NORMAL_PRIORITY : LOW_PRIORITY)
                                        .addClass(this).addMethod(this).addSourceLineRange(this, activeUnconditional.getStart(), activeUnconditional.getEnd()));
                        throw new StopOpcodeParsingException();
                    }
                } else if (!ifBlocks.isEmpty() && (getNextPC() == ifBlocks.getFirst().getEnd()) && !gotoAcrossPC(getNextPC())) {
                    activeUnconditional = ifBlocks.getFirst();
                }
            } else if ((seen == TABLESWITCH) || (seen == LOOKUPSWITCH)) {
                int[] offsets = getSwitchOffsets();
                int pc = getPC();
                for (int offset : offsets) {
                    casePositions.addFirst(Integer.valueOf(pc + offset));
                }
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    private boolean gotoAcrossPC(int pc) {

        int target = gotoBranchPCs.previousSetBit(Integer.MAX_VALUE);
        return (target > pc);
    }

    /**
     * determines if this opcode couldn't be part of a conditional expression or at least is very unlikely to be so.
     *
     * @param seen
     *            the currently parse opcode
     * @return if this operation resets the looking for conditionals
     */
    private boolean isResetOp(int seen) {
        return (seen == PUTFIELD) || (seen == PUTSTATIC) || (seen == POP) || (seen == POP2) || OpcodeUtils.isStore(seen)
                || (OpcodeUtils.isInvoke(seen) && getSigConstantOperand().endsWith(")Z"));
    }

    private void removeLoopBlocks(int target) {
        Iterator<IfBlock> it = ifBlocks.descendingIterator();
        while (it.hasNext()) {
            if (it.next().getStart() >= target) {
                it.remove();
            } else {
                return;
            }
        }
    }

    /**
     * represents the byte offset code range of code that is executed inside an if block
     */
    static class IfBlock {
        private int start;
        private int end;

        public IfBlock(int s, int e) {
            start = s;
            end = e;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }
}
