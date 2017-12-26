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
import java.util.Map;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for code that builds an array by using a StringTokenizer to break up a string and place individual elements into an array. It is simpler to use
 * String.split instead.
 */
@CustomUserValue
public class UseSplit extends BytecodeScanningDetector {
    enum State {
        SEEN_NOTHING, SEEN_STRINGTOKENIZER, SEEN_COUNTTOKENS, SEEN_NEWARRAY, SEEN_HASMORE, SEEN_NEXT, SEEN_ARRAYSTORE
    }

    private BugReporter bugReporter;
    private OpcodeStack stack;
    private Map<Integer, State> regValueType;
    private State state;
    private int loopStart, loopEnd;

    /**
     * constructs a USS detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public UseSplit(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to make sure the class is at least java 1.4 and to reset the opcode stack
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            JavaClass cls = classContext.getJavaClass();
            if (cls.getMajor() >= Const.MAJOR_1_4) {
                stack = new OpcodeStack();
                regValueType = new HashMap<Integer, State>();
                super.visitClassContext(classContext);
            }
        } finally {
            stack = null;
            regValueType = null;
        }
    }

    /**
     * implements the visitor to reset the stack
     *
     * @param obj
     *            the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        regValueType.clear();
        state = State.SEEN_NOTHING;
        loopStart = -1;
        loopEnd = -1;
        super.visitCode(obj);
    }

    /**
     * implements the visitor to look for code that uses StringTokenizer when a simple String.split could be used.
     *
     * @param seen
     *            the currently parsed opcode
     */
    @Override
    public void sawOpcode(int seen) {
        try {
            stack.precomputation(this);

            int pc = getPC();
            if ((loopEnd != -1) && (pc > loopEnd)) {
                loopStart = -1;
                loopEnd = -1;
                regValueType.clear();
            }

            if (OpcodeUtils.isALoad(seen)) {
                int reg = RegisterUtils.getALoadReg(this, seen);
                State type = regValueType.get(Integer.valueOf(reg));
                if (type == null) {
                    state = State.SEEN_NOTHING;
                } else {
                    state = type;
                }
                return;
            }
            if (OpcodeUtils.isAStore(seen)) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    int reg = RegisterUtils.getAStoreReg(this, seen);
                    regValueType.put(Integer.valueOf(reg), (State) item.getUserValue());
                }
                state = State.SEEN_NOTHING;
                return;
            }
            if (OpcodeUtils.isILoad(seen)) {
                int reg = RegisterUtils.getLoadReg(this, seen);
                State type = regValueType.get(Integer.valueOf(reg));
                if (type == null) {
                    state = State.SEEN_NOTHING;
                } else {
                    state = type;
                }
                return;
            }
            if (OpcodeUtils.isIStore(seen)) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    int reg = RegisterUtils.getStoreReg(this, seen);
                    regValueType.put(Integer.valueOf(reg), (State) item.getUserValue());
                }
                state = State.SEEN_NOTHING;
                return;
            }

            switch (state) {
                case SEEN_NOTHING:
                    if ((seen == Const.INVOKESPECIAL) && "java/util/StringTokenizer".equals(getClassConstantOperand())
                            && Values.CONSTRUCTOR.equals(getNameConstantOperand())
                            && SignatureBuilder.SIG_TWO_STRINGS_TO_VOID.equals(getSigConstantOperand())) {
                        state = State.SEEN_STRINGTOKENIZER;
                    }
                break;

                case SEEN_STRINGTOKENIZER:
                    if (seen == Const.INVOKEVIRTUAL) {
                        String methodName = getNameConstantOperand();
                        String signature = getSigConstantOperand();
                        if ("countTokens".equals(methodName) && SignatureBuilder.SIG_VOID_TO_INT.equals(signature))
                            state = State.SEEN_COUNTTOKENS;
                        else if ("hasMoreTokens".equals(methodName) || "hasMoreElements".equals(methodName))
                            state = State.SEEN_HASMORE;
                        else if ("nextToken".equals(methodName) || "nextElement".equals(methodName)) {
                            if ((pc < loopStart) || (pc > loopEnd))
                                regValueType.clear();
                            else
                                state = State.SEEN_NEXT;
                        }
                    }
                break;

                case SEEN_COUNTTOKENS:
                    if (seen == Const.ANEWARRAY)
                        state = State.SEEN_NEWARRAY;
                    else if (seen == Const.IF_ICMPGE) {
                        int target = getBranchTarget() - 3;// sizeof goto
                        byte[] code = getCode().getCode();
                        if ((code[target] & 0x000000FF) == Const.GOTO) {
                            int offset = (code[target + 1] << 1) + code[target + 2];
                            int gotoTarget = target + offset + 3;
                            if (gotoTarget < getPC()) {
                                loopStart = gotoTarget;
                                loopEnd = target;
                            }
                        }
                    }
                break;

                case SEEN_HASMORE:
                    if (seen == Const.IFEQ) {
                        int target = getBranchTarget() - 3;// sizeof goto
                        byte[] code = getCode().getCode();
                        if ((code[target] & 0x000000FF) == Const.GOTO) {
                            int offset = (code[target + 1] << 1) + code[target + 2];
                            int gotoTarget = target + offset + 3;
                            if (gotoTarget < getPC()) {
                                loopStart = gotoTarget;
                                loopEnd = target;
                            }
                        }
                    }
                    state = State.SEEN_NOTHING;
                break;

                case SEEN_NEXT:
                    if ((seen == Const.AASTORE) && (pc > loopStart) && (pc < loopEnd) && (stack.getStackDepth() > 2)) {
                        OpcodeStack.Item arrayItem = stack.getStackItem(2);
                        State arrayType = (State) arrayItem.getUserValue();
                        OpcodeStack.Item elemItem = stack.getStackItem(0);
                        State elemType = (State) elemItem.getUserValue();
                        if ((arrayType == State.SEEN_NEWARRAY) && (elemType == State.SEEN_NEXT)) {
                            bugReporter.reportBug(new BugInstance(this, BugType.USS_USE_STRING_SPLIT.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                                    .addSourceLine(this));
                        }
                    }
                    state = State.SEEN_NOTHING;
                break;

                case SEEN_ARRAYSTORE:
                case SEEN_NEWARRAY:
                break;
            }
        } finally {
            TernaryPatcher.pre(stack, seen);
            stack.sawOpcode(this, seen);
            TernaryPatcher.post(stack, seen);
            if ((state != State.SEEN_NOTHING) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item item = stack.getStackItem(0);
                item.setUserValue(state);
            }
        }
    }

}
