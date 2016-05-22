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

import java.util.ArrayList;
import java.util.List;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantClass;
import org.apache.bcel.classfile.ConstantString;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for complex if conditions where multiple or clauses are joined together where the same variable is compared against a number of static values. This
 * pattern is much more easy to read if you put those values in a static set, and just use one contains(value) call. The set name adds self-documentation as
 * well.
 */
public class ContainsBasedConditional extends BytecodeScanningDetector {

    private static final int LOW_CONDITIONAL_COUNT = 3;
    private static final int NORMAL_CONDITIONAL_COUNT = 4;
    private static final int HIGH_CONDITIONAL_COUNT = 6;

    private enum State {
        SAW_NOTHING, SAW_LOAD, SAW_CONST, SAW_EQUALS, SAW_PATTERN
    };

    private BugReporter bugReporter;
    private OpcodeStack stack;
    private List<Integer> switchLocs;
    private State state;
    private int loadType;
    private String constType;
    private int conditionCount;
    private int bugPC;

    /**
     * constructs a CBC detector given the reporter to report bugs on
     *
     * @param reporter
     *            the sync of bug reports
     */
    public ContainsBasedConditional(BugReporter reporter) {
        bugReporter = reporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            switchLocs = new ArrayList<Integer>();
            super.visitClassContext(classContext);
        } finally {
            switchLocs = null;
            stack = null;
        }
    }

    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        state = State.SAW_NOTHING;
        loadType = 0;
        constType = null;
        conditionCount = 0;
        bugPC = 0;
        switchLocs.clear();
        super.visitCode(obj);
    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "SF_SWITCH_FALLTHROUGH", justification = "This fall-through is deliberate and documented")
    @Override
    public void sawOpcode(int seen) {
        try {
            stack.precomputation(this);

            if ((seen == LOOKUPSWITCH) || (seen == TABLESWITCH)) {
                switchLocs.add(Integer.valueOf(getPC()));
                for (int offset : getSwitchOffsets()) {
                    switchLocs.add(Integer.valueOf(getPC() + offset));
                }
            }

            if (!switchLocs.isEmpty() && (getPC() == switchLocs.get(0).intValue())) {
                state = State.SAW_NOTHING;
                switchLocs.remove(0);
                return;
            }

            switch (state) {
                case SAW_NOTHING:
                    conditionCount = 0;
                    //$FALL-THROUGH$
                case SAW_PATTERN:
                    if (isLoad(seen)) {
                        if (conditionCount > 0) {
                            if (loadType == seen) {
                                state = State.SAW_LOAD;
                            } else {
                                state = State.SAW_NOTHING;
                            }
                        } else {
                            loadType = seen;
                            bugPC = getPC();
                            state = State.SAW_LOAD;
                        }
                    } else {
                        if (conditionCount >= LOW_CONDITIONAL_COUNT) {
                            bugReporter.reportBug(new BugInstance(this, BugType.CBC_CONTAINS_BASED_CONDITIONAL.name(), priority(conditionCount)).addClass(this)
                                    .addMethod(this).addSourceLine(this, bugPC));
                        }
                    }
                break;

                case SAW_LOAD:
                    if ((seen == LDC) || (seen == LDC_W)) {
                        Constant c = getConstantRefOperand();
                        String currConstType = null;
                        if (c instanceof ConstantString) {
                            currConstType = Values.SLASHED_JAVA_LANG_STRING;
                        } else if (c instanceof ConstantClass) {
                            currConstType = Values.SLASHED_JAVA_LANG_CLASS;
                        }
                        if (conditionCount > 0) {
                            if ((constType != null) && constType.equals(currConstType)) {
                                state = State.SAW_CONST;
                            } else {
                                state = State.SAW_NOTHING;
                            }
                        } else if (currConstType != null) {
                            state = State.SAW_CONST;
                            constType = currConstType;
                        } else {
                            state = State.SAW_NOTHING;
                        }
                    } else if (seen == GETSTATIC) {
                        state = State.SAW_CONST;

                    } else if ((seen >= ICONST_M1) && (seen <= ICONST_5)) {
                        state = State.SAW_CONST;

                    } else if ((seen >= LCONST_0) && (seen <= LCONST_1)) {
                        state = State.SAW_CONST;

                    } else {
                        state = State.SAW_NOTHING;
                    }
                break;

                case SAW_CONST:
                    if ((seen == INVOKEVIRTUAL) && "equals".equals(getNameConstantOperand()) && "(Ljava/lang/Object;)Z".equals(getSigConstantOperand())) {
                        state = State.SAW_EQUALS;
                    } else if (seen == IF_ICMPEQ) {
                        conditionCount++;
                        state = State.SAW_PATTERN;
                    } else if (seen == IF_ICMPNE) {
                        conditionCount++;
                        if (conditionCount >= LOW_CONDITIONAL_COUNT) {
                            bugReporter.reportBug(new BugInstance(this, BugType.CBC_CONTAINS_BASED_CONDITIONAL.name(), priority(conditionCount)).addClass(this)
                                    .addMethod(this).addSourceLine(this, bugPC));
                        }
                        state = State.SAW_NOTHING;
                    } else {
                        state = State.SAW_NOTHING;
                    }
                break;

                case SAW_EQUALS:
                    if (seen == IFNE) {
                        conditionCount++;
                        state = State.SAW_PATTERN;
                    } else if (seen == IFEQ) {
                        conditionCount++;
                        if (conditionCount >= LOW_CONDITIONAL_COUNT) {
                            bugReporter.reportBug(new BugInstance(this, BugType.CBC_CONTAINS_BASED_CONDITIONAL.name(), priority(conditionCount)).addClass(this)
                                    .addMethod(this).addSourceLine(this, bugPC));
                        }
                        state = State.SAW_NOTHING;
                    } else {
                        state = State.SAW_NOTHING;
                    }
                break;
                default: // this indicates incompatible code change
                    throw new AssertionError("Unhandled state: " + state);
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    private static boolean isLoad(int seen) {
        return OpcodeUtils.isALoad(seen) || OpcodeUtils.isILoad(seen) || OpcodeUtils.isLLoad(seen);
    }

    private static int priority(int conditionCount) {
        return (conditionCount < NORMAL_CONDITIONAL_COUNT) ? LOW_PRIORITY : (conditionCount < HIGH_CONDITIONAL_COUNT) ? NORMAL_PRIORITY : HIGH_PRIORITY;
    }
}
