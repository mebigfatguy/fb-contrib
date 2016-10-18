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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.CodeException;

import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.ToString;

import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for try/finally blocks that manage resources, without using try-with-resources
 */
public class UseTryWithResources extends BytecodeScanningDetector {

    private BugReporter bugReporter;
    private OpcodeStack stack;
    private Map<Integer, TryBlock> finallyBlocks;
    private Map<Integer, Integer> regStoredPCs;
    private int lastGoto;

    public UseTryWithResources(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {

        try {
            int majorVersion = classContext.getJavaClass().getMajor();

            if (majorVersion >= MAJOR_1_7) {
                stack = new OpcodeStack();
                finallyBlocks = new HashMap<>();
                regStoredPCs = new HashMap<>();
                super.visitClassContext(classContext);
            }
        } finally {
            stack = null;
            finallyBlocks = null;
            regStoredPCs = null;
        }
    }

    @Override
    public void visitCode(Code obj) {
        if (prescreen(obj)) {
            stack.resetForMethodEntry(this);
            regStoredPCs.clear();
            lastGoto = -1;
            super.visitCode(obj);
        }
    }

    @Override
    public void sawOpcode(int seen) {
        try {
            int pc = getPC();
            Iterator<TryBlock> it = finallyBlocks.values().iterator();
            while (it.hasNext()) {
                TryBlock tb = it.next();
                if (tb.getHandlerEndPC() < pc) {
                    it.remove();
                }
            }
            TryBlock tb = finallyBlocks.get(pc);
            if (tb != null) {
                if (lastGoto > -1) {
                    tb.setHandlerEndPC(lastGoto);
                }
            }

            if (OpcodeUtils.isAStore(seen)) {
                regStoredPCs.put(Integer.valueOf(getRegisterOperand()), Integer.valueOf(pc));
            }

            lastGoto = (((seen == GOTO) || (seen == GOTO_W)) && (getBranchOffset() > 0)) ? getBranchTarget() : -1;
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    private boolean prescreen(Code obj) {
        finallyBlocks.clear();
        CodeException[] ces = obj.getExceptionTable();
        if ((ces == null) || (ces.length == 0)) {
            return false;
        }

        boolean hasFinally = false;
        for (CodeException ce : ces) {
            if (ce.getCatchType() == 0) {
                finallyBlocks.put(Integer.valueOf(ce.getHandlerPC()), new TryBlock(ce.getStartPC(), ce.getEndPC(), ce.getHandlerPC()));
                hasFinally = true;
            }
        }

        return hasFinally;
    }

    @Override
    public String toString() {
        return ToString.build(this);
    }

    static class TryBlock {
        private int startPC;
        private int endPC;
        private int handlerPC;
        private int handlerEndPC;

        public TryBlock(int startPC, int endPC, int handlerPC) {
            super();
            this.startPC = startPC;
            this.endPC = endPC;
            this.handlerPC = handlerPC;
            this.handlerEndPC = Integer.MAX_VALUE;
        }

        public int getHandlerEndPC() {
            return handlerEndPC;
        }

        public void setHandlerEndPC(int handlerEndPC) {
            this.handlerEndPC = handlerEndPC;
        }

        public int getStartPC() {
            return startPC;
        }

        public int getEndPC() {
            return endPC;
        }

        public int getHandlerPC() {
            return handlerPC;
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }
}
