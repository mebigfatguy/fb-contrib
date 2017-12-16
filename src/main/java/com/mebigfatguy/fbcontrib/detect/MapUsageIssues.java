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
import java.util.Objects;

import org.apache.bcel.classfile.Code;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.CodeByteUtils;
import com.mebigfatguy.fbcontrib.utils.FQMethod;
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
 * looks for odd usage patterns when using Maps
 */
@CustomUserValue
public class MapUsageIssues extends BytecodeScanningDetector {

    private static final FQMethod CONTAINSKEY_METHOD = new FQMethod("java/util/Map", "containsKey", "(Ljava/lang/Object;)Z");
    private static final FQMethod GET_METHOD = new FQMethod("java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
    private BugReporter bugReporter;
    private OpcodeStack stack;
    private Map<MapRef, ContainsKey> mapContainsKeyUsed;

    /**
     * constructs a MUP detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public MapUsageIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            mapContainsKeyUsed = new HashMap<>();
            super.visitClassContext(classContext);
        } finally {
            mapContainsKeyUsed = null;
            stack = null;
        }
    }

    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        mapContainsKeyUsed.clear();
        super.visitCode(obj);
    }

    @Override
    public void sawOpcode(int seen) {
        try {
            if (!mapContainsKeyUsed.isEmpty()) {
                Iterator<Map.Entry<MapRef, ContainsKey>> it = mapContainsKeyUsed.entrySet().iterator();
                int pc = getPC();
                while (it.hasNext()) {
                    Map.Entry<MapRef, ContainsKey> entry = it.next();
                    if (!entry.getKey().isValid() || entry.getValue().outOfScope(pc)) {
                        it.remove();
                    }
                }
            }
            if (seen == INVOKEINTERFACE) {
                FQMethod fqm = new FQMethod(getClassConstantOperand(), getNameConstantOperand(), getSigConstantOperand());
                if (CONTAINSKEY_METHOD.equals(fqm)) {
                    if (getNextOpcode() == IFEQ) {
                        int ifEnd = getNextPC() + CodeByteUtils.getshort(getCode().getCode(), getNextPC() + 1);
                        if (stack.getStackDepth() >= 2) {
                            OpcodeStack.Item itm = stack.getStackItem(1);
                            mapContainsKeyUsed.put(new MapRef(itm), new ContainsKey(stack.getStackItem(0), ifEnd));
                        }
                    }
                } else if (GET_METHOD.equals(fqm)) {
                    if (stack.getStackDepth() >= 2) {
                        OpcodeStack.Item itm = stack.getStackItem(1);
                        ContainsKey ck = mapContainsKeyUsed.remove(new MapRef(itm));
                        if (ck != null) {
                            bugReporter.reportBug(new BugInstance(this, BugType.MUP_CONTAINSKEY_BEFORE_GET.name(), ck.getReportLevel()).addClass(this)
                                    .addMethod(this).addSourceLine(this));
                        }
                    }
                }
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    static class ContainsKey {
        private Object keyValue;
        private int scopeEnd;
        private int reportLevel;

        public ContainsKey(OpcodeStack.Item itm, int ifEnd) {
            scopeEnd = ifEnd;
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
        }

        public boolean outOfScope(int pc) {
            return pc >= scopeEnd;
        }

        public int getReportLevel() {
            return reportLevel;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ContainsKey)) {
                return false;
            }

            ContainsKey that = (ContainsKey) o;

            if ((keyValue == null) || (that.keyValue == null)) {
                return false;
            }

            return keyValue.equals(that.keyValue);
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }

    static class MapRef {
        private int register;
        private XField field;

        public MapRef(OpcodeStack.Item itm) {
            int reg = itm.getRegisterNumber();
            if (reg >= 0) {
                register = reg;
            } else {
                XField xf = itm.getXField();
                if (xf != null) {
                    field = xf;
                }
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
            if (!(o instanceof MapRef)) {
                return false;
            }

            MapRef that = (MapRef) o;

            return (register == that.register) && Objects.equals(field, that.field);
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }
}
