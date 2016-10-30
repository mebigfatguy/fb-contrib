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

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.CodeException;
import org.apache.bcel.classfile.LineNumber;
import org.apache.bcel.classfile.LineNumberTable;

import com.mebigfatguy.fbcontrib.collect.MethodInfo;
import com.mebigfatguy.fbcontrib.collect.Statistics;
import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XField;

/**
 * looks for calls of the same method on the same object when that object hasn't changed. This often is redundant, and the second call can be removed, or
 * combined.
 */
@CustomUserValue
public class PossiblyRedundantMethodCalls extends BytecodeScanningDetector {
    public static final String PRMC_RISKY_FIELD_USER_KEY = "fbcontrib.PRMC.riskynames";
    public static final String PRMC_RISKY_CLASS_USER_KEY = "fbcontrib.PRMC.riskyclasses";
    public static final String PRMC_HIGH_BYTECOUNT = "fbcontrib.PRMC.highbytecount";
    public static final String PRMC_HIGH_METHODCALLS = "fbcontrib.PRMC.highmethodcalls";
    public static final String PRMC_NORMAL_BYTECOUNT = "fbcontrib.PRMC.normalbytecount";
    public static final String PRMC_NORMAL_METHODCALLS = "fbcontrib.PRMC.normalmethodcalls";

    /**
     * a collection of names that are to be checked against a currently parsed method, to see if that method is risky to be called redundant. The contents are
     * either
     * <ul>
     * <li>a simple name that can be found as <em>part</em> of the methodName, like "destroy" which would match destroy(), or destroyAll()</li>
     * <li>a fully qualified method name that exactly matches a method, like "java/lang/String.valueOf"</li>
     * </ul>
     */
    private static Set<String> riskyMethodNameContents = new HashSet<>();
    private static int highByteCountLimit = 200;
    private static int highMethodCallLimit = 10;
    private static int normalByteCountLimit = 50;
    private static int normalMethodCallLimit = 4;

    static {
        riskyMethodNameContents.add("next");
        riskyMethodNameContents.add("add");
        riskyMethodNameContents.add("create");
        riskyMethodNameContents.add("append");
        riskyMethodNameContents.add("find");
        riskyMethodNameContents.add("put");
        riskyMethodNameContents.add("remove");
        riskyMethodNameContents.add("read");
        riskyMethodNameContents.add("write");
        riskyMethodNameContents.add("push");
        riskyMethodNameContents.add("pop");
        riskyMethodNameContents.add("scan");
        riskyMethodNameContents.add("skip");
        riskyMethodNameContents.add("clone");
        riskyMethodNameContents.add("close");
        riskyMethodNameContents.add("copy");
        riskyMethodNameContents.add("currentTimeMillis");
        riskyMethodNameContents.add("nanoTime");
        riskyMethodNameContents.add("newInstance");
        riskyMethodNameContents.add("noneOf");
        riskyMethodNameContents.add("allOf");
        riskyMethodNameContents.add("random");
        riskyMethodNameContents.add("beep");
        riskyMethodNameContents.add("emptyList");
        riskyMethodNameContents.add("emptySet");
        riskyMethodNameContents.add("emptyMap");

        String userNameProp = System.getProperty(PRMC_RISKY_FIELD_USER_KEY);
        if (userNameProp != null) {
            String[] userNames = userNameProp.split("\\s*,\\s*");
            for (String name : userNames) {
                riskyMethodNameContents.add(name);
            }
        }
        Integer prop = Integer.getInteger(PRMC_HIGH_BYTECOUNT);
        if (prop != null) {
            highByteCountLimit = prop.intValue();
        }
        prop = Integer.getInteger(PRMC_HIGH_METHODCALLS);
        if (prop != null) {
            highMethodCallLimit = prop.intValue();
        }
        prop = Integer.getInteger(PRMC_NORMAL_BYTECOUNT);
        if (prop != null) {
            normalByteCountLimit = prop.intValue();
        }
        prop = Integer.getInteger(PRMC_NORMAL_METHODCALLS);
        if (prop != null) {
            normalMethodCallLimit = prop.intValue();
        }
    }

    private static Set<String> riskyClassNames = new HashSet<>();

    static {
        riskyClassNames.add("java/nio/ByteBuffer");
        riskyClassNames.add("java/io/DataInputStream");
        riskyClassNames.add("java/io/ObjectInputStream");
        riskyClassNames.add("java/util/Calendar");
        riskyClassNames.add("java/util/stream/Collectors");
        riskyClassNames.add("com/google/common/collect/Lists");
        riskyClassNames.add("com/google/common/collect/Sets");
        riskyClassNames.add("com/google/common/collect/Maps");
        riskyClassNames.add("com/google/common/collect/Queues");

        String userNameProp = System.getProperty(PRMC_RISKY_CLASS_USER_KEY);
        if (userNameProp != null) {
            String[] userNames = userNameProp.split("\\s*,\\s*");
            for (String name : userNames) {
                riskyClassNames.add(name);
            }
        }
    }

    private final BugReporter bugReporter;
    private OpcodeStack stack = null;
    private Map<Integer, MethodCall> localMethodCalls = null;
    private Map<FieldInfo, MethodCall> fieldMethodCalls = null;
    private Map<String, MethodCall> staticMethodCalls = null;
    private BitSet branchTargets = null;

    /**
     * constructs a PRMC detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public PossiblyRedundantMethodCalls(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to create and clear the stack, method call maps, and branch targets
     *
     * @param classContext
     *            the context object of the currently visited class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            localMethodCalls = new HashMap<>();
            fieldMethodCalls = new HashMap<>();
            staticMethodCalls = new HashMap<>();
            branchTargets = new BitSet();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            localMethodCalls = null;
            fieldMethodCalls = null;
            staticMethodCalls = null;
            branchTargets = null;
        }
    }

    /**
     * implements the visitor to reset the stack, and method call maps for new method Note: that when collecting branch targets, it's unfortunately not good
     * enough to just collect the handler pcs, as javac plays fast and loose, and will sometimes jam code below the end pc and before the first handler pc,
     * which gets executed. So we need to clear our maps if we go past the end pc as well.
     *
     * @param obj
     *            the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        localMethodCalls.clear();
        fieldMethodCalls.clear();
        staticMethodCalls.clear();
        branchTargets.clear();
        CodeException[] codeExceptions = obj.getExceptionTable();
        for (CodeException codeEx : codeExceptions) {
            // adding the end pc seems silly, but it is need because javac may repeat
            // part of the finally block in the try block, at times.
            branchTargets.set(codeEx.getEndPC());
            branchTargets.set(codeEx.getHandlerPC());
        }
        super.visitCode(obj);
    }

    /**
     * implements the visitor to look for repetitive calls to the same method on the same object using the same constant parameters. These methods must return a
     * value.
     *
     * @param seen
     *            the opcode of the currently parsed instruction
     */
    @Override
    public void sawOpcode(int seen) {
        String userValue = null;

        try {
            stack.precomputation(this);

            int pc = getPC();
            if (branchTargets.get(pc)) {
                localMethodCalls.clear();
                fieldMethodCalls.clear();
                branchTargets.clear(pc);
            }

            if (((seen >= IFEQ) && (seen <= GOTO)) || ((seen >= IFNULL) && (seen <= GOTO_W))) {
                branchTargets.set(getBranchTarget());
            } else if ((seen == TABLESWITCH) || (seen == LOOKUPSWITCH)) {
                int[] offsets = getSwitchOffsets();
                for (int offset : offsets) {
                    branchTargets.set(offset + pc);
                }
            } else if ((seen == ASTORE) || ((seen >= ASTORE_0) && (seen <= ASTORE_3))) {
                localMethodCalls.remove(Integer.valueOf(RegisterUtils.getAStoreReg(this, seen)));
            } else if (seen == PUTFIELD) {
                String fieldSource = "";

                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    fieldSource = (String) item.getUserValue();
                    if (fieldSource == null) {
                        fieldSource = "";
                    }
                }
                fieldMethodCalls.remove(new FieldInfo(fieldSource, getNameConstantOperand()));
            } else if (seen == GETFIELD) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    userValue = (String) item.getUserValue();
                    if (userValue == null) {
                        int reg = item.getRegisterNumber();
                        if (reg >= 0) {
                            userValue = String.valueOf(reg);
                        } else {
                            XField xf = item.getXField();
                            if (xf != null) {
                                userValue = xf.getName();
                            }
                        }
                    }
                }
            } else if ((seen == INVOKEVIRTUAL) || (seen == INVOKEINTERFACE) || (seen == INVOKESTATIC)) {

                String className = getClassConstantOperand();
                String signature = getSigConstantOperand();
                int parmCount = SignatureUtils.getNumParameters(signature);

                int reg = -1;
                XField field = null;
                MethodCall mc = null;
                String fieldSource = null;

                if ((seen != INVOKESTATIC) && (stack.getStackDepth() > parmCount)) {
                    OpcodeStack.Item obj = stack.getStackItem(parmCount);
                    reg = obj.getRegisterNumber();
                    field = obj.getXField();

                    if (reg >= 0) {
                        mc = localMethodCalls.get(Integer.valueOf(reg));
                        MethodInfo mi = Statistics.getStatistics().getMethodStatistics(className, getNameConstantOperand(), signature);
                        if ((mi != null) && mi.getModifiesState()) {
                            clearFieldMethods(String.valueOf(reg));
                            return;
                        }
                    } else if (field != null) {
                        fieldSource = (String) obj.getUserValue();
                        if (fieldSource == null) {
                            fieldSource = "";
                        }
                        mc = fieldMethodCalls.get(new FieldInfo(fieldSource, field.getName()));
                        MethodInfo mi = Statistics.getStatistics().getMethodStatistics(className, getNameConstantOperand(), signature);
                        if ((mi != null) && mi.getModifiesState()) {
                            clearFieldMethods(fieldSource);
                            return;
                        }
                    }
                }

                int neededStackSize = parmCount + ((seen == INVOKESTATIC) ? 0 : 1);
                if (stack.getStackDepth() >= neededStackSize) {
                    Object[] parmConstants = new Object[parmCount];
                    for (int i = 0; i < parmCount; i++) {
                        OpcodeStack.Item parm = stack.getStackItem(i);
                        parmConstants[i] = parm.getConstant();
                        if (parm.getSignature().charAt(0) == '[') {
                            if (!Values.ZERO.equals(parm.getConstant())) {
                                return;
                            }
                            XField f = parm.getXField();
                            if (f != null) {
                                // Two different fields holding a 0 length array should be considered different
                                parmConstants[i] = f.getName() + ':' + parmConstants[i];
                            }
                        }

                        if (parmConstants[i] == null) {
                            return;
                        }
                    }

                    if (seen == INVOKESTATIC) {
                        mc = staticMethodCalls.get(className);
                    } else if ((reg < 0) && (field == null)) {
                        return;
                    }

                    String methodName = getNameConstantOperand();
                    if (mc != null) {
                        if (!signature.endsWith("V") && methodName.equals(mc.getName()) && signature.equals(mc.getSignature())
                                && !isRiskyName(className, methodName)) {
                            Object[] parms = mc.getParms();
                            if (Arrays.equals(parms, parmConstants)) {
                                int ln = getLineNumber(pc);

                                if ((ln != mc.getLineNumber()) || (Math.abs(pc - mc.getPC()) < 10)) {
                                    Statistics statistics = Statistics.getStatistics();
                                    MethodInfo mi = statistics.getMethodStatistics(getClassConstantOperand(), methodName, signature);

                                    bugReporter.reportBug(
                                            new BugInstance(this, BugType.PRMC_POSSIBLY_REDUNDANT_METHOD_CALLS.name(), getBugPriority(methodName, mi))
                                                    .addClass(this).addMethod(this).addSourceLine(this).addString(methodName + signature));
                                }
                            }
                        }

                        if (seen == INVOKESTATIC) {
                            staticMethodCalls.remove(className);
                        } else {
                            if (reg >= 0) {
                                localMethodCalls.remove(Integer.valueOf(reg));
                            } else if (fieldSource != null) {
                                fieldMethodCalls.remove(new FieldInfo(fieldSource, field.getName()));
                            }
                        }
                    } else {
                        int ln = getLineNumber(pc);
                        if (seen == INVOKESTATIC) {
                            staticMethodCalls.put(className, new MethodCall(methodName, signature, parmConstants, pc, ln));
                        } else {
                            if (reg >= 0) {
                                localMethodCalls.put(Integer.valueOf(reg), new MethodCall(methodName, signature, parmConstants, pc, ln));
                            } else if (field != null) {
                                OpcodeStack.Item obj = stack.getStackItem(parmCount);
                                fieldSource = (String) obj.getUserValue();
                                if (fieldSource == null) {
                                    fieldSource = "";
                                }
                                fieldMethodCalls.put(new FieldInfo(fieldSource, field.getName()), new MethodCall(methodName, signature, parmConstants, pc, ln));
                            }
                        }
                    }
                }
            }
        } finally {
            stack.sawOpcode(this, seen);
            if ((userValue != null) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item item = stack.getStackItem(0);
                item.setUserValue(userValue);
            }
        }
    }

    private void clearFieldMethods(String fieldSource) {
        Iterator<FieldInfo> it = fieldMethodCalls.keySet().iterator();
        while (it.hasNext()) {
            if (it.next().hasFieldSource(fieldSource)) {
                it.remove();
            }
        }
    }

    /**
     * returns the bug priority based on metrics about the method
     *
     * @param methodName
     *            TODO
     * @param mi
     *            metrics about the method
     * @return the bug priority
     */
    private static int getBugPriority(String methodName, MethodInfo mi) {
        if ((mi.getNumBytes() >= highByteCountLimit) || (mi.getNumMethodCalls() >= highMethodCallLimit)) {
            return HIGH_PRIORITY;
        }

        if (Values.STATIC_INITIALIZER.equals(methodName)) {
            return LOW_PRIORITY;
        }

        if ((mi.getNumBytes() >= normalByteCountLimit) || (mi.getNumMethodCalls() >= normalMethodCallLimit)) {
            return NORMAL_PRIORITY;
        }

        if ((mi.getNumBytes() == 0) || (mi.getNumMethodCalls() == 0)) {
            return LOW_PRIORITY;
        }

        return EXP_PRIORITY;
    }

    /**
     * returns true if the class or method name contains a pattern that is considered likely to be this modifying
     *
     * @param className
     *            the class name to check
     * @param methodName
     *            the method name to check
     * @return whether the method sounds like it modifies this
     */
    private static boolean isRiskyName(String className, String methodName) {
        if (riskyClassNames.contains(className)) {
            return true;
        }

        String qualifiedMethodName = className + '.' + methodName;
        if (riskyMethodNameContents.contains(qualifiedMethodName)) {
            return true;
        }

        for (String riskyName : riskyMethodNameContents) {
            if (methodName.indexOf(riskyName) >= 0) {
                return true;
            }
        }

        return methodName.contains("$");
    }

    /**
     * returns the source line number for the pc, or just the pc if the line number table doesn't exist
     *
     * @param pc
     *            current pc
     * @return the line number
     */
    private int getLineNumber(int pc) {
        LineNumberTable lnt = getMethod().getLineNumberTable();
        if (lnt == null) {
            return pc;
        }

        LineNumber[] lns = lnt.getLineNumberTable();
        if (lns == null) {
            return pc;
        }

        if (pc > lns[lns.length - 1].getStartPC()) {
            return lns[lns.length - 1].getLineNumber();
        }

        int lo = 0;
        int hi = lns.length - 2;
        int mid = 0;
        while (lo <= hi) {
            mid = (lo + hi) >>> 1;
            if (pc < lns[mid].getStartPC()) {
                hi = mid - 1;
            } else if (pc >= lns[mid + 1].getStartPC()) {
                lo = mid + 1;
            } else {
                break;
            }
        }

        return lns[mid].getLineNumber();
    }

    /**
     * contains information about a field access
     */
    static class FieldInfo {
        private final String fieldSource;
        private final String fieldName;

        public FieldInfo(String source, String name) {
            fieldSource = source;
            fieldName = name;
        }

        public boolean hasFieldSource(String source) {
            return fieldSource.equals(source);
        }

        @Override
        public int hashCode() {
            return fieldSource.hashCode() ^ fieldName.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof FieldInfo)) {
                return false;
            }

            FieldInfo that = (FieldInfo) o;
            return fieldSource.equals(that.fieldSource) && fieldName.equals(that.fieldName);
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }

    /**
     * contains information about a method call
     */
    static class MethodCall {
        private final String methodName;
        private final String methodSignature;
        private final Object[] methodParms;
        private final int methodPC;
        private final int methodLineNumber;

        public MethodCall(String name, String signature, Object[] parms, int pc, int lineNumber) {
            methodName = name;
            methodSignature = signature;
            methodParms = parms;
            methodPC = pc;
            methodLineNumber = lineNumber;
        }

        public String getName() {
            return methodName;
        }

        public String getSignature() {
            return methodSignature;
        }

        public Object[] getParms() {
            return methodParms;
        }

        public int getPC() {
            return methodPC;
        }

        public int getLineNumber() {
            return methodLineNumber;
        }
    }
}
