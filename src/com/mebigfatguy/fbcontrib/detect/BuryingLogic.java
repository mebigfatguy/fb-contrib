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
import java.util.Deque;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.ToString;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

public class BuryingLogic extends BytecodeScanningDetector {

    private static final String BURY_LOGIC_RATIO_PROPERTY = "fb-contrib.bl.ratio";
    private static final double DEFAULT_BUG_RATIO_LIMIT = 12.0;

    private BugReporter bugReporter;
    private OpcodeStack stack;
    private Deque<IfBlock> ifBlocks;
    private boolean activeUnconditional;
    private boolean isReported;
    private double bugRatioLimit;

    public BuryingLogic(BugReporter bugReporter) {
        this.bugReporter = bugReporter;

        String ratio = System.getProperty(BURY_LOGIC_RATIO_PROPERTY);
        try {
            bugRatioLimit = Double.parseDouble(ratio);
            if (bugRatioLimit <= 0) {
                bugRatioLimit = DEFAULT_BUG_RATIO_LIMIT;
            }
        } catch (Exception e) {
            bugRatioLimit = DEFAULT_BUG_RATIO_LIMIT;
        }
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            ifBlocks = new ArrayDeque<>();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            ifBlocks = null;
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
        activeUnconditional = false;
        isReported = false;
        super.visitCode(obj);
    }

    @Override
    public void sawOpcode(int seen) {
        if (isReported) {
            return;
        }

        if (!ifBlocks.isEmpty()) {
            IfBlock block = ifBlocks.getFirst();
            if ((getPC() >= block.getEnd()) && (!block.isUnconditionalReturn())) {
                ifBlocks.removeFirst();
            }
        }

        if (isBranch(seen)) {
            if (activeUnconditional) {
                activeUnconditional = false;
                ifBlocks.removeFirst();
                return;
            }

            if (getBranchOffset() > 0) {
                ifBlocks.addLast(new IfBlock(getNextPC(), getBranchTarget()));
            }
        }

        if (isReturn(seen)) {
            if (activeUnconditional) {
                IfBlock block = ifBlocks.getFirst();
                int ifSize = block.getEnd() - block.getStart();
                int elseSize = getPC() - block.getEnd();

                double ratio = (double) ifSize / (double) elseSize;
                if (ratio > bugRatioLimit) {
                    bugReporter.reportBug(new BugInstance(this, BugType.BL_BURYING_LOGIC.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                            .addSourceLineRange(this, block.getStart(), block.getEnd()));
                    isReported = true;
                }
            } else if (!ifBlocks.isEmpty() && (getNextPC() == ifBlocks.getFirst().getEnd())) {
                ifBlocks.getFirst().setUnconditionalReturn(true);
                activeUnconditional = true;
            }
        }
    }

    static class IfBlock {
        private int start;
        private int end;
        private boolean unconditinalReturn;

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

        public boolean isUnconditionalReturn() {
            return unconditinalReturn;
        }

        public void setUnconditionalReturn(boolean unconditinalReturn) {
            this.unconditinalReturn = unconditinalReturn;
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }
}
