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
package com.mebigfatguy.fbcontrib.collect;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.Constants;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.Annotations;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.CollectionUtils;
import com.mebigfatguy.fbcontrib.utils.QMethod;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.NonReportingDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.SignatureParser;

/**
 * a first pass detector to collect various statistics used in second pass
 * detectors.
 */
public class CollectStatistics extends BytecodeScanningDetector implements NonReportingDetector {
    private static final Set<String> COMMON_METHOD_SIG_PREFIXES = UnmodifiableSet.create(
            // @formatter:off
            new SignatureBuilder().withMethodName(Values.CONSTRUCTOR).toString(),
            new SignatureBuilder().withMethodName(Values.TOSTRING).withReturnType(Values.SLASHED_JAVA_LANG_STRING)
                    .toString(),
            new SignatureBuilder().withMethodName(Values.HASHCODE).withReturnType(Values.SIG_PRIMITIVE_INT).toString(),
            "clone()", "values()",
            new SignatureBuilder().withMethodName("main").withParamTypes(SignatureBuilder.SIG_STRING_ARRAY).toString()
    // @formatter:on
    );

    private static final Set<String> BEAN_ANNOTATIONS = UnmodifiableSet.create(
            // @formatter:off
            "Lorg/springframework/stereotype/Component;", "Lorg/springframework/stereotype/Controller;",
            "Lorg/springframework/stereotype/Repository;", "Lorg/springframework/stereotype/Service;"
    // @formatter:on
    );

    private BugReporter bugReporter;
    private int numMethodCalls;
    private boolean modifiesState;
    private boolean classHasAnnotation;
    private OpcodeStack stack;
    private Map<QMethod, Set<CalledMethod>> selfCallTree;
    private Set<QMethod> constrainingMethods;
    private QMethod curMethod;

    /**
     * constructs a CollectStatistics detector which clears the singleton that holds
     * the statistics for all classes parsed in the first pass.
     *
     * @param bugReporter unused, but required by reflection contract
     */
    // required for reflection
    @SuppressWarnings("PMD.UnusedFormalParameter")
    public CollectStatistics(BugReporter bugReporter) {
        Statistics.getStatistics().clear();
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to collect statistics on this class
     *
     * @param classContext the currently class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            JavaClass cls = classContext.getJavaClass();
            constrainingMethods = buildConstrainingMethods(cls, new HashSet<String>());
            AnnotationEntry[] annotations = cls.getAnnotationEntries();
            classHasAnnotation = !CollectionUtils.isEmpty(annotations);
            stack = new OpcodeStack();
            selfCallTree = new HashMap<>();
            super.visitClassContext(classContext);

            performModifyStateClosure(classContext.getJavaClass());

        } finally {
            stack = null;
            selfCallTree = null;
            curMethod = null;
            constrainingMethods = null;
        }
    }

    @Override
    public void visitAnnotation(Annotations annotations) {
        for (AnnotationEntry entry : annotations.getAnnotationEntries()) {
            String annotationType = entry.getAnnotationType();
            if (BEAN_ANNOTATIONS.contains(annotationType)) {
                Statistics.getStatistics().addAutowiredBean(getDottedClassName());
            }
        }
    }

    @Override
    public void visitCode(Code obj) {

        numMethodCalls = 0;
        modifiesState = false;

        byte[] code = obj.getCode();
        if (code == null) {
            return;
        }
        stack.resetForMethodEntry(this);
        curMethod = null;
        super.visitCode(obj);
        String clsName = getClassName();
        Method method = getMethod();
        int accessFlags = method.getAccessFlags();

        boolean isDerived = false;
        if (!constrainingMethods.isEmpty()) {
            QMethod qm = new QMethod(method.getName(), method.getSignature());
            isDerived = isConstrained(qm);
        }

        MethodInfo mi = Statistics.getStatistics().addMethodStatistics(clsName, getMethodName(), getMethodSig(),
                accessFlags, code.length, numMethodCalls, isDerived);
        if (clsName.indexOf(Values.INNER_CLASS_SEPARATOR) >= 0
                || (accessFlags & (ACC_ABSTRACT | ACC_INTERFACE | ACC_ANNOTATION)) != 0) {
            mi.addCallingAccess(Constants.ACC_PUBLIC);
        } else if ((accessFlags & Constants.ACC_PRIVATE) == 0) {
            if (isAssociationedWithAnnotations(method)) {
                mi.addCallingAccess(Constants.ACC_PUBLIC);
            } else {
                String methodSig = getMethodName() + getMethodSig();
                for (String sig : COMMON_METHOD_SIG_PREFIXES) {
                    if (methodSig.startsWith(sig)) {
                        mi.addCallingAccess(Constants.ACC_PUBLIC);
                        break;
                    }
                }
            }
        }

        mi.setModifiesState(modifiesState);
    }

    @Override
    public void sawOpcode(int seen) {
        try {
            switch (seen) {
            case INVOKEVIRTUAL:
            case INVOKEINTERFACE:
            case INVOKESPECIAL:
            case INVOKESTATIC:
            case INVOKEDYNAMIC:
                numMethodCalls++;

                if (seen != INVOKESTATIC) {
                    int numParms = SignatureUtils.getNumParameters(getSigConstantOperand());
                    if (stack.getStackDepth() > numParms) {
                        OpcodeStack.Item itm = stack.getStackItem(numParms);
                        if (itm.getRegisterNumber() == 0) {
                            Set<CalledMethod> calledMethods;

                            if (curMethod == null) {
                                curMethod = new QMethod(getMethodName(), getMethodSig());
                                calledMethods = new HashSet<>();
                                selfCallTree.put(curMethod, calledMethods);
                            } else {
                                calledMethods = selfCallTree.get(curMethod);
                            }

                            calledMethods.add(
                                    new CalledMethod(new QMethod(getNameConstantOperand(), getSigConstantOperand()),
                                            seen == INVOKESPECIAL));
                        }
                    }
                }
                break;

            case PUTSTATIC:
            case PUTFIELD:
                modifiesState = true;
                break;

            default:
                break;
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    private void performModifyStateClosure(JavaClass cls) {
        boolean foundNewCall = true;
        Statistics statistics = Statistics.getStatistics();

        String clsName = cls.getClassName();
        while (foundNewCall && !selfCallTree.isEmpty()) {
            foundNewCall = false;

            Iterator<Map.Entry<QMethod, Set<CalledMethod>>> callerIt = selfCallTree.entrySet().iterator();
            while (callerIt.hasNext()) {
                Map.Entry<QMethod, Set<CalledMethod>> callerEntry = callerIt.next();
                QMethod caller = callerEntry.getKey();

                MethodInfo callerMi = statistics.getMethodStatistics(clsName, caller.getMethodName(),
                        caller.getSignature());
                if (callerMi == null) {
                    // odd, shouldn't happen
                    foundNewCall = true;
                } else if (callerMi.getModifiesState()) {
                    foundNewCall = true;
                } else {

                    for (CalledMethod calledMethod : callerEntry.getValue()) {

                        if (calledMethod.isSuper) {
                            callerMi.setModifiesState(true);
                            foundNewCall = true;
                            break;
                        }

                        MethodInfo calleeMi = statistics.getMethodStatistics(clsName,
                                calledMethod.callee.getMethodName(), calledMethod.callee.getSignature());
                        if (calleeMi == null) {
                            // a super or sub class probably implements this method so just assume it
                            // modifies state
                            callerMi.setModifiesState(true);
                            foundNewCall = true;
                            break;
                        }

                        if (calleeMi.getModifiesState()) {
                            callerMi.setModifiesState(true);
                            foundNewCall = true;
                            break;
                        }
                    }
                }

                if (foundNewCall) {
                    callerIt.remove();
                }
            }
        }

        selfCallTree.clear();
    }

    private boolean isAssociationedWithAnnotations(Method m) {
        if (classHasAnnotation) {
            return true;
        }

        return !CollectionUtils.isEmpty(m.getAnnotationEntries());
    }

    private Set<QMethod> buildConstrainingMethods(JavaClass cls, Set<String> visitedClasses) {

        Set<QMethod> constraints = new HashSet<>();
        try {
            for (JavaClass inf : cls.getInterfaces()) {
                String infName = inf.getClassName();
                if (!visitedClasses.contains(infName)) {
                    visitedClasses.add(infName);

                    for (Method m : inf.getMethods()) {
                        constraints.add(new QMethod(m.getName(), m.getSignature()));
                    }
                }
            }

            for (JavaClass parent : cls.getSuperClasses()) {
                String clsName = parent.getClassName();
                if (!visitedClasses.contains(clsName)) {
                    visitedClasses.add(clsName);

                    if (!Values.DOTTED_JAVA_LANG_OBJECT.equals(parent.getClassName())) {
                        for (Method m : parent.getMethods()) {
                            constraints.add(new QMethod(m.getName(), m.getSignature()));
                        }
                        constraints.addAll(buildConstrainingMethods(parent, visitedClasses));
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            bugReporter.reportMissingClass(e);
        }

        return constraints;
    }

    private boolean isConstrained(QMethod m) {
        if (constrainingMethods.contains(m)) {
            return true;
        }

        SignatureParser mp = new SignatureParser(m.getSignature());
        int numParms = mp.getNumParameters();

        for (QMethod constraint : constrainingMethods) {
            if (m.getMethodName().equals(constraint.getMethodName())) {
                SignatureParser cp = new SignatureParser(constraint.getSignature());
                if (numParms == cp.getNumParameters()) {
                    String[] mParms = mp.getArguments();
                    String[] cParms = cp.getArguments();

                    boolean matches = true;
                    for (int i = 0; i < numParms; i++) {
                        if (Values.SIG_JAVA_LANG_OBJECT.equals(cParms[i])) {
                            matches = mParms[i].charAt(0) == 'L';
                        } else {
                            matches = cParms[i].equals(mParms[i]);
                            if (!matches && cParms[i].charAt(0) == 'L' && mParms[i].charAt(0) == 'L') {
                                try {
                                    JavaClass cc = Repository.lookupClass(SignatureUtils.stripSignature(cParms[i]));
                                    JavaClass mc = Repository.lookupClass(SignatureUtils.stripSignature(mParms[i]));
                                    matches = mc.instanceOf(cc);
                                } catch (ClassNotFoundException e) {
                                    bugReporter.reportMissingClass(e);
                                    matches = false;
                                }
                            }
                        }

                        if (!matches) {
                            break;
                        }
                    }
                    if (matches) {
                        if (Values.SIG_JAVA_LANG_OBJECT.equals(cp.getReturnTypeSignature())) {
                            matches = mp.getReturnTypeSignature().charAt(0) == 'L';
                        } else {
                            String cRet = cp.getReturnTypeSignature();
                            String mRet = mp.getReturnTypeSignature();
                            matches = cRet.equals(mRet);
                            if (!matches && cRet.charAt(0) == 'L' && mRet.charAt(0) == 'L') {
                                try {
                                    JavaClass cc = Repository.lookupClass(SignatureUtils.stripSignature(cRet));
                                    JavaClass mc = Repository.lookupClass(SignatureUtils.stripSignature(mRet));
                                    matches = mc.instanceOf(cc);
                                } catch (ClassNotFoundException e) {
                                    bugReporter.reportMissingClass(e);
                                    matches = false;
                                }
                            }
                        }
                    }

                    if (matches) {
                        return true;
                    }
                }

            }
        }

        return false;
    }

    /**
     * represents a method that is called, and whether it is in the super class
     */
    static class CalledMethod {
        private QMethod callee;
        private boolean isSuper;

        public CalledMethod(QMethod c, boolean s) {
            callee = c;
            isSuper = s;
        }

        @Override
        public int hashCode() {
            return callee.hashCode() & (isSuper ? 0 : 1);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof CalledMethod)) {
                return false;
            }

            CalledMethod that = (CalledMethod) obj;

            return isSuper == that.isSuper && callee.equals(that.callee);
        }
    }
}
