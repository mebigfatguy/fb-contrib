package com.mebigfatguy.fbcontrib.detect;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.ElementValuePair;
import org.apache.bcel.classfile.ExceptionTable;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.FieldOrMethod;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;

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
    }

    private static JavaClass runtimeExceptionClass;

    static {
        try {
            runtimeExceptionClass = Repository.lookupClass("java.lang.RuntimeException");
        } catch (Exception e) {
            // can't log, have no bugReporter
        }
    }

    private static final Pattern annotationClassPattern = Pattern.compile("(L[^;]+;)");

    private BugReporter bugReporter;

    private JavaClass cls;
    private OpcodeStack stack;
    private Map<FQMethod, Boolean> transactionalMethods;
    private boolean isEntity;
    private boolean hasId;
    private boolean hasGeneratedValue;
    private boolean hasEagerOneToMany;
    private boolean hasFetch;
    private boolean hasHCEquals;

    public JPAIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext clsContext) {
        try {
            cls = clsContext.getJavaClass();
            catalogClass(cls);

            if (isEntity) {
                if (hasHCEquals && hasId && hasGeneratedValue) {
                    bugReporter.reportBug(new BugInstance(this, BugType.JPAI_HC_EQUALS_ON_MANAGED_ENTITY.name(), LOW_PRIORITY).addClass(cls));
                }
                if (hasEagerOneToMany && !hasFetch) {
                    bugReporter.reportBug(new BugInstance(this, BugType.JPAI_INEFFICIENT_EAGER_FETCH.name(), LOW_PRIORITY).addClass(cls));
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

    @Override
    public void visitMethod(Method obj) {
        TransactionalType transType = getTransactionalType(obj);
        if (!obj.isPublic() && (transType != TransactionalType.NONE)) {
            bugReporter
                    .reportBug(new BugInstance(this, BugType.JPAI_TRANSACTION_ON_NON_PUBLIC_METHOD.name(), NORMAL_PRIORITY).addClass(this).addMethod(cls, obj));
        }

        if ((transType == TransactionalType.WRITE) && (runtimeExceptionClass != null)) {
            try {
                Set<JavaClass> annotatedRollBackExceptions = getAnnotatedRollbackExceptions(obj);
                Set<JavaClass> declaredExceptions = getDeclaredExceptions(obj);
                reportExceptionMismatch(obj, annotatedRollBackExceptions, declaredExceptions, false, BugType.JPAI_NON_SPECIFIED_TRANSACTION_EXCEPTION_HANDLING);
                reportExceptionMismatch(obj, declaredExceptions, annotatedRollBackExceptions, true, BugType.JPAI_UNNECESSARY_TRANSACTION_EXCEPTION_HANDLING);
            } catch (ClassNotFoundException cnfe) {
                bugReporter.reportMissingClass(cnfe);
            }
        }

        super.visitMethod(obj);
    }

    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        super.visitCode(obj);
    }

    @Override
    public void sawOpcode(int seen) {
        JPAUserValue userValue = null;

        try {
            switch (seen) {
                case INVOKEVIRTUAL:
                case INVOKEINTERFACE: {
                    String dottedCls = getDottedClassConstantOperand();
                    String methodName = getNameConstantOperand();
                    String signature = getSigConstantOperand();

                    if (transactionalMethods.containsKey(new FQMethod(dottedCls, methodName, signature))) {
                        Type[] parmTypes = Type.getArgumentTypes(signature);
                        if (stack.getStackDepth() > parmTypes.length) {
                            OpcodeStack.Item itm = stack.getStackItem(parmTypes.length);
                            if (itm.getRegisterNumber() == 0) {
                                bugReporter.reportBug(new BugInstance(this, BugType.JPAI_NON_PROXIED_TRANSACTION_CALL.name(), NORMAL_PRIORITY).addClass(this)
                                        .addMethod(this).addSourceLine(this));
                            }
                        }
                    }

                    if ("javax.persistence.EntityManager".equals(dottedCls) && "merge".equals(methodName)) {
                        userValue = JPAUserValue.MERGE;
                    }
                    break;
                }

                case POP: {
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item itm = stack.getStackItem(0);
                        if (itm.getUserValue() == JPAUserValue.MERGE) {
                            bugReporter.reportBug(new BugInstance(this, BugType.JPAI_IGNORED_MERGE_RESULT.name(), LOW_PRIORITY).addClass(this).addMethod(this)
                                    .addSourceLine(this));
                        }
                    }
                    break;
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

    private void catalogClass(JavaClass cls) {
        transactionalMethods = new HashMap<FQMethod, Boolean>();
        isEntity = false;
        hasId = false;
        hasGeneratedValue = false;
        hasEagerOneToMany = false;
        hasHCEquals = false;

        for (AnnotationEntry entry : cls.getAnnotationEntries()) {
            if ("Ljavax/persistence/Entity;".equals(entry.getAnnotationType())) {
                isEntity = true;
                break;
            }
        }

        for (Method m : cls.getMethods()) {
            catalogFieldOrMethod(m);

            if (("equals".equals(m.getName()) && "(Ljava/lang/Object;)Z".equals(m.getSignature()))
                    || ("hashCode".equals(m.getName()) && "()I".equals(m.getSignature()))) {
                hasHCEquals = true;
            }
        }

        for (Field f : cls.getFields()) {
            catalogFieldOrMethod(f);
        }
    }

    private void catalogFieldOrMethod(FieldOrMethod fm) {
        for (AnnotationEntry entry : fm.getAnnotationEntries()) {
            String type = entry.getAnnotationType();
            switch (type) {
                case "Lorg/springframework/transaction/annotation/Transactional;":
                    if (fm instanceof Method) {
                        Boolean isWrite = Boolean.TRUE;
                        for (ElementValuePair pair : entry.getElementValuePairs()) {
                            if ("readOnly".equals(pair.getNameString())) {
                                isWrite = Boolean.valueOf("false".equals(pair.getValue().stringifyValue()));
                                break;
                            }
                        }
                        transactionalMethods.put(new FQMethod(cls.getClassName(), fm.getName(), fm.getSignature()), isWrite);
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
            }
        }
    }

    private void reportExceptionMismatch(Method method, Set<JavaClass> expectedExceptions, Set<JavaClass> actualExceptions, boolean checkByDirectionally,
            BugType bugType) {
        try {
            for (JavaClass declEx : actualExceptions) {
                boolean handled = false;
                for (JavaClass annotEx : expectedExceptions) {
                    if (declEx.instanceOf(annotEx) || (checkByDirectionally && annotEx.instanceOf(declEx))) {
                        handled = true;
                        break;
                    }
                }

                if (!handled) {
                    if (!expectedExceptions.contains(declEx)) {
                        bugReporter.reportBug(new BugInstance(this, bugType.name(), NORMAL_PRIORITY).addClass(this).addMethod(cls, method)
                                .addString("Exception: " + declEx.getClassName()));
                    }
                }
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        }
    }

    private void reportNonThrownAnnotationRollbacks(Method method) {

    }

    private Set<JavaClass> getAnnotatedRollbackExceptions(Method method) throws ClassNotFoundException {

        for (AnnotationEntry annotation : method.getAnnotationEntries()) {
            if ("Lorg/springframework/transaction/annotation/Transactional;".equals(annotation.getAnnotationType())) {
                if (annotation.getNumElementValuePairs() == 0) {
                    return Collections.<JavaClass> emptySet();
                }
                Set<JavaClass> rollbackExceptions = new HashSet<JavaClass>();
                for (ElementValuePair pair : annotation.getElementValuePairs()) {
                    if ("rollbackFor".equals(pair.getNameString()) || "noRollbackFor".equals(pair.getNameString())) {

                        String exNames = pair.getValue().stringifyValue();
                        Matcher m = annotationClassPattern.matcher(exNames);
                        while (m.find()) {
                            String exName = m.group(1);
                            JavaClass exCls = Repository.lookupClass(exName.substring(1, exName.length() - 1));
                            if (!exCls.instanceOf(runtimeExceptionClass)) {
                                rollbackExceptions.add(exCls);
                            }
                        }
                    }
                }
                return rollbackExceptions;
            }
        }

        return Collections.<JavaClass> emptySet();
    }

    private Set<JavaClass> getDeclaredExceptions(Method method) throws ClassNotFoundException {
        ExceptionTable et = method.getExceptionTable();
        if ((et == null) || (et.getLength() == 0)) {
            return Collections.<JavaClass> emptySet();
        }

        Set<JavaClass> exceptions = new HashSet<JavaClass>();
        for (String en : et.getExceptionNames()) {
            JavaClass exCls = Repository.lookupClass(en);
            if (!exCls.instanceOf(runtimeExceptionClass)) {
                exceptions.add(exCls);
            }
        }

        return exceptions;
    }

    /**
     * this method limits the scope of a bad tri-state boolean pattern. The map could contain the TransactionType, but that would bloat the map horribly. So
     * hide the conversion from Boolean to TransactionType in this method
     *
     * @param m
     *            the method to check for transactional methods
     * @return whether the method is Transactional non, read or write
     */
    private TransactionalType getTransactionalType(Method m) {
        Boolean isWrite = transactionalMethods.get(new FQMethod(cls.getClassName(), m.getName(), m.getSignature()));
        if (isWrite == null) {
            return TransactionalType.NONE;
        }

        return isWrite ? TransactionalType.WRITE : TransactionalType.READ;
    }

}
