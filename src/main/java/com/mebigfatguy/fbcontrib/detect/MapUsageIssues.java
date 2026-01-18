/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2019 Dave Brosius
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.bcel.Const;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.CodeByteUtils;
import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.QMethod;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableList;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.MethodAnnotation;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.SourceLineAnnotation;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XField;
import edu.umd.cs.findbugs.ba.XMethod;

/**
 * looks for odd usage patterns when using Maps
 */
@CustomUserValue
public class MapUsageIssues extends BytecodeScanningDetector {

    private static final FQMethod CONTAINS_METHOD = new FQMethod("java/util/Set", "contains", SignatureBuilder.SIG_OBJECT_TO_BOOLEAN);
    private static final FQMethod CONTAINSKEY_METHOD = new FQMethod("java/util/Map", "containsKey", SignatureBuilder.SIG_OBJECT_TO_BOOLEAN);
    private static final FQMethod GET_METHOD = new FQMethod("java/util/Map", "get", SignatureBuilder.SIG_OBJECT_TO_OBJECT);
    private static final FQMethod PUT_METHOD = new FQMethod("java/util/Map", "put", SignatureBuilder.SIG_TWO_OBJECTS_TO_OBJECT);
    private static final FQMethod REMOVE_METHOD = new FQMethod("java/util/Map", "remove", SignatureBuilder.SIG_OBJECT_TO_OBJECT);

    private static final QMethod SIZE_METHOD = new QMethod("size", SignatureBuilder.SIG_VOID_TO_INT);

    private static final Set<String> COLLECTION_ACCESSORS = UnmodifiableSet.create("keySet", "entrySet", Values.VALUES);
    private static final String CONCURRENT_HASH_MAP = "Ljava/util/concurrent/ConcurrentHashMap;";
    private static JavaClass mapClass;

    private static List<String> NORMAL_CTORS = UnmodifiableList.create("<init>", "newHashMap", "newHashMapWithExpectedSize");

    static {
        try {
            mapClass = Repository.lookupClass("java/util/Map");
        } catch (ClassNotFoundException cnfe) {
            mapClass = null;
        }
    }

    private BugReporter bugReporter;
    private OpcodeStack stack;
    private Map<MapRef, ContainsKey> mapContainsKeyUsed;
    private Map<MapRef, Get> mapGetUsed;
    private Map<String, ConcStatus> concurrentHashMapFields;
    private Map<String, BugLocation> staticMaps;

    /**
     * constructs a MUI detector given the reporter to report bugs on
     *
     * @param bugReporter the sync of bug reports
     */
    public MapUsageIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            mapContainsKeyUsed = new HashMap<>();
            mapGetUsed = new HashMap<>();
            concurrentHashMapFields = new HashMap<>();
            staticMaps = new HashMap<>();
            super.visitClassContext(classContext);

            for (BugLocation loc : staticMaps.values()) {
                if (loc != null) {
                    bugReporter.reportBug(new BugInstance(this, BugType.MUI_RETURNING_MUTABLE_STATIC_MAP.name(), NORMAL_PRIORITY).addClass(this)
                            .addMethod(loc.getMethodAnnotation()).addSourceLine(loc.getSrcLineAnnotation()));
                }
            }
        } finally {
            staticMaps = null;
            concurrentHashMapFields = null;
            mapContainsKeyUsed = null;
            mapGetUsed = null;
            stack = null;
        }
    }

    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        mapContainsKeyUsed.clear();
        mapGetUsed.clear();
        super.visitCode(obj);
    }

    @Override
    public void visitField(Field obj) {
        int mod = obj.getModifiers();
        if ((mod & Const.ACC_STATIC) == 0) {
            return;
        }
        if ((mod & Const.ACC_PRIVATE) == 0) {
            return;
        }
        if ((mod & Const.ACC_SYNTHETIC) != 0) {
            return;
        }

        if (obj.getName().contains("$")) {
            return;
        }

        try {
            String sig = obj.getSignature();
            if (sig.startsWith("L") && !sig.startsWith("Ljava/lang/")) {
                JavaClass fieldClass = Repository.lookupClass(SignatureUtils.stripSignature(sig));
                if (fieldClass.instanceOf(mapClass)) {
                    staticMaps.put(obj.getName(), null);
                }
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        }
    }

    @Override
    public void sawOpcode(int seen) {
        ConcStatus userValue = null;
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

            // checking for a branch might be overkill, but for now lets go with it
            if (!mapGetUsed.isEmpty() && OpcodeUtils.isBranch(seen)) {
                Iterator<Map.Entry<MapRef, Get>> it = mapGetUsed.entrySet().iterator();
                while (it.hasNext()) {
                    it.next();
                    it.remove();
                }
            }

            clearConcFields();

            if (seen == Const.PUTSTATIC) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item itm = stack.getStackItem(0);
                    if (CONCURRENT_HASH_MAP.equals(itm.getSignature()) && getClassName().equals(getClassConstantOperand())) {
                        concurrentHashMapFields.put(getNameConstantOperand(), new ConcStatus(getNameConstantOperand()));
                    } else {
                        concurrentHashMapFields.remove(getNameConstantOperand());
                    }

                    if (staticMaps.containsKey(getNameConstantOperand())) {
                        XMethod rv = itm.getReturnValueOf();
                        if (rv != null) {
                            String name = rv.getName();
                            if (!NORMAL_CTORS.contains(name)) {
                                staticMaps.remove(getNameConstantOperand());
                            }
                        } else {
                            String sig = itm.getSignature();
                            if (sig.contains("Unmodifiable") || sig.contains("Immutable") || sig.contains("Singleton")) {
                                staticMaps.remove(getNameConstantOperand());
                            }
                        }
                    }
                }
            } else if (seen == Const.ARETURN) {
                if ((getMethod().getModifiers() & (Const.ACC_PUBLIC | Const.ACC_SYNTHETIC)) == Const.ACC_PUBLIC) {
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item itm = stack.getStackItem(0);
                        XField xf = itm.getXField();
                        if (xf != null && staticMaps.containsKey(xf.getName())) {
                            staticMaps.put(xf.getName(),
                                    new BugLocation(MethodAnnotation.fromVisitedMethod(this), SourceLineAnnotation.fromVisitedInstruction(this)));
                        }
                    }
                }
            } else if ((seen == Const.IFNULL) || (seen == Const.IFNONNULL)) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item itm = stack.getStackItem(0);
                    XMethod method = itm.getReturnValueOf();
                    if ((method != null) && (mapClass != null)) {
                        if (COLLECTION_ACCESSORS.contains(method.getName())) {

                            JavaClass cls = Repository.lookupClass(method.getClassName());
                            if (cls.implementationOf(mapClass)) {
                                bugReporter.reportBug(new BugInstance(this, BugType.MUI_NULL_CHECK_ON_MAP_SUBSET_ACCESSOR.name(), NORMAL_PRIORITY)
                                        .addClass(this).addMethod(this).addSourceLine(this));
                            }

                        }
                    }

                    if (seen == Const.IFNONNULL) {
                        ConcStatus cs = (ConcStatus) itm.getUserValue();
                        if (cs != null) {
                            int target = this.getBranchTarget();
                            if (target > getPC()) {
                                cs.setCheckEnd(target);
                            } else {
                                cs.setCheckEnd(0);
                            }
                        }

                    }
                }
            } else if (seen == Const.INVOKEINTERFACE) {
                FQMethod fqm = new FQMethod(getClassConstantOperand(), getNameConstantOperand(), getSigConstantOperand());
                if (CONTAINS_METHOD.equals(fqm)) {
                    if (stack.getStackDepth() >= 2) {
                        OpcodeStack.Item item = stack.getStackItem(1);
                        if ((item.getRegisterNumber() < 0) && (item.getXField() == null)) {
                            XMethod xm = item.getReturnValueOf();
                            if ((xm != null) && COLLECTION_ACCESSORS.contains(xm.getName()) && Values.DOTTED_JAVA_UTIL_MAP.equals(xm.getClassName())) {
                                bugReporter.reportBug(new BugInstance(this, BugType.MUI_USE_CONTAINSKEY.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                                        .addSourceLine(this));
                            }
                        }
                    }
                } else if (CONTAINSKEY_METHOD.equals(fqm)) {
                    if (getNextOpcode() == Const.IFEQ) {
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
                        if ((ck != null) && new ContainsKey(stack.getStackItem(0), 0).equals(ck)) {
                            bugReporter.reportBug(new BugInstance(this, BugType.MUI_CONTAINSKEY_BEFORE_GET.name(), ck.getReportLevel()).addClass(this)
                                    .addMethod(this).addSourceLine(this));
                        }

                        mapGetUsed.put(new MapRef(itm), new Get(stack.getStackItem(0)));

                        if (itm.getXField() != null) {
                            userValue = concurrentHashMapFields.get(itm.getXField().getName());
                        }
                    }
                } else if (PUT_METHOD.equals(fqm)) {
                    if (stack.getStackDepth() >= 3) {
                        OpcodeStack.Item itm = stack.getStackItem(2);
                        if (itm.getXField() != null) {
                            ConcStatus cs = concurrentHashMapFields.get(itm.getXField().getName());
                            if (cs != null && cs.getCheckEnd() != 0) {
                                bugReporter.reportBug(new BugInstance(this, BugType.MUI_USE_COMPUTEIFABSENT.name(), NORMAL_PRIORITY).addClass(this)
                                        .addMethod(this).addSourceLine(this));

                            }
                        }
                    }
                } else if (REMOVE_METHOD.equals(fqm)) {
                    if (stack.getStackDepth() >= 2) {
                        OpcodeStack.Item itm = stack.getStackItem(1);
                        Get get = mapGetUsed.remove(new MapRef(itm));
                        if ((get != null) && new Get(stack.getStackItem(0)).equals(get)) {
                            bugReporter.reportBug(new BugInstance(this, BugType.MUI_GET_BEFORE_REMOVE.name(), get.getReportLevel()).addClass(this)
                                    .addMethod(this).addSourceLine(this));
                        }
                    }
                }

                QMethod qm = new QMethod(getNameConstantOperand(), getSigConstantOperand());
                if (SIZE_METHOD.equals(qm)) {
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item itm = stack.getStackItem(0);
                        if ((itm.getRegisterNumber() < 0) && (itm.getXField() == null)) {
                            XMethod xm = itm.getReturnValueOf();
                            if ((xm != null) && Values.DOTTED_JAVA_UTIL_MAP.equals(xm.getClassName()) && COLLECTION_ACCESSORS.contains(xm.getName())) {
                                bugReporter.reportBug(new BugInstance(this, BugType.MUI_CALLING_SIZE_ON_SUBCONTAINER.name(), NORMAL_PRIORITY).addClass(this)
                                        .addMethod(this).addSourceLine(this));
                            }
                        }
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            bugReporter.reportMissingClass(e);
        } finally {
            stack.sawOpcode(this, seen);
            if (userValue != null && stack.getStackDepth() > 0) {
                OpcodeStack.Item itm = stack.getStackItem(0);
                itm.setUserValue(userValue);
            }
        }
    }

    private void clearConcFields() {
        for (ConcStatus cs : concurrentHashMapFields.values()) {
            if (cs.getCheckEnd() != 0 && getPC() >= cs.getCheckEnd()) {
                cs.setCheckEnd(0);
            }
        }
    }

    @SuppressWarnings("CPD-START")
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
        public int hashCode() {
            return keyValue == null ? 0 : keyValue.hashCode();
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }

    static class Get {
        private Object keyValue;
        private int reportLevel;

        public Get(OpcodeStack.Item itm) {
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

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Get)) {
                return false;
            }

            Get that = (Get) o;

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

    static class ConcStatus {

        private final String fieldName;
        private int checkEnd;

        public ConcStatus(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getFieldName() {
            return fieldName;
        }

        public int getCheckEnd() {
            return checkEnd;
        }

        public void setCheckEnd(int checkEnd) {
            this.checkEnd = checkEnd;
        }
    }

    static class BugLocation {
        private final MethodAnnotation methodAnnotation;
        private final SourceLineAnnotation srcLineAnnotation;

        public BugLocation(MethodAnnotation methodAnnotation, SourceLineAnnotation srcLineAnnotation) {
            super();
            this.methodAnnotation = methodAnnotation;
            this.srcLineAnnotation = srcLineAnnotation;
        }

        public MethodAnnotation getMethodAnnotation() {
            return methodAnnotation;
        }

        public SourceLineAnnotation getSrcLineAnnotation() {
            return srcLineAnnotation;
        }
    }
}
