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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.collect.MethodInfo;
import com.mebigfatguy.fbcontrib.collect.Statistics;
import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.StopOpcodeParsingException;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XMethod;
import edu.umd.cs.findbugs.internalAnnotations.SlashedClassName;

/**
 * looks for common problems with the application of annotations
 */
@CustomUserValue
public class AnnotationIssues extends BytecodeScanningDetector {

    private static final String USER_NULLABLE_ANNOTATIONS = "fb-contrib.ai.annotations";

    private static final Set<String> NULLABLE_ANNOTATIONS = new HashSet<>();

    static {
        NULLABLE_ANNOTATIONS.add("Lorg/jetbrains/annotations/Nullable;");
        NULLABLE_ANNOTATIONS.add("Ljavax/annotation/Nullable;");
        NULLABLE_ANNOTATIONS.add("Ljavax/annotation/CheckForNull;");
        NULLABLE_ANNOTATIONS.add("Lcom/sun/istack/Nullable;");
        NULLABLE_ANNOTATIONS.add("Ledu/umd/cs/findbugs/annotations/Nullable;");
        NULLABLE_ANNOTATIONS.add("Lorg/springframework/lang/Nullable;");
        NULLABLE_ANNOTATIONS.add("Landroid/support/annotations/Nullable");

        String userAnnotations = System.getProperty(USER_NULLABLE_ANNOTATIONS);
        if ((userAnnotations != null) && !userAnnotations.isEmpty()) {
            String[] annotations = userAnnotations.split(Values.WHITESPACE_COMMA_SPLIT);
            for (String annotation : annotations) {
                NULLABLE_ANNOTATIONS.add("L" + annotation.replace('.', '/') + ";");
            }
        }
    }

    private static final Set<FQMethod> NOTABLE_EXCEPTIONS = UnmodifiableSet.create(
    // @formatter:off
            new FQMethod(Values.SLASHED_JAVA_LANG_CLASS, "newInstance", SignatureBuilder.SIG_VOID_TO_OBJECT)
    // @formatter:on
    );

    public enum NULLABLE {
        TRUE
    };

    private BugReporter bugReporter;
    private Map<Integer, Integer> assumedNullTill;
    private Map<Integer, Integer> assumedNonNullTill;
    private Set<Integer> noAssumptionsPossible;
    private List<Integer> branchTargets;
    private OpcodeStack stack;
    private boolean methodIsNullable;

    /**
     * constructs a AI detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public AnnotationIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    public boolean isCollecting() {
        return false;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            JavaClass cls = classContext.getJavaClass();
            if (cls.getMajor() >= Constants.MAJOR_1_5) {
                if (isCollecting() || !cls.isAnonymous()) {
                    stack = new OpcodeStack();
                    assumedNullTill = new HashMap<>();
                    assumedNonNullTill = new HashMap<>();
                    noAssumptionsPossible = new HashSet<>();
                    branchTargets = new ArrayList();
                    super.visitClassContext(classContext);
                }
            }
        } finally {
            stack = null;
            assumedNullTill = null;
            assumedNonNullTill = null;
            noAssumptionsPossible = null;
            branchTargets = null;
        }
    }

    @Override
    public void visitCode(Code obj) {

        Method method = getMethod();
        String sig = method.getSignature();
        String returnType = sig.substring(sig.indexOf(')') + 1);
        char returnTypeChar = returnType.charAt(0);
        if ((returnTypeChar != 'L') && (returnTypeChar != '[')) {
            return;
        }

        if (method.isSynthetic() && !isCollecting()) {
            return;
        }

        if (Values.SIG_JAVA_LANG_VOID.equals(returnType)) {
            return;
        }

        if (NOTABLE_EXCEPTIONS.contains(new FQMethod(getClassName(), method.getName(), sig))) {
            MethodInfo methodInfo = Statistics.getStatistics().getMethodStatistics(getClassName(), method.getName(), method.getSignature());
            methodInfo.setCanReturnNull(false);
            return;
        }

        if (methodHasNullableAnnotation(method)) {
            if (isCollecting()) {
                MethodInfo methodInfo = Statistics.getStatistics().getMethodStatistics(getClassName(), method.getName(), method.getSignature());
                methodInfo.setCanReturnNull(true);
            }
            return;
        }

        MethodInfo methodInfo = Statistics.getStatistics().getMethodStatistics(getClassName(), method.getName(), method.getSignature());
        if (!isCollecting() && methodInfo.getCanReturnNull()) {
            bugReporter.reportBug(new BugInstance(this, BugType.AI_ANNOTATION_ISSUES_NEEDS_NULLABLE.name(), LOW_PRIORITY).addClass(this).addMethod(this));
        } else {

            methodIsNullable = false;
            stack.resetForMethodEntry(this);
            assumedNullTill.clear();
            assumedNonNullTill.clear();
            noAssumptionsPossible.clear();
            branchTargets.clear();

            try {
                super.visitCode(obj);
            } catch (StopOpcodeParsingException e) {
            }

            if (methodIsNullable) {
                if (isCollecting()) {
                    methodInfo.setCanReturnNull(true);
                } else {
                    bugReporter
                            .reportBug(new BugInstance(this, BugType.AI_ANNOTATION_ISSUES_NEEDS_NULLABLE.name(), LOW_PRIORITY).addClass(this).addMethod(this));
                }
            }
        }
    }

    @Override
    public void sawOpcode(int seen) {
        boolean resultIsNullable = false;

        if (OpcodeUtils.isBranch(seen) && (getBranchOffset() > 0)) {
            branchTargets.add(getBranchTarget());
            Collections.sort(branchTargets);
        }

        clearBranchTargets(getPC());
        convertNullToNonNull(getPC());
        clearAssumptions(assumedNullTill, getPC());
        clearAssumptions(assumedNonNullTill, getPC());

        if (OpcodeUtils.isBranch(seen) && (getBranchOffset() > 0)) {
            branchTargets.add(getBranchTarget());
            Collections.sort(branchTargets);
        }

        try {
            switch (seen) {
                case ARETURN: {
                    if (!methodIsNullable && (stack.getStackDepth() > 0)) {
                        OpcodeStack.Item itm = stack.getStackItem(0);
                        Integer reg = Integer.valueOf(itm.getRegisterNumber());
                        methodIsNullable = !assumedNonNullTill.containsKey(reg) && (!noAssumptionsPossible.contains(reg)
                                && ((assumedNullTill.containsKey(reg)) || isStackElementNullable(getClassName(), getMethod(), itm)));
                        if (methodIsNullable) {
                            throw new StopOpcodeParsingException();
                        }
                    }
                    break;
                }

                case IFNONNULL:
                    if (getBranchOffset() > 0) {
                        if (stack.getStackDepth() > 0) {
                            OpcodeStack.Item itm = stack.getStackItem(0);
                            int reg = itm.getRegisterNumber();
                            if (reg >= 0) {
                                assumedNullTill.put(reg, getBranchTarget());
                            }
                        }
                    }
                break;

                case IFNULL:
                    if (getBranchOffset() > 0) {
                        if (stack.getStackDepth() > 0) {
                            OpcodeStack.Item itm = stack.getStackItem(0);
                            int reg = itm.getRegisterNumber();
                            if (reg >= 0) {
                                assumedNonNullTill.put(reg, getBranchTarget());
                            }
                        }
                    }
                break;

                case INVOKESTATIC:
                case INVOKEINTERFACE:
                case INVOKEVIRTUAL: {
                    resultIsNullable = (isMethodNullable(getClassConstantOperand(), getNameConstantOperand(), getSigConstantOperand()));
                    break;
                }

                case ATHROW: {
                    removeAssumptions(assumedNonNullTill);
                    removeAssumptions(assumedNullTill);
                    break;
                }

            }
        } finally {
            stack.sawOpcode(this, seen);
            if ((resultIsNullable) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item itm = stack.getStackItem(0);
                itm.setUserValue(NULLABLE.TRUE);
            }
        }
    }

    public static boolean methodHasNullableAnnotation(Method m) {
        for (AnnotationEntry entry : m.getAnnotationEntries()) {
            String annotationType = entry.getAnnotationType();
            if (NULLABLE_ANNOTATIONS.contains(annotationType)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isStackElementNullable(String className, Method method, OpcodeStack.Item itm) {
        if (itm.isNull() || (itm.getUserValue() instanceof NULLABLE)) {
            MethodInfo mi = Statistics.getStatistics().getMethodStatistics(className, method.getName(), method.getSignature());
            if (mi != null) {
                mi.setCanReturnNull(true);
            }
            return true;
        } else {
            XMethod xm = itm.getReturnValueOf();
            if (xm != null) {
                MethodInfo mi = Statistics.getStatistics().getMethodStatistics(xm.getClassName().replace('.', '/'), xm.getName(), xm.getSignature());
                if ((mi != null) && mi.getCanReturnNull()) {
                    mi = Statistics.getStatistics().getMethodStatistics(className, method.getName(), method.getSignature());
                    if (mi != null) {
                        mi.setCanReturnNull(true);
                    }
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean isMethodNullable(@SlashedClassName String className, String methodName, String methodSignature) {
        char returnTypeChar = methodSignature.charAt(methodSignature.indexOf(')') + 1);
        if ((returnTypeChar != 'L') && (returnTypeChar != '[')) {
            return false;
        }
        MethodInfo mi = Statistics.getStatistics().getMethodStatistics(className, methodName, methodSignature);
        return ((mi != null) && mi.getCanReturnNull());

        // can we check if it has @Nullable on it? hmm need to convert to Method
    }

    /**
     * the map is keyed by register, and value by when an assumption holds to a byte offset if we have passed when the assumption holds, clear the item from the
     * map
     *
     * @param assumptionTill
     *            the map of assumptions
     * @param pc
     *            // * the current pc
     */
    public static void clearAssumptions(Map<Integer, Integer> assumptionTill, int pc) {
        Iterator<Integer> it = assumptionTill.values().iterator();
        while (it.hasNext()) {
            if (it.next().intValue() <= pc) {
                it.remove();
            }
        }
    }

    public void convertNullToNonNull(int pc) {
        for (Map.Entry<Integer, Integer> entry : assumedNullTill.entrySet()) {
            if (entry.getValue().intValue() == pc) {
                int lastOp = getPrevOpcode(1);
                if ((lastOp == ARETURN) || (lastOp == ATHROW)) {
                    int nonNullTill = getNextBranchTarget();
                    assumedNonNullTill.put(entry.getKey(), nonNullTill);
                }
            }
        }
    }

    /**
     * remove branch targets that have been passed
     *
     * @param pc
     *            the current pc
     */
    public void clearBranchTargets(int pc) {
        Iterator<Integer> it = branchTargets.iterator();
        while (it.hasNext()) {
            int target = it.next().intValue();
            if (target <= pc) {
                it.remove();
            }
        }
    }

    public int getNextBranchTarget() {
        if (branchTargets.isEmpty()) {
            return Integer.MAX_VALUE;
        }

        return branchTargets.get(0);
    }

    public void removeAssumptions(Map<Integer, Integer> assumptionsTill) {
        noAssumptionsPossible.addAll(assumptionsTill.keySet());
    }
}
