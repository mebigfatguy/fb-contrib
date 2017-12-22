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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.CodeException;
import org.apache.bcel.classfile.JavaClass;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.CollectionUtils;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
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
    private JavaClass throwableClass;
    private BugReporter bugReporter;
    private OpcodeStack stack;
    private Map<Integer, TryBlock> finallyBlocks;
    private Map<Integer, Integer> regStoredPCs;
    private int lastGotoPC;
    private int lastNullCheckedReg;
    private int bugPC;
    private int closePC;
    private int suppressedPC;
    private State state;

    public UseTryWithResources(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
        try {
            throwableClass = Repository.lookupClass(Values.SLASHED_JAVA_LANG_THROWABLE);
            autoCloseableClass = Repository.lookupClass("java/lang/AutoCloseable");
        } catch (ClassNotFoundException e) {
            bugReporter.reportMissingClass(e);
        }
    }

    @Override
    public void visitClassContext(ClassContext classContext) {

        if (autoCloseableClass == null) {
            return;
        }

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
        finallyBlocks.clear();
        if (prescreen(obj)) {
            stack.resetForMethodEntry(this);
            regStoredPCs.clear();
            lastGotoPC = -1;
            state = State.SEEN_NOTHING;
            lastNullCheckedReg = -1;
            bugPC = -1;
            closePC = -1;
            suppressedPC = -1;
            super.visitCode(obj);

            if ((closePC >= 0) && (suppressedPC < 0)) {
                bugReporter.reportBug(new BugInstance(this, BugType.UTWR_USE_TRY_WITH_RESOURCES.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                        .addSourceLine(this, bugPC));
            }
        }
    }

    @Override
    public void sawOpcode(int seen) {

        if (suppressedPC >= 0) {
            return;
        }

        try {
            int pc = getPC();
            Iterator<TryBlock> it = finallyBlocks.values().iterator();
            while (it.hasNext()) {
                TryBlock tb = it.next();
                if (tb.getHandlerEndPC() < pc) {
                    it.remove();
                }
            }
            TryBlock tb = finallyBlocks.get(Integer.valueOf(pc));
            if (tb != null) {
                if (lastGotoPC > -1) {
                    tb.setHandlerEndPC(lastGotoPC);
                }
            }

            if (OpcodeUtils.isAStore(seen)) {
                regStoredPCs.put(Integer.valueOf(getRegisterOperand()), Integer.valueOf(pc));
            }

            switch (state) {
                case SEEN_NOTHING:
                    sawOpcodeAfterNothing(seen);
                break;

                case SEEN_IFNULL:
                    sawOpcodeAfterNullCheck(seen);
                break;

                case SEEN_ALOAD:
                    sawOpcodeAfterLoad(seen, pc);
                break;
            }

            lastGotoPC = (((seen == GOTO) || (seen == GOTO_W)) && (getBranchOffset() > 0)) ? getBranchTarget() : -1;
        } catch (ClassNotFoundException e) {
            bugReporter.reportMissingClass(e);
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    private void sawOpcodeAfterNothing(int seen) throws ClassNotFoundException {
        if ((seen == IFNULL) && (stack.getStackDepth() >= 1)) {
            OpcodeStack.Item itm = stack.getStackItem(0);
            lastNullCheckedReg = itm.getRegisterNumber();
            state = lastNullCheckedReg >= 0 ? State.SEEN_IFNULL : State.SEEN_NOTHING;
        } else {
            lastNullCheckedReg = -1;
        }

        if ((bugPC >= 0) && ((seen == INVOKEVIRTUAL) || (seen == INVOKEINTERFACE)) && "addSuppressed".equals(getNameConstantOperand())
                && SignatureBuilder.SIG_THROWABLE_TO_VOID.equals(getSigConstantOperand())
                && Repository.lookupClass(getClassConstantOperand()).instanceOf(throwableClass)) {
            closePC = -1;
            bugPC = -1;
            suppressedPC = getPC();
        }
    }

    private void sawOpcodeAfterNullCheck(int seen) {
        if (!OpcodeUtils.isALoad(seen)) {
            return;
        }
        if (lastNullCheckedReg == getRegisterOperand()) {
            state = State.SEEN_ALOAD;
        } else {
            state = State.SEEN_NOTHING;
            lastNullCheckedReg = -1;
        }
    }

    private void sawOpcodeAfterLoad(int seen, int pc) throws ClassNotFoundException {
        if (((seen == INVOKEVIRTUAL) || (seen == INVOKEINTERFACE)) && "close".equals(getNameConstantOperand())
                && SignatureBuilder.SIG_VOID_TO_VOID.equals(getSigConstantOperand())
                && Repository.lookupClass(getClassConstantOperand()).implementationOf(autoCloseableClass)) {
            TryBlock tb = findEnclosingFinally(pc);
            if ((tb != null) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item itm = stack.getStackItem(0);
                int closeableReg = itm.getRegisterNumber();
                if (closeableReg >= 0) {
                    Integer storePC = regStoredPCs.get(Integer.valueOf(closeableReg));
                    if ((storePC != null) && (storePC.intValue() <= tb.getStartPC())) {
                        bugPC = pc;
                        closePC = tb.getHandlerEndPC();
                    }
                }
            }
        }
        state = State.SEEN_NOTHING;
        lastNullCheckedReg = -1;
    }

    private boolean prescreen(Code obj) {

        if (getMethod().isNative()) {
            return false;
        }

        CodeException[] ces = obj.getExceptionTable();
        if (CollectionUtils.isEmpty(ces)) {
            return false;
        }

        boolean hasFinally = false;
        for (CodeException ce : ces) {
            if (ce.getCatchType() == 0) {
                finallyBlocks.put(Integer.valueOf(ce.getHandlerPC()),
                        new TryBlock(ce.getStartPC(), ce.getEndPC(), ce.getHandlerPC(), obj.getCode().length - 1));
                hasFinally = true;
            }
        }

        return hasFinally;
    }

    @Nullable
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

        public TryBlock(int startPC, int endPC, int handlerPC, int handlerEndPC) {
            super();
            this.startPC = startPC;
            this.endPC = endPC;
            this.handlerPC = handlerPC;
            this.handlerEndPC = handlerEndPC;
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
