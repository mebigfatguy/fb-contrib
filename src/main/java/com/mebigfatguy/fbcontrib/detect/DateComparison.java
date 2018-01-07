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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;

/**
 * Looks for inefficient comparison of Date objects using two comparisons when one would do.
 */
public class DateComparison extends BytecodeScanningDetector {
    enum State {
        SAW_NOTHING, SAW_LOAD1_1, SAW_LOAD1_2, SAW_CMP1, SAW_IFNE, SAW_LOAD2_1, SAW_LOAD2_2, SAW_CMP2
    }

    private static final Set<String> dateClasses;

    static {
        Set<String> dc = new HashSet<String>();
        dc.add("java.util.Date");
        dc.add("java.sql.Date");
        dc.add("java.sql.Timestamp");
        dateClasses = Collections.unmodifiableSet(dc);
    }

    private final BugReporter bugReporter;
    private State state;
    private int register1_1;
    private int register1_2;
    private int register2_1;
    private int register2_2;

    /**
     * constructs a DDC detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public DateComparison(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * overrides the visitor to reset the registers
     *
     * @param obj
     *            the method of the currently parsed method
     */
    @Override
    public void visit(Method obj) {
        state = State.SAW_NOTHING;
        register1_1 = -1;
        register1_2 = -1;
        register2_1 = -1;
        register2_2 = -1;
        super.visit(obj);
    }

    /**
     * overrides the visitor to look for double date compares using the same registers
     *
     * @param seen
     *            the current opcode parsed.
     */
    @Override
    public void sawOpcode(int seen) {
        switch (state) {
            case SAW_NOTHING:
                if (OpcodeUtils.isALoad(seen)) {
                    register1_1 = RegisterUtils.getALoadReg(this, seen);
                    state = State.SAW_LOAD1_1;
                }
            break;

            case SAW_LOAD1_1:
                if (OpcodeUtils.isALoad(seen)) {
                    register1_2 = RegisterUtils.getALoadReg(this, seen);
                }

                if (register1_2 > -1) {
                    state = State.SAW_LOAD1_2;
                } else {
                    state = State.SAW_NOTHING;
                }
            break;

            case SAW_LOAD1_2:
                if (seen == INVOKEVIRTUAL) {
                    String cls = getDottedClassConstantOperand();
                    if (dateClasses.contains(cls)) {
                        String methodName = getNameConstantOperand();
                        if ("equals".equals(methodName) || "after".equals(methodName) || "before".equals(methodName)) {
                            state = State.SAW_CMP1;
                        }
                    }
                }
                if (state != State.SAW_CMP1) {
                    state = State.SAW_NOTHING;
                }
            break;

            case SAW_CMP1:
                if (seen == IFNE) {
                    state = State.SAW_IFNE;
                } else {
                    state = State.SAW_NOTHING;
                }
            break;

            case SAW_IFNE:
                if (OpcodeUtils.isALoad(seen)) {
                    register2_1 = RegisterUtils.getALoadReg(this, seen);
                }

                if (register2_1 > -1) {
                    state = State.SAW_LOAD2_1;
                } else {
                    state = State.SAW_NOTHING;
                }
            break;

            case SAW_LOAD2_1:
                if (OpcodeUtils.isALoad(seen)) {
                    register2_2 = RegisterUtils.getALoadReg(this, seen);
                }

                if ((register2_2 > -1)
                        && (((register1_1 == register2_1) && (register1_2 == register2_2)) || ((register1_1 == register2_2) && (register1_2 == register2_1)))) {
                    state = State.SAW_LOAD2_2;
                } else {
                    state = State.SAW_NOTHING;
                }
            break;

            case SAW_LOAD2_2:
                if (seen == INVOKEVIRTUAL) {
                    String cls = getDottedClassConstantOperand();
                    if (dateClasses.contains(cls)) {
                        String methodName = getNameConstantOperand();
                        if ("equals".equals(methodName) || "after".equals(methodName) || "before".equals(methodName)) {
                            state = State.SAW_CMP2;
                        }
                    }
                }
                if (state != State.SAW_CMP2) {
                    state = State.SAW_NOTHING;
                }
            break;

            case SAW_CMP2:
                if (seen == IFEQ) {
                    bugReporter.reportBug(new BugInstance("DDC_DOUBLE_DATE_COMPARISON", NORMAL_PRIORITY).addClassAndMethod(this).addSourceLine(this));
                }
                state = State.SAW_NOTHING;
            break;

            default:
            break;
        }
    }
}
