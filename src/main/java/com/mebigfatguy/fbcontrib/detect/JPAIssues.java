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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.bcel.Const;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.ElementValuePair;
import org.apache.bcel.classfile.ExceptionTable;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.FieldOrMethod;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for various issues around the use of the Java Persistence API (JPA)
 */
@CustomUserValue
public class JPAIssues extends BytecodeScanningDetector {

    enum JPAUserValue {
        MERGE
    };

    enum TransactionalType {
        NONE, READ, WRITE;

        public static boolean isContainedBy(TransactionalType type, TransactionalType containedType) {
            if (type == NONE) {
                return true;
            }

            if ((type == READ) && ((containedType == READ) || (containedType == WRITE))) {
                return true;
            }

            return ((type == WRITE) && (containedType == WRITE));
        }
    }

    private static final Pattern annotationClassPattern = Pattern.compile("(L[^;]+;)");

    private BugReporter bugReporter;
    private JavaClass runtimeExceptionClass;
    private JavaClass cls;
    private OpcodeStack stack;
    private Map<FQMethod, TransactionalType> transactionalMethods;
    private boolean isEntity;
    private boolean hasId;
    private boolean hasGeneratedValue;
    private boolean hasEagerOneToMany;
    private boolean hasFetch;
    private boolean hasHCEquals;
    private TransactionalType methodTransType;
    private boolean isPublic;

    /**
     * constructs a JPA detector given the reporter to report bugs on
     *
     * @param bugReporter the sync of bug reports
     */

    public JPAIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;

        try {
            runtimeExceptionClass = Repository.lookupClass(Values.SLASHED_JAVA_LANG_RUNTIMEEXCEPTION);
        } catch (ClassNotFoundException e) {
            bugReporter.reportMissingClass(e);
        }

    }

    /**
     * implements the visitor to find @Entity classes that have both generated @Ids
     * and have implemented hashCode/equals. Also looks for eager one to many join
     * fetches as that leads to 1+n queries.
     *
     * @param clsContext the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext clsContext) {
        try {
            cls = clsContext.getJavaClass();
            catalogClass(cls);

            if (isEntity) {
                if (hasHCEquals && hasId && hasGeneratedValue) {
                    bugReporter.reportBug(
                            new BugInstance(this, BugType.JPAI_HC_EQUALS_ON_MANAGED_ENTITY.name(), LOW_PRIORITY)
                                    .addClass(cls));
                }
                if (hasEagerOneToMany && !hasFetch) {
                    bugReporter
                            .reportBug(new BugInstance(this, BugType.JPAI_INEFFICIENT_EAGER_FETCH.name(), LOW_PRIORITY)
                                    .addClass(cls));
                }
            }

            if (!transactionalMethods.isEmpty()) {
                stack = new OpcodeStack();
                super.visitClassContext(clsContext);
            }
        } finally {
            transactionalMethods = null;
            stack = null;
        }
    }

    /**
     * implements the visitor to look for non public methods that have
     * an @Transactional annotation applied to it. Spring only scans public methods
     * for special handling. It also looks to see if the exceptions thrown by the
     * method line up with the declared exceptions handled in the @Transactional
     * annotation.
     *
     * @param obj the currently parse method
     */
    @Override
    public void visitMethod(Method obj) {

        if (getMethod().isSynthetic()) {
            return;
        }
        methodTransType = getTransactionalType(obj);
        if ((methodTransType != TransactionalType.NONE) && !obj.isPublic()) {
            bugReporter.reportBug(
                    new BugInstance(this, BugType.JPAI_TRANSACTION_ON_NON_PUBLIC_METHOD.name(), NORMAL_PRIORITY)
                            .addClass(this).addMethod(cls, obj));
        }

        if ((methodTransType == TransactionalType.WRITE) && (runtimeExceptionClass != null)) {
            try {
                Set<JavaClass> annotatedRollBackExceptions = getAnnotatedRollbackExceptions(obj);
                Set<JavaClass> declaredExceptions = getDeclaredExceptions(obj);
                reportExceptionMismatch(obj, annotatedRollBackExceptions, declaredExceptions, false,
                        BugType.JPAI_NON_SPECIFIED_TRANSACTION_EXCEPTION_HANDLING);
                reportExceptionMismatch(obj, declaredExceptions, annotatedRollBackExceptions, true,
                        BugType.JPAI_UNNECESSARY_TRANSACTION_EXCEPTION_HANDLING);
            } catch (ClassNotFoundException cnfe) {
                bugReporter.reportMissingClass(cnfe);
            }
        }

        super.visitMethod(obj);
    }

    /**
     * implements the visitor to reset the opcode stack, Note that the synthetic
     * check is done in both visitMethod and visitCode as visitMethod is not a
     * proper listener stopping method. We don't want to report issues reported in
     * visitMethod if it is synthetic, but we also don't want it to get into
     * sawOpcode, so that is why it is done here as well.
     *
     * @param obj the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {

        Method m = getMethod();
        if (m.isSynthetic()) {
            return;
        }

        isPublic = m.isPublic();

        stack.resetForMethodEntry(this);
        super.visitCode(obj);
    }

    /**
     * implements the visitor to look for calls to @Transactional methods that do
     * not go through a spring proxy. These methods are easily seen as internal
     * class calls. There are other cases as well, from external/internal classes
     * but these aren't reported.
     *
     * @param seen the currently parsed opcode
     */
    @Override
    public void sawOpcode(int seen) {
        JPAUserValue userValue = null;

        try {
            switch (seen) {
            case Const.INVOKEVIRTUAL:
            case Const.INVOKEINTERFACE: {
                userValue = processInvoke();
                break;
            }

            case Const.POP: {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item itm = stack.getStackItem(0);
                    if (itm.getUserValue() == JPAUserValue.MERGE) {
                        bugReporter
                                .reportBug(new BugInstance(this, BugType.JPAI_IGNORED_MERGE_RESULT.name(), LOW_PRIORITY)
                                        .addClass(this).addMethod(this).addSourceLine(this));
                    }
                }
                break;
            }

            default:
                break;
            }
        } finally {
            stack.sawOpcode(this, seen);
            if ((userValue != null) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item itm = stack.getStackItem(0);
                itm.setUserValue(userValue);
            }
        }
    }

    @Nullable
    private JPAUserValue processInvoke() {
        String dottedCls = getDottedClassConstantOperand();
        String methodName = getNameConstantOperand();
        String signature = getSigConstantOperand();

        TransactionalType calledMethodTransType = getTransactionalType(new FQMethod(dottedCls, methodName, signature));
        if ((calledMethodTransType != TransactionalType.NONE)
                && !TransactionalType.isContainedBy(calledMethodTransType, methodTransType)) {
            int numParameters = SignatureUtils.getNumParameters(signature);
            if (stack.getStackDepth() > numParameters) {
                OpcodeStack.Item itm = stack.getStackItem(numParameters);
                if (itm.getRegisterNumber() == 0) {
                    bugReporter.reportBug(new BugInstance(this, BugType.JPAI_NON_PROXIED_TRANSACTION_CALL.name(),
                            isPublic ? NORMAL_PRIORITY : LOW_PRIORITY).addClass(this).addMethod(this)
                                    .addSourceLine(this));
                }
            }
        }

        if ("javax.persistence.EntityManager".equals(dottedCls) && "merge".equals(methodName)) {
            return JPAUserValue.MERGE;
        }

        return null;
    }

    /**
     * parses the current class for spring-tx and jpa annotations, as well as
     * hashCode and equals methods.
     *
     * @param clz the currently parsed class
     */
    private void catalogClass(JavaClass clz) {
        transactionalMethods = new HashMap<>();
        isEntity = false;
        hasId = false;
        hasGeneratedValue = false;
        hasEagerOneToMany = false;
        hasHCEquals = false;

        for (AnnotationEntry entry : clz.getAnnotationEntries()) {
            if ("Ljavax/persistence/Entity;".equals(entry.getAnnotationType())) {
                isEntity = true;
                break;
            }
        }

        for (Method m : clz.getMethods()) {
            catalogFieldOrMethod(m);

            if (("equals".equals(m.getName()) && SignatureBuilder.SIG_OBJECT_TO_BOOLEAN.equals(m.getSignature()))
                    || (Values.HASHCODE.equals(m.getName())
                            && SignatureBuilder.SIG_VOID_TO_INT.equals(m.getSignature()))) {
                hasHCEquals = true;
            }
        }

        for (Field f : clz.getFields()) {
            catalogFieldOrMethod(f);
        }
    }

    /**
     * parses a field or method for spring-tx or jpa annotations
     *
     * @param fm the currently parsed field or method
     */
    private void catalogFieldOrMethod(FieldOrMethod fm) {
        for (AnnotationEntry entry : fm.getAnnotationEntries()) {
            String type = entry.getAnnotationType();
            switch (type) {
            case "Lorg/springframework/transaction/annotation/Transactional;":
                if (fm instanceof Method) {
                    boolean isWrite = true;
                    for (ElementValuePair pair : entry.getElementValuePairs()) {
                        if ("readOnly".equals(pair.getNameString())) {
                            isWrite = "false".equals(pair.getValue().stringifyValue());
                            break;
                        }
                    }
                    transactionalMethods.put(new FQMethod(cls.getClassName(), fm.getName(), fm.getSignature()),
                            isWrite ? TransactionalType.WRITE : TransactionalType.READ);
                }
                break;

            case "Ljavax/persistence/Id;":
                hasId = true;
                break;

            case "Ljavax/persistence/GeneratedValue;":
                hasGeneratedValue = true;
                break;

            case "Ljavax/persistence/OneToMany;":
                for (ElementValuePair pair : entry.getElementValuePairs()) {
                    if ("fetch".equals(pair.getNameString()) && "EAGER".equals(pair.getValue().stringifyValue())) {
                        hasEagerOneToMany = true;
                        break;
                    }
                }
                break;

            case "Lorg/hibernate/annotations/Fetch;":
            case "Lorg/eclipse/persistence/annotations/JoinFetch;":
            case "Lorg/eclipse/persistence/annotations/BatchFetch;":
                hasFetch = true;
                break;

            default:
                break;
            }
        }
    }

    /**
     * compares the current methods exceptions to those declared in the
     * spring-tx's @Transactional method, both rollbackFor and noRollbackFor. It
     * looks both ways, exceptions thrown that aren't handled by
     * rollbacks/norollbacks, and Spring declarations that aren't actually thrown.
     *
     * @param method               the currently parsed method
     * @param expectedExceptions   exceptions declared in the @Transactional
     *                             annotation
     * @param actualExceptions     non-runtime exceptions that are thrown by the
     *                             method
     * @param checkByDirectionally whether to check both ways
     * @param bugType              what type of bug to report if found
     */
    private void reportExceptionMismatch(Method method, Set<JavaClass> expectedExceptions,
            Set<JavaClass> actualExceptions, boolean checkByDirectionally, BugType bugType) {
        try {
            for (JavaClass declEx : actualExceptions) {
                boolean handled = false;
                for (JavaClass annotEx : expectedExceptions) {
                    if (declEx.instanceOf(annotEx) || (checkByDirectionally && annotEx.instanceOf(declEx))) {
                        handled = true;
                        break;
                    }
                }

                if (!handled && !expectedExceptions.contains(declEx)) {
                    bugReporter.reportBug(new BugInstance(this, bugType.name(), NORMAL_PRIORITY).addClass(this)
                            .addMethod(cls, method).addString("Exception: " + declEx.getClassName()));
                }
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        }
    }

    /**
     * parses an spring-tx @Transactional annotations for rollbackFor/noRollbackfor
     * attributes of a @Transactional annotation.
     *
     * @param method the currently parsed method
     *
     * @return the exception classes declared in the @Transactional annotation
     *
     * @throws ClassNotFoundException if exception classes are not found
     */
    private Set<JavaClass> getAnnotatedRollbackExceptions(Method method) throws ClassNotFoundException {

        for (AnnotationEntry annotation : method.getAnnotationEntries()) {
            if ("Lorg/springframework/transaction/annotation/Transactional;".equals(annotation.getAnnotationType())) {
                if (annotation.getNumElementValuePairs() == 0) {
                    return Collections.<JavaClass>emptySet();
                }
                Set<JavaClass> rollbackExceptions = new HashSet<>();
                for (ElementValuePair pair : annotation.getElementValuePairs()) {
                    if ("rollbackFor".equals(pair.getNameString()) || "noRollbackFor".equals(pair.getNameString())) {

                        String exNames = pair.getValue().stringifyValue();
                        Matcher m = annotationClassPattern.matcher(exNames);
                        while (m.find()) {
                            String exName = m.group(1);
                            JavaClass exCls = Repository.lookupClass(SignatureUtils.trimSignature(exName));
                            if (!exCls.instanceOf(runtimeExceptionClass)) {
                                rollbackExceptions.add(exCls);
                            }
                        }
                    }
                }
                return rollbackExceptions;
            }
        }

        return Collections.<JavaClass>emptySet();
    }

    /**
     * retrieves the set of non-runtime exceptions that are declared to be thrown by
     * the method
     *
     * @param method the currently parsed method
     *
     * @return the set of exceptions thrown
     *
     * @throws ClassNotFoundException if an exception class is not found
     */
    private Set<JavaClass> getDeclaredExceptions(Method method) throws ClassNotFoundException {
        ExceptionTable et = method.getExceptionTable();
        if ((et == null) || (et.getLength() == 0)) {
            return Collections.<JavaClass>emptySet();
        }

        Set<JavaClass> exceptions = new HashSet<>();
        for (String en : et.getExceptionNames()) {
            JavaClass exCls = Repository.lookupClass(en);
            if (!exCls.instanceOf(runtimeExceptionClass)) {
                exceptions.add(exCls);
            }
        }

        return exceptions;
    }

    /**
     * returns the type of transactional annotation is applied to this method
     *
     * @param method the method to check for transactional methods
     * @return whether the method is Transactional non, read or write
     */
    private TransactionalType getTransactionalType(Method method) {
        return getTransactionalType(new FQMethod(cls.getClassName(), method.getName(), method.getSignature()));
    }

    /**
     * returns the type of transactional annotation is applied to this method
     *
     * @param method the method to check for transactional methods
     * @return whether the method is Transactional non, read or write
     */
    private TransactionalType getTransactionalType(FQMethod method) {
        TransactionalType type = transactionalMethods.get(method);
        if (type == null) {
            return TransactionalType.NONE;
        }

        return type;
    }

}
