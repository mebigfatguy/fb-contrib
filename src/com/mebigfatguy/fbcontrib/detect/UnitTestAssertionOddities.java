/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2016 Dave Brosius
 * Copyright (C) 2016 - Juan Martin Sotuyo Dodero
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

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.ElementValuePair;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XField;
import edu.umd.cs.findbugs.classfile.FieldDescriptor;
import edu.umd.cs.findbugs.classfile.analysis.AnnotationValue;

/** looks for odd uses of the Assert class of the JUnit and TestNG framework */
@CustomUserValue
public class UnitTestAssertionOddities extends BytecodeScanningDetector {
    private enum State {
        SAW_NOTHING, SAW_IF_ICMPNE, SAW_IF_NE, SAW_IF_ICMPEQ, SAW_ICONST_1, SAW_GOTO, SAW_ICONST_0, SAW_EQUALS
    }

    private enum TestFrameworkType {
        UNKNOWN, JUNIT, TESTNG;
    }

    private static final Set<String> INJECTOR_ANNOTATIONS = UnmodifiableSet.create(
        // @formatter:off
        "org.mockito.Mock",
        "org.springframework.beans.factory.annotation.Autowired"
        // @formatter:on
    );

    private static final String BOOLEAN_TYPE_SIGNATURE = "Ljava/lang/Boolean;";
    private static final String LJAVA_LANG_DOUBLE = "Ljava/lang/Double;";

    private static final String TESTCASE_CLASS = "junit.framework.TestCase";
    private static final String TEST_CLASS = "org.junit.Test";
    private static final String TEST_ANNOTATION_SIGNATURE = "Lorg/junit/Test;";
    private static final String OLD_ASSERT_CLASS = "junit/framework/Assert";
    private static final String NEW_ASSERT_CLASS = "org/junit/Assert";

    private static final String TESTNG_CLASS = "org.testng.annotations.Test";
    private static final String TESTNG_ANNOTATION_SIGNATURE = "Lorg/testng/annotations/Test;";
    private static final String NG_ASSERT_CLASS = "org/testng/Assert";
    private static final String NG_JUNIT_ASSERT_CLASS = "org/testng/AssertJUnit";

    private BugReporter bugReporter;
    private JavaClass testCaseClass;
    private JavaClass testAnnotationClass;
    private JavaClass testNGAnnotationClass;
    private OpcodeStack stack;
    private boolean isTestCaseDerived;
    private boolean isAnnotationCapable;
    private String clsName;
    private boolean sawAssert;
    private State state;
    private boolean checkIsNegated;
    private TestFrameworkType frameworkType;
    private boolean hasAnnotation;
    private Set<FieldDescriptor> fieldsWithAnnotations;

    /**
     * constructs a JOA detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public UnitTestAssertionOddities(BugReporter bugReporter) {
        this.bugReporter = bugReporter;

        try {
            testCaseClass = Repository.lookupClass(TESTCASE_CLASS);
        } catch (ClassNotFoundException cnfe) {
            testCaseClass = null;
        }
        try {
            testAnnotationClass = Repository.lookupClass(TEST_CLASS);
        } catch (ClassNotFoundException cnfe) {
            testAnnotationClass = null;
        }

        try {
            testNGAnnotationClass = Repository.lookupClass(TESTNG_CLASS);
        } catch (ClassNotFoundException cnfe) {
            testNGAnnotationClass = null;
        }
    }

    /**
     * override the visitor to see if this class could be a test class
     *
     * @param classContext
     *            the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            JavaClass cls = classContext.getJavaClass();
            clsName = cls.getClassName().replace('.', '/');
            isTestCaseDerived = (testCaseClass != null) && cls.instanceOf(testCaseClass);
            isAnnotationCapable = (cls.getMajor() >= 5) && ((testAnnotationClass != null) || (testNGAnnotationClass != null));
            if (isTestCaseDerived || isAnnotationCapable) {
                stack = new OpcodeStack();
                fieldsWithAnnotations = new HashSet<FieldDescriptor>();
                super.visitClassContext(classContext);
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        } finally {
            stack = null;
            fieldsWithAnnotations = null;
        }
    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "FCBL_FIELD_COULD_BE_LOCAL", justification = "False positives occur when state is maintained across callbacks")
    @Override
    public void visitCode(Code obj) {
        Method m = getMethod();
        frameworkType = isTestCaseDerived && m.getName().startsWith("test") ? TestFrameworkType.JUNIT : TestFrameworkType.UNKNOWN;
        hasAnnotation = false;

        if ((frameworkType == TestFrameworkType.UNKNOWN) && isAnnotationCapable) {
            AnnotationEntry[] annotations = m.getAnnotationEntries();
            if (annotations != null) {
                for (AnnotationEntry annotation : annotations) {
                    String annotationType = annotation.getAnnotationType();
                    if (annotation.isRuntimeVisible()) {
                        if (TEST_ANNOTATION_SIGNATURE.equals(annotationType)) {
                            frameworkType = TestFrameworkType.JUNIT;
                            hasAnnotation = true;
                            break;
                        } else if (TESTNG_ANNOTATION_SIGNATURE.equals(annotationType)) {
                            frameworkType = TestFrameworkType.TESTNG;
                            hasAnnotation = true;
                            break;
                        }
                    }
                }
            }
        }

        if (frameworkType != TestFrameworkType.UNKNOWN) {
            stack.resetForMethodEntry(this);
            state = State.SAW_NOTHING;
            sawAssert = false;
            super.visitCode(obj);

            if (!sawAssert && !hasExpects()) {
                bugReporter.reportBug(new BugInstance(this, frameworkType == TestFrameworkType.JUNIT ? BugType.UTAO_JUNIT_ASSERTION_ODDITIES_NO_ASSERT.name()
                        : BugType.UTAO_TESTNG_ASSERTION_ODDITIES_NO_ASSERT.name(), LOW_PRIORITY).addClass(this).addMethod(this));
            }
        }
    }

    @Override
    public void visitField(Field obj) {
        if (obj.getAnnotationEntries().length > 0) {
            fieldsWithAnnotations.add(getFieldDescriptor());
        }
    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "CLI_CONSTANT_LIST_INDEX", justification = "Constrained by FindBugs API")
    @Override
    public void sawOpcode(int seen) {
        String userValue = null;

        try {
            stack.precomputation(this);

            if (seen == INVOKESTATIC) {
                String clsName = getClassConstantOperand();
                if (OLD_ASSERT_CLASS.equals(clsName) || NEW_ASSERT_CLASS.equals(clsName) || NG_JUNIT_ASSERT_CLASS.equals(clsName)) {

                    sawAssert = true;

                    if (hasAnnotation && (frameworkType == TestFrameworkType.JUNIT) && OLD_ASSERT_CLASS.equals(clsName)) {
                        bugReporter.reportBug(new BugInstance(this, BugType.UTAO_JUNIT_ASSERTION_ODDITIES_USING_DEPRECATED.name(), NORMAL_PRIORITY)
                                .addClass(this).addMethod(this).addSourceLine(this));
                    }

                    String methodName = getNameConstantOperand();
                    if ("assertEquals".equals(methodName)) {
                        String signature = getSigConstantOperand();
                        Type[] argTypes = Type.getArgumentTypes(signature);
                        if (((argTypes.length == 2) || (argTypes.length == 3)) && (stack.getStackDepth() >= 2)) {
                            OpcodeStack.Item item0 = stack.getStackItem(0);
                            OpcodeStack.Item expectedItem = stack.getStackItem(1);
                            Object cons1 = expectedItem.getConstant();
                            if ((cons1 != null) && BOOLEAN_TYPE_SIGNATURE.equals(expectedItem.getSignature())
                                    && BOOLEAN_TYPE_SIGNATURE.equals(item0.getSignature())) {
                                bugReporter.reportBug(new BugInstance(this, BugType.UTAO_JUNIT_ASSERTION_ODDITIES_BOOLEAN_ASSERT.name(), NORMAL_PRIORITY)
                                        .addClass(this).addMethod(this).addSourceLine(this));
                                return;
                            }
                            if ((cons1 == null) && (item0.getConstant() != null) && ((argTypes.length == 2) || !isFloatingPtPrimitive(item0.getSignature()))) {
                                bugReporter.reportBug(new BugInstance(this, BugType.UTAO_JUNIT_ASSERTION_ODDITIES_ACTUAL_CONSTANT.name(), NORMAL_PRIORITY)
                                        .addClass(this).addMethod(this).addSourceLine(this));
                                return;
                            }
                            if (expectedItem.isNull() && !hasFieldInjectorAnnotation(expectedItem)) {
                                bugReporter.reportBug(new BugInstance(this, BugType.UTAO_JUNIT_ASSERTION_ODDITIES_USE_ASSERT_NULL.name(), NORMAL_PRIORITY)
                                        .addClass(this).addMethod(this).addSourceLine(this));
                                return;
                            }
                            if (argTypes[argTypes.length - 1].equals(Type.DOUBLE) && argTypes[argTypes.length - 2].equals(Type.DOUBLE)
                                    && ((argTypes.length < 3) || !argTypes[argTypes.length - 3].equals(Type.DOUBLE))) {
                                bugReporter.reportBug(new BugInstance(this, BugType.UTAO_JUNIT_ASSERTION_ODDITIES_INEXACT_DOUBLE.name(), NORMAL_PRIORITY)
                                        .addClass(this).addMethod(this).addSourceLine(this));
                                return;
                            }
                        }
                    } else if ("assertNotEquals".equals(methodName)) {
                        String signature = getSigConstantOperand();
                        Type[] argTypes = Type.getArgumentTypes(signature);
                        if (((argTypes.length == 2) || (argTypes.length == 3)) && (stack.getStackDepth() >= 2)) {
                            OpcodeStack.Item expectedItem = stack.getStackItem(1);
                            if (expectedItem.isNull()) {
                                bugReporter.reportBug(new BugInstance(this, BugType.UTAO_JUNIT_ASSERTION_ODDITIES_USE_ASSERT_NOT_NULL.name(), NORMAL_PRIORITY)
                                        .addClass(this).addMethod(this).addSourceLine(this));
                                return;
                            }
                        }
                    } else if ("assertNotNull".equals(methodName)) {
                        if ((stack.getStackDepth() > 0) && "valueOf".equals(stack.getStackItem(0).getUserValue())) {
                            bugReporter.reportBug(new BugInstance(this, BugType.UTAO_JUNIT_ASSERTION_ODDITIES_IMPOSSIBLE_NULL.name(), NORMAL_PRIORITY)
                                    .addClass(this).addMethod(this).addSourceLine(this));
                        }
                    } else if ((!checkIsNegated && "assertTrue".equals(methodName)) || (checkIsNegated && "assertFalse".equals(methodName))) {
                        if ((state == State.SAW_ICONST_0) || (state == State.SAW_EQUALS)) {
                            bugReporter.reportBug(new BugInstance(this, BugType.UTAO_JUNIT_ASSERTION_ODDITIES_USE_ASSERT_EQUALS.name(), NORMAL_PRIORITY)
                                    .addClass(this).addMethod(this).addSourceLine(this));
                        }
                    } else if (((!checkIsNegated && "assertFalse".equals(methodName)) || (checkIsNegated && "assertTrue".equals(methodName)))
                            && ((state == State.SAW_ICONST_0) || (state == State.SAW_EQUALS))) {
                        bugReporter.reportBug(new BugInstance(this, BugType.UTAO_JUNIT_ASSERTION_ODDITIES_USE_ASSERT_NOT_EQUALS.name(), NORMAL_PRIORITY)
                                .addClass(this).addMethod(this).addSourceLine(this));
                    }
                } else if (NG_ASSERT_CLASS.equals(clsName)) {
                    sawAssert = true;
                    String methodName = getNameConstantOperand();
                    if ("assertEquals".equals(methodName)) {
                        String signature = getSigConstantOperand();
                        Type[] argTypes = Type.getArgumentTypes(signature);
                        if ((argTypes.length == 2) || (argTypes.length == 3)) {

                            OpcodeStack.Item actualItem, expectedItem;
                            if ((argTypes.length == 2) && (stack.getStackDepth() >= 2)) {
                                expectedItem = stack.getStackItem(0);
                                actualItem = stack.getStackItem(1);
                            } else if ((argTypes.length == 3) && (stack.getStackDepth() >= 3)) {
                                expectedItem = stack.getStackItem(1);
                                actualItem = stack.getStackItem(2);
                            } else {
                                return;
                            }

                            Object cons1 = expectedItem.getConstant();
                            if ((cons1 != null) && argTypes[0].equals(Type.BOOLEAN) && argTypes[1].equals(Type.BOOLEAN)) {
                                bugReporter.reportBug(new BugInstance(this, BugType.UTAO_TESTNG_ASSERTION_ODDITIES_BOOLEAN_ASSERT.name(), NORMAL_PRIORITY)
                                        .addClass(this).addMethod(this).addSourceLine(this));
                                return;
                            }
                            if ((actualItem.getConstant() != null) && (expectedItem.getConstant() == null)
                                    && ((argTypes.length == 2) || !isFloatingPtPrimitive(actualItem.getSignature()))) {
                                bugReporter.reportBug(new BugInstance(this, BugType.UTAO_TESTNG_ASSERTION_ODDITIES_ACTUAL_CONSTANT.name(), NORMAL_PRIORITY)
                                        .addClass(this).addMethod(this).addSourceLine(this));
                                return;
                            }
                            if (expectedItem.isNull() && !hasFieldInjectorAnnotation(expectedItem)) {
                                bugReporter.reportBug(new BugInstance(this, BugType.UTAO_TESTNG_ASSERTION_ODDITIES_USE_ASSERT_NULL.name(), NORMAL_PRIORITY)
                                        .addClass(this).addMethod(this).addSourceLine(this));
                                return;
                            }
                            if (Type.OBJECT.equals(argTypes[0]) && Type.OBJECT.equals(argTypes[1]) && LJAVA_LANG_DOUBLE.equals(actualItem.getSignature())
                                    && LJAVA_LANG_DOUBLE.equals(expectedItem.getSignature())) {
                                bugReporter.reportBug(new BugInstance(this, BugType.UTAO_TESTNG_ASSERTION_ODDITIES_INEXACT_DOUBLE.name(), NORMAL_PRIORITY)
                                        .addClass(this).addMethod(this).addSourceLine(this));
                                return;
                            }
                        }
                    } else if ("assertNotEquals".equals(methodName)) {
                        String signature = getSigConstantOperand();
                        Type[] argTypes = Type.getArgumentTypes(signature);
                        OpcodeStack.Item expectedItem;
                        if ((argTypes.length == 2) && (stack.getStackDepth() >= 2)) {
                            expectedItem = stack.getStackItem(0);
                        } else if ((argTypes.length == 3) && (stack.getStackDepth() >= 3)) {
                            expectedItem = stack.getStackItem(1);
                        } else {
                            return;
                        }

                        XField fld = expectedItem.getXField();
                        if (((fld == null) || !fieldsWithAnnotations.contains(fld.getFieldDescriptor())) && (expectedItem.isNull())) {
                            bugReporter.reportBug(new BugInstance(this, BugType.UTAO_TESTNG_ASSERTION_ODDITIES_USE_ASSERT_NOT_NULL.name(), NORMAL_PRIORITY)
                                    .addClass(this).addMethod(this).addSourceLine(this));
                            return;
                        }
                    } else if ("assertNotNull".equals(methodName)) {
                        if ((stack.getStackDepth() > 0) && "valueOf".equals(stack.getStackItem(0).getUserValue())) {
                            bugReporter.reportBug(new BugInstance(this, BugType.UTAO_TESTNG_ASSERTION_ODDITIES_IMPOSSIBLE_NULL.name(), NORMAL_PRIORITY)
                                    .addClass(this).addMethod(this).addSourceLine(this));
                        }
                    } else if ((!checkIsNegated && "assertTrue".equals(methodName)) || (checkIsNegated && "assertFalse".equals(methodName))) {
                        if ((state == State.SAW_ICONST_0) || (state == State.SAW_EQUALS)) {
                            bugReporter.reportBug(new BugInstance(this, BugType.UTAO_TESTNG_ASSERTION_ODDITIES_USE_ASSERT_EQUALS.name(), NORMAL_PRIORITY)
                                    .addClass(this).addMethod(this).addSourceLine(this));
                        }
                    } else if (((!checkIsNegated && "assertFalse".equals(methodName)) || (checkIsNegated && "assertTrue".equals(methodName)))
                            && ((state == State.SAW_ICONST_0) || (state == State.SAW_EQUALS))) {
                        bugReporter.reportBug(new BugInstance(this, BugType.UTAO_TESTNG_ASSERTION_ODDITIES_USE_ASSERT_NOT_EQUALS.name(), NORMAL_PRIORITY)
                                .addClass(this).addMethod(this).addSourceLine(this));
                    }
                } else {
                    String methodName = getNameConstantOperand();
                    String sig = getSigConstantOperand();
                    if (clsName.startsWith("java/lang/") && "valueOf".equals(methodName) && (sig.indexOf(")Ljava/lang/") >= 0)) {
                        userValue = "valueOf";
                    }
                }
            } else if ((seen == ATHROW) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item item = stack.getStackItem(0);
                String throwClass = item.getSignature();
                if ("Ljava/lang/AssertionError;".equals(throwClass)) {
                    bugReporter.reportBug(new BugInstance(this,
                            frameworkType == TestFrameworkType.JUNIT ? BugType.UTAO_JUNIT_ASSERTION_ODDITIES_ASSERT_USED.name()
                                    : BugType.UTAO_TESTNG_ASSERTION_ODDITIES_ASSERT_USED.name(),
                            NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
                    sawAssert = true;
                }
            }

            switch (state) {
                case SAW_NOTHING:
                case SAW_EQUALS:
                    // starting the chain, reset to false
                    checkIsNegated = false;
                    if (seen == IF_ICMPNE) {
                        state = State.SAW_IF_ICMPNE;
                    } else if (seen == IFNE) {
                        state = State.SAW_IF_NE;
                        checkIsNegated = true;
                    } else if (seen == IF_ICMPEQ) {
                        state = State.SAW_IF_ICMPEQ;
                        checkIsNegated = true;
                    } else {
                        state = State.SAW_NOTHING;
                    }
                break;

                case SAW_IF_ICMPEQ:
                case SAW_IF_NE:
                case SAW_IF_ICMPNE:
                    if (seen == ICONST_1) {
                        state = State.SAW_ICONST_1;
                    } else {
                        state = State.SAW_NOTHING;
                    }
                break;

                case SAW_ICONST_1:
                    if (seen == GOTO) {
                        state = State.SAW_GOTO;
                    } else {
                        state = State.SAW_NOTHING;
                    }
                break;

                case SAW_GOTO:
                    if (seen == ICONST_0) {
                        state = State.SAW_ICONST_0;
                    } else {
                        state = State.SAW_NOTHING;
                    }
                break;

                default:
                    state = State.SAW_NOTHING;
                break;
            }

            if ((seen == INVOKEVIRTUAL) || (seen == INVOKESTATIC) || (seen == INVOKESPECIAL)) {
                String lcName = getNameConstantOperand().toLowerCase(Locale.ENGLISH);
                if (seen == INVOKEVIRTUAL) {
                    String sig = getSigConstantOperand();
                    if ("equals".equals(lcName) && "(Ljava/lang/Object;)Z".equals(sig)) {
                        state = State.SAW_EQUALS;
                    }
                }

                // assume that if you call a method in the unit test class, or
                // call a method with assert of verify in them
                // it's possibly doing asserts for you. Yes this is a hack

                if (clsName.equals(getClassConstantOperand()) || lcName.contains("assert") || lcName.contains("verify")) {
                    sawAssert = true;
                }
            }

        } finally {
            TernaryPatcher.pre(stack, seen);
            stack.sawOpcode(this, seen);
            TernaryPatcher.post(stack, seen);
            if ((userValue != null) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item item = stack.getStackItem(0);
                item.setUserValue(userValue);
            }
        }
    }

    private boolean isFloatingPtPrimitive(String signature) {
        return "D".equals(signature) || "F".equals(signature);
    }

    private boolean hasExpects() {
        AnnotationEntry[] annotations = getMethod().getAnnotationEntries();
        if (annotations != null) {
            for (AnnotationEntry annotation : annotations) {
                String type = annotation.getAnnotationType();
                if ("Lorg/junit/Test;".equals(type) || "Lorg/testng/annotations/Test;".equals(type)) {
                    ElementValuePair[] evPairs = annotation.getElementValuePairs();
                    if (evPairs != null) {
                        for (ElementValuePair evPair : evPairs) {
                            String evName = evPair.getNameString();
                            if ("expected".equals(evName) || "expectedExceptions".equals(evName)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    private boolean hasFieldInjectorAnnotation(OpcodeStack.Item item) {
        XField xf = item.getXField();
        if (xf == null) {
            return false;
        }

        Collection<AnnotationValue> annotations = xf.getAnnotations();
        for (AnnotationValue value : annotations) {
            if (INJECTOR_ANNOTATIONS.contains(value.getAnnotationClass().getDottedClassName())) {
                return true;
            }
        }
        return false;
    }
}
