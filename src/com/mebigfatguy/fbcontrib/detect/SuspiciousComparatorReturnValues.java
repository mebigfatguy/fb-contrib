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
import java.util.Map;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for class that implement Comparator or Comparable, and whose compare or compareTo methods return constant values only, but that don't represent the
 * three possible choice (a negative number, 0, and a positive number).
 */
@CustomUserValue
public class SuspiciousComparatorReturnValues extends BytecodeScanningDetector {
    private static Map<JavaClass, MethodInfo> compareClasses = new HashMap<JavaClass, MethodInfo>();

    static {
        try {
            compareClasses.put(Repository.lookupClass("java/lang/Comparable"), new MethodInfo("compareTo", 1, "I"));
            compareClasses.put(Repository.lookupClass("java/util/Comparator"), new MethodInfo("compare", 2, "I"));
        } catch (ClassNotFoundException cnfe) {
            // don't have a bugReporter yet, so do nothing
        }
    }

    private OpcodeStack stack;
    private final BugReporter bugReporter;
    private MethodInfo methodInfo;
    private boolean indeterminate;
    private boolean seenNegative;
    private boolean seenPositive;
    private boolean seenZero;
    private boolean seenUnconditionalNonZero;
    private int furthestBranchTarget;
    private Integer sawConstant;

    /**
     * constructs a SCRV detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public SuspiciousComparatorReturnValues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to actually iterate twice over this class, once for compareTo and once for compare.
     *
     * @param classContext
     *            the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            JavaClass cls = classContext.getJavaClass();
            for (Map.Entry<JavaClass, MethodInfo> entry : compareClasses.entrySet()) {
                if (cls.implementationOf(entry.getKey())) {
                    methodInfo = entry.getValue();
                    stack = new OpcodeStack();
                    super.visitClassContext(classContext);
                    break;
                }
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        } finally {
            methodInfo = null;
            stack = null;
        }
    }

    /**
     * implements the visitor to check to see what constants were returned from a comparator. If no constants were returned it can't determine anything, however
     * if only constants were returned, it looks to see if negative positive and zero was returned. It also looks to see if a non zero value is returned
     * unconditionally. While it is possible that later check is ok, it usually means something is wrong.
     *
     * @param obj
     *            the currently parsed code block
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "FCBL_FIELD_COULD_BE_LOCAL", justification = "False positives occur when state is maintained across callbacks")
    @Override
    public void visitCode(Code obj) {
        if (getMethod().isSynthetic()) {
            return;
        }

        String methodName = getMethodName();
        String methodSig = getMethodSig();
        if (methodName.equals(methodInfo.methodName) && methodSig.endsWith(methodInfo.signatureEnding)
                && (Type.getArgumentTypes(methodSig).length == methodInfo.argumentCount)) {
            stack.resetForMethodEntry(this);
            indeterminate = false;
            seenNegative = false;
            seenPositive = false;
            seenZero = false;
            seenUnconditionalNonZero = false;
            furthestBranchTarget = -1;
            sawConstant = null;
            super.visitCode(obj);
            if (!indeterminate && (!seenZero || seenUnconditionalNonZero || (obj.getCode().length > 2))) {
                boolean seenAll = seenNegative & seenPositive & seenZero;
                if (!seenAll || seenUnconditionalNonZero) {
                    bugReporter.reportBug(
                            new BugInstance(this, BugType.SCRV_SUSPICIOUS_COMPARATOR_RETURN_VALUES.name(), (!seenAll) ? NORMAL_PRIORITY : LOW_PRIORITY)
                                    .addClass(this).addMethod(this).addSourceLine(this, 0));
                }
            }
        }
    }

    /**
     * implements the visitor to look for returns of constant values, and records them for being negative, zero or positive. It also records unconditional
     * returns of non zero values
     *
     * @param seen
     *            the currently parsed opcode
     */
    @Override
    public void sawOpcode(int seen) {
        try {
            if (indeterminate) {
                return;
            }

            stack.precomputation(this);

            switch (seen) {
                case IRETURN: {
                    processIntegerReturn();
                }
                break;

                case GOTO:
                case GOTO_W: {
                    if (stack.getStackDepth() > 0) {
                        indeterminate = true;
                    }
                    if (furthestBranchTarget < getBranchTarget()) {
                        furthestBranchTarget = getBranchTarget();
                    }
                }
                break;

                case IFEQ:
                case IFNE:
                case IFLT:
                case IFGE:
                case IFGT:
                case IFLE:
                case IF_ICMPEQ:
                case IF_ICMPNE:
                case IF_ICMPLT:
                case IF_ICMPGE:
                case IF_ICMPGT:
                case IF_ICMPLE:
                case IF_ACMPEQ:
                case IF_ACMPNE:
                case IFNULL:
                case IFNONNULL: {
                    if (furthestBranchTarget < getBranchTarget()) {
                        furthestBranchTarget = getBranchTarget();
                    }
                }
                break;

                case LOOKUPSWITCH:
                case TABLESWITCH: {
                    int defTarget = getDefaultSwitchOffset() + getPC();
                    if (furthestBranchTarget > defTarget) {
                        furthestBranchTarget = defTarget;
                    }
                }
                break;

                case ATHROW: {
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item item = stack.getStackItem(0);
                        String exSig = item.getSignature();
                        if ("Ljava/lang/UnsupportedOperationException;".equals(exSig)) {
                            indeterminate = true;
                        }
                    }
                }
                break;

                /*
                 * these three opcodes are here because findbugs proper is broken, it sometimes doesn't push this constant on the stack, because of bad branch
                 * handling
                 */
                case ICONST_0:
                    if (getNextOpcode() == IRETURN) {
                        sawConstant = Integer.valueOf(0);
                    }
                break;

                case ICONST_M1:
                    if (getNextOpcode() == IRETURN) {
                        sawConstant = Integer.valueOf(-1);
                    }
                break;

                case ICONST_1:
                    if (getNextOpcode() == IRETURN) {
                        sawConstant = Integer.valueOf(1);
                    }
                break;

                default:
                break;

            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    /**
     * processes an IRETURN looking for constants and categorizes them as negative, zero or positive. it also records a unconditional return of a non zero value
     *
     */
    private void processIntegerReturn() {
        if ((sawConstant != null) || (stack.getStackDepth() > 0)) {
            Integer returnValue = null;
            if (sawConstant != null) {
                returnValue = sawConstant;
            } else {
                OpcodeStack.Item item = stack.getStackItem(0);
                returnValue = (Integer) item.getConstant();
            }

            if (returnValue == null) {
                indeterminate = true;
            } else {
                int v = returnValue.intValue();
                if (v < 0) {
                    seenNegative = true;
                    if (getPC() > furthestBranchTarget) {
                        seenUnconditionalNonZero = true;
                    }
                } else if (v > 0) {
                    seenPositive = true;
                    if (getPC() > furthestBranchTarget) {
                        seenUnconditionalNonZero = true;
                    }
                } else {
                    seenZero = true;
                }
            }
        } else {
            indeterminate = true;
        }

        sawConstant = null;
    }
}

/**
 * a simple data class that holds information about a method call
 */
class MethodInfo {
    final String methodName;
    final int argumentCount;
    final String signatureEnding;

    /**
     * simple constructor for initializing the data
     *
     * @param methodName
     *            the name of the method
     * @param argumentCount
     *            the number of parameters
     * @param signatureEnding
     *            the return value signature type
     */
    MethodInfo(String methodName, int argumentCount, String signatureEnding) {
        this.methodName = methodName;
        this.argumentCount = argumentCount;
        this.signatureEnding = signatureEnding;
    }
}
