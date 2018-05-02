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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import org.apache.bcel.classfile.Code;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.ToString;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XField;
import edu.umd.cs.findbugs.ba.XMethod;

/**
 * looks for odd usage patterns when using Sets
 */
@CustomUserValue
public class SetUsageIssues extends BytecodeScanningDetector {

    private static final FQMethod CONTAINS_METHOD = new FQMethod("java/util/Set", "contains", SignatureBuilder.SIG_OBJECT_TO_BOOLEAN);
    private static final FQMethod ADD_METHOD = new FQMethod("java/util/Set", "add", SignatureBuilder.SIG_OBJECT_TO_BOOLEAN);
    private static final FQMethod REMOVE_METHOD = new FQMethod("java/util/Set", "remove", SignatureBuilder.SIG_OBJECT_TO_BOOLEAN);

    private BugReporter bugReporter;
    private OpcodeStack stack;
    private Map<SetRef, Contains> setContainsUsed;

    /**
     * constructs a SUI detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public SetUsageIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            setContainsUsed = new HashMap<>();
            super.visitClassContext(classContext);
        } finally {
            setContainsUsed = null;
            stack = null;
        }
    }

    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        setContainsUsed.clear();
        super.visitCode(obj);
    }

    @Override
    public void sawOpcode(int seen) {
        SetRef userValue = null;

        try {
            if (!setContainsUsed.isEmpty()) {
                Iterator<Contains> it = setContainsUsed.values().iterator();
                while (it.hasNext()) {
                    if (it.next().getScopeEnd() <= getPC()) {
                        it.remove();
                    }
                }
            }

            if (seen == INVOKEINTERFACE) {
                FQMethod fqm = new FQMethod(getClassConstantOperand(), getNameConstantOperand(), getSigConstantOperand());
                if (CONTAINS_METHOD.equals(fqm)) {
                    if (stack.getStackDepth() >= 2) {
                        SetRef sr = new SetRef(stack.getStackItem(1));
                        setContainsUsed.put(sr, new Contains(stack.getStackItem(0)));
                        userValue = sr;
                    }
                } else if (ADD_METHOD.equals(fqm)) {
                    if (stack.getStackDepth() >= 2) {
                        OpcodeStack.Item itm = stack.getStackItem(1);
                        Contains contains = setContainsUsed.remove(new SetRef(itm));
                        if ((contains != null) && new Contains(stack.getStackItem(0)).equals(contains) && !contains.isContained()) {
                            bugReporter.reportBug(new BugInstance(this, BugType.SUI_CONTAINS_BEFORE_ADD.name(), contains.getReportLevel()).addClass(this)
                                    .addMethod(this).addSourceLine(this));
                        }
                    }
                } else if (REMOVE_METHOD.equals(fqm)) {
                    if (stack.getStackDepth() >= 2) {
                        OpcodeStack.Item itm = stack.getStackItem(1);
                        Contains contains = setContainsUsed.remove(new SetRef(itm));
                        if ((contains != null) && new Contains(stack.getStackItem(0)).equals(contains) && contains.isContained()) {
                            bugReporter.reportBug(new BugInstance(this, BugType.SUI_CONTAINS_BEFORE_REMOVE.name(), contains.getReportLevel()).addClass(this)
                                    .addMethod(this).addSourceLine(this));
                        }
                    }
                }
            } else if ((seen == IFNE) || (seen == IFEQ)) {
                if ((stack.getStackDepth() > 0) && (getBranchOffset() > 0)) {
                    OpcodeStack.Item itm = stack.getStackItem(0);
                    SetRef sr = (SetRef) itm.getUserValue();
                    if (sr != null) {
                        Contains contains = setContainsUsed.get(sr);
                        if (contains != null) {
                            contains.setScopeEnd(getBranchTarget());
                            contains.setContained(seen == IFEQ);
                        }
                    }
                }
            }

        } finally {
            stack.sawOpcode(this, seen);
            if ((userValue != null) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item itm = stack.getStackItem(0);
                itm.setUserValue(userValue);
            }
        }
    }

    static class Contains {
        private Object keyValue;
        private int reportLevel;
        private int scopeEnd;
        private boolean isContained;

        public Contains(OpcodeStack.Item itm) {
            int reg = itm.getRegisterNumber();
            if (reg >= 0) {
                keyValue = Integer.valueOf(reg);
                reportLevel = NORMAL_PRIORITY;
            } else {
                XField xf = itm.getXField();
                if (xf != null) {
                    keyValue = xf;
                    reportLevel = NORMAL_PRIORITY;
                } else {
                    Object cons = itm.getConstant();
                    if (cons != null) {
                        keyValue = cons;
                        reportLevel = NORMAL_PRIORITY;
                    } else {
                        XMethod xm = itm.getReturnValueOf();
                        if (xm != null) {
                            keyValue = xm;
                            reportLevel = LOW_PRIORITY;
                        }
                        keyValue = null;
                    }
                }
            }
            scopeEnd = Integer.MAX_VALUE;
        }

        public boolean isContained() {
            return isContained;
        }

        public void setContained(boolean contained) {
            isContained = contained;
        }

        public void setScopeEnd(int pc) {
            scopeEnd = pc;
        }

        public int getScopeEnd() {
            return scopeEnd;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Contains)) {
                return false;
            }

            Contains that = (Contains) o;

            if ((keyValue == null) || (that.keyValue == null)) {
                return false;
            }

            return keyValue.equals(that.keyValue);
        }

        @Override
        public int hashCode() {
            return keyValue == null ? 0 : keyValue.hashCode();
        }

        public int getReportLevel() {
            return reportLevel;
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }

    static class SetRef {
        private int register;
        private XField field;

        public SetRef(OpcodeStack.Item itm) {
            int reg = itm.getRegisterNumber();
            if (reg >= 0) {
                register = reg;
            } else {
                XField xf = itm.getXField();
                if (xf != null) {
                    field = xf;
                }
                register = -1;
            }
        }

        @Override
        public int hashCode() {
            if (register >= 0) {
                return register;
            }

            if (field != null) {
                return field.hashCode();
            }

            return Integer.MAX_VALUE;
        }

        public boolean isValid() {
            return (register >= 0) || (field != null);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SetRef)) {
                return false;
            }

            SetRef that = (SetRef) o;

            return (register == that.register) && Objects.equals(field, that.field);
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }
}
