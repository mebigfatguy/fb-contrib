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
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.CodeException;
import org.apache.bcel.classfile.JavaClass;

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

    enum State {
        SEEN_NOTHING, SEEN_IFNULL, SEEN_ALOAD
    };

    private JavaClass autoCloseableClass;
    private BugReporter bugReporter;
    private OpcodeStack stack;
    private LinkedHashMap<Integer, TryBlock> finallyBlocks;
    private Map<Integer, Integer> regStoredPCs;
    private int lastGoto;
    private int lastNullCheckedReg;
    private State state;

    public UseTryWithResources(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
        try {
            autoCloseableClass = Repository.lookupClass("java/lang/Autocloseable");
        } catch (ClassNotFoundException e) {
            bugReporter.reportMissingClass(e);
        }
    }

    @Override
    public void visitClassContext(ClassContext classContext) {

        try {
            int majorVersion = classContext.getJavaClass().getMajor();

            if (majorVersion >= MAJOR_1_7) {
                stack = new OpcodeStack();
                finallyBlocks = new LinkedHashMap<>();
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
            state = State.SEEN_NOTHING;
            lastNullCheckedReg = -1;
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

            switch (state) {
                case SEEN_NOTHING:
                    if ((seen == IFNULL) && (stack.getStackDepth() >= 1)) {
                        OpcodeStack.Item itm = stack.getStackItem(0);
                        lastNullCheckedReg = itm.getRegisterNumber();
                        state = lastNullCheckedReg >= 0 ? State.SEEN_IFNULL : State.SEEN_NOTHING;
                    } else {
                        lastNullCheckedReg = -1;
                    }
                break;

                case SEEN_IFNULL:
                    if (OpcodeUtils.isALoad(seen)) {
                        if (lastNullCheckedReg == getRegisterOperand()) {
                            state = State.SEEN_ALOAD;
                        } else {
                            state = State.SEEN_NOTHING;
                            lastNullCheckedReg = -1;
                        }

                    }
                break;

                case SEEN_ALOAD:
                    if ((seen == INVOKEVIRTUAL) || (seen == INVOKEINTERFACE)) {
                        if ("close".equals(getNameConstantOperand()) && ("()V".equals(getSigConstantOperand()))) {
                            JavaClass cls = Repository.lookupClass(getClassConstantOperand());
                            if (cls.implementationOf(autoCloseableClass)) {

                                tb = findEnclosingFinally(pc);
                                if (tb != null) {
                                    if (stack.getStackDepth() > 0) {
                                        OpcodeStack.Item itm = stack.getStackItem(0);
                                        int closeableReg = itm.getRegisterNumber();
                                        if (closeableReg >= 0) {
                                            Integer storePC = regStoredPCs.get(closeableReg);
                                            if (storePC != null) {
                                                if (storePC <= tb.getStartPC()) {
                                                    // save this location off
                                                }
                                            }
                                        }
                                    }
                                }
                                // wait to see if addSuppressedException is called
                            }
                        }
                    }
                    state = State.SEEN_NOTHING;
                    lastNullCheckedReg = -1;
                break;
            }

            lastGoto = (((seen == GOTO) || (seen == GOTO_W)) && (getBranchOffset() > 0)) ? getBranchTarget() : -1;
        } catch (ClassNotFoundException e) {
            bugReporter.reportMissingClass(e);
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

    private TryBlock findEnclosingFinally(int pc) {
        if (finallyBlocks.isEmpty()) {
            return null;
        }

        TryBlock deepest = null;
        for (TryBlock tb : finallyBlocks.values()) {
            int handlerPC = tb.getHandlerPC();
            if ((deepest == null) || ((handlerPC <= pc) && (deepest.getHandlerPC() < handlerPC))) {
                deepest = tb;
            }
        }

        return deepest;
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
