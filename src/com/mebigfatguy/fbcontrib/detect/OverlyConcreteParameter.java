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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.Constants;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.CodeException;
import org.apache.bcel.classfile.ExceptionTable;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.LocalVariable;
import org.apache.bcel.classfile.LocalVariableTable;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.classfile.ParameterAnnotationEntry;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.StopOpcodeParsingException;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for parameters that are defined by classes, but only use methods defined by an implemented interface or super class. Relying on concrete classes in
 * public signatures causes cohesion, and makes low impact changes more difficult.
 */
public class OverlyConcreteParameter extends BytecodeScanningDetector {

    private static final Set<String> CONVERSION_ANNOTATIONS = UnmodifiableSet.create("Ljavax/persistence/Converter;", "Ljavax/ws/rs/Consumes;");

    private static final Set<String> CONVERSION_SUPER_CLASSES = UnmodifiableSet.create("com.fasterxml.jackson.databind.JsonSerializer",
            "com.fasterxml.jackson.databind.JsonDeserializer");

    private final BugReporter bugReporter;
    private JavaClass[] constrainingClasses;
    private Map<Integer, Map<JavaClass, List<MethodInfo>>> parameterDefiners;
    private BitSet usedParameters;
    private JavaClass objectClass;
    private JavaClass cls;
    private OpcodeStack stack;
    private int parmCount;
    private boolean methodSignatureIsConstrained;
    private boolean methodIsStatic;

    /**
     * constructs a OCP detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public OverlyConcreteParameter(final BugReporter bugReporter) {
        this.bugReporter = bugReporter;
        try {
            objectClass = Repository.lookupClass("java/lang/Object");
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
            objectClass = null;
        }
    }

    /**
     * implements the visitor to collect classes that constrains this class (super classes/interfaces) and to reset the opcode stack
     *
     * @param classContext
     *            the currently parse class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            cls = classContext.getJavaClass();

            if (!isaConversionClass(cls)) {
                JavaClass[] infs = cls.getAllInterfaces();
                JavaClass[] sups = cls.getSuperClasses();
                constrainingClasses = new JavaClass[infs.length + sups.length];
                System.arraycopy(infs, 0, constrainingClasses, 0, infs.length);
                System.arraycopy(sups, 0, constrainingClasses, infs.length, sups.length);
                parameterDefiners = new HashMap<>();
                usedParameters = new BitSet();
                stack = new OpcodeStack();
                super.visitClassContext(classContext);
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        } finally {
            constrainingClasses = null;
            parameterDefiners = null;
            usedParameters = null;
            stack = null;
        }
    }

    /**
     * implements the visitor to look to see if this method is constrained by a superclass or interface.
     *
     * @param obj
     *            the currently parsed method
     */
    @Override
    public void visitMethod(Method obj) {
        methodSignatureIsConstrained = false;
        String methodName = obj.getName();

        if (!Values.CONSTRUCTOR.equals(methodName) && !Values.STATIC_INITIALIZER.equals(methodName)) {

            String methodSig = obj.getSignature();

            methodSignatureIsConstrained = methodIsSpecial(methodName, methodSig) || methodHasSyntheticTwin(methodName, methodSig);

            if (!methodSignatureIsConstrained) {
                for (AnnotationEntry entry : obj.getAnnotationEntries()) {
                    if (CONVERSION_ANNOTATIONS.contains(entry.getAnnotationType())) {
                        methodSignatureIsConstrained = true;
                        break;
                    }
                }
            }
            if (!methodSignatureIsConstrained) {
                String parms = methodSig.split("\\(|\\)")[1];
                if (parms.indexOf(';') >= 0) {

                    outer: for (JavaClass cls : constrainingClasses) {
                        Method[] methods = cls.getMethods();
                        for (Method m : methods) {
                            if (methodName.equals(m.getName()) && methodSig.equals(m.getSignature())) {
                                methodSignatureIsConstrained = true;
                                break outer;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * implements the visitor to collect information about the parameters of a this method
     *
     * @param obj
     *            the currently parsed code block
     */
    @Override
    public void visitCode(final Code obj) {
        try {
            if (methodSignatureIsConstrained) {
                return;
            }

            if (obj.getCode() == null) {
                return;
            }
            Method m = getMethod();

            if (m.isSynthetic()) {
                return;
            }

            if (m.getName().startsWith("access$")) {
                return;
            }

            methodIsStatic = m.isStatic();
            parmCount = m.getArgumentTypes().length;

            if (parmCount == 0) {
                return;
            }

            parameterDefiners.clear();
            usedParameters.clear();
            stack.resetForMethodEntry(this);

            if (buildParameterDefiners()) {
                try {
                    super.visitCode(obj);
                    reportBugs();
                } catch (StopOpcodeParsingException e) {
                    // no more possible parameter definers
                }
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        }
    }

    /**
     * implements the visitor to filter out parameter use where the actual defined type of the method declaration is needed. What remains could be more
     * abstractly defined.
     *
     * @param seen
     *            the currently parsed opcode
     */
    @Override
    public void sawOpcode(final int seen) {

        try {
            stack.precomputation(this);

            if (OpcodeUtils.isInvoke(seen)) {
                String methodSig = getSigConstantOperand();
                Type[] parmTypes = Type.getArgumentTypes(methodSig);
                int stackDepth = stack.getStackDepth();
                if (stackDepth >= parmTypes.length) {
                    for (int i = 0; i < parmTypes.length; i++) {
                        OpcodeStack.Item itm = stack.getStackItem(i);
                        int reg = itm.getRegisterNumber();
                        removeUselessDefiners(parmTypes[parmTypes.length - i - 1].getSignature(), reg);
                    }
                }

                if ((seen != INVOKESPECIAL) && (seen != INVOKESTATIC)) {
                    if (stackDepth > parmTypes.length) {
                        OpcodeStack.Item itm = stack.getStackItem(parmTypes.length);
                        int reg = itm.getRegisterNumber();
                        int parm = reg;
                        if (!methodIsStatic) {
                            parm--;
                        }
                        if ((parm >= 0) && (parm < parmCount)) {
                            removeUselessDefiners(reg);
                        }
                    } else {
                        parameterDefiners.clear();
                    }
                }
            } else if ((seen == ASTORE) || ((seen >= ASTORE_0) && (seen <= ASTORE_3)) || (seen == PUTFIELD) || (seen == GETFIELD) || (seen == PUTSTATIC)
                    || (seen == GETSTATIC)) {
                // Don't check parameters that are aliased
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item itm = stack.getStackItem(0);
                    int reg = itm.getRegisterNumber();
                    int parm = reg;
                    if (!methodIsStatic) {
                        parm--;
                    }
                    if ((parm >= 0) && (parm < parmCount)) {
                        parameterDefiners.remove(Integer.valueOf(reg));
                    }
                } else {
                    parameterDefiners.clear();
                }

                if ((seen == GETFIELD) || (seen == PUTFIELD)) {
                    if (stack.getStackDepth() > 1) {
                        OpcodeStack.Item itm = stack.getStackItem(1);
                        int reg = itm.getRegisterNumber();
                        int parm = reg;
                        if (!methodIsStatic) {
                            parm--;
                        }
                        if ((parm >= 0) && (parm < parmCount)) {
                            parameterDefiners.remove(Integer.valueOf(reg));
                        }
                    } else {
                        parameterDefiners.clear();
                    }
                }

            } else if (OpcodeUtils.isALoad(seen)) {
                int reg = RegisterUtils.getALoadReg(this, seen);

                int parm = reg;
                if (!methodIsStatic) {
                    parm--;
                }
                if ((parm >= 0) && (parm < parmCount)) {
                    usedParameters.set(reg);
                }
            } else if (seen == AASTORE) {
                // Don't check parameters that are stored in
                if (stack.getStackDepth() >= 3) {
                    OpcodeStack.Item itm = stack.getStackItem(0);
                    int reg = itm.getRegisterNumber();
                    int parm = reg;
                    if (!methodIsStatic) {
                        parm--;
                    }
                    if ((parm >= 0) && (parm < parmCount)) {
                        parameterDefiners.remove(Integer.valueOf(reg));
                    }
                } else {
                    parameterDefiners.clear();
                }
            } else if (seen == ARETURN) {
                if (stack.getStackDepth() >= 1) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    int reg = item.getRegisterNumber();
                    int parm = reg;
                    if (!methodIsStatic) {
                        parm--;
                    }

                    if ((parm >= 0) && (parm < parmCount)) {
                        parameterDefiners.remove(Integer.valueOf(reg));
                    }
                } else {
                    parameterDefiners.clear();
                }
            }
        } finally {
            if (parameterDefiners.isEmpty()) {
                throw new StopOpcodeParsingException();
            }

            stack.sawOpcode(this, seen);
        }
    }

    /**
     * determines whether the method is a baked in special method of the jdk
     *
     * @param methodName
     *            the method name to check
     * @param methodSig
     *            the parameter signature of the method to check
     * @return if it is a well known baked in method
     */
    private static boolean methodIsSpecial(String methodName, String methodSig) {
        return ("readObject".equals(methodName) && "(Ljava/io/ObjectInputStream;)V".equals(methodSig));
    }

    /**
     * returns whether this method has an equivalent method that is synthetic, which implies this method is constrained by some Generified interface. We could
     * compare parameters but that is a bunch of work that probably doesn't make this test any more precise, so just return true if method name and synthetic is
     * found.
     *
     * @param methodName
     *            the method name to look for a synthetic twin of
     * @param methodSig
     *            the method signature to lookfor a synthetic twin of
     * @return if a synthetic twin is found
     */
    private boolean methodHasSyntheticTwin(String methodName, String methodSig) {
        for (Method m : cls.getMethods()) {
            if (m.isSynthetic() && m.getName().equals(methodName) && !m.getSignature().equals(methodSig)) {
                return true;
            }
        }

        return false;
    }

    /**
     * implements the post processing steps to report the remaining unremoved parameter definers, ie those, that can be defined more abstractly.
     */
    private void reportBugs() {
        Iterator<Map.Entry<Integer, Map<JavaClass, List<MethodInfo>>>> it = parameterDefiners.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Map<JavaClass, List<MethodInfo>>> entry = it.next();

            Integer reg = entry.getKey();
            if (!usedParameters.get(reg.intValue())) {
                it.remove();
                continue;
            }
            Map<JavaClass, List<MethodInfo>> definers = entry.getValue();
            definers.remove(objectClass);
            if (definers.size() > 0) {
                String name = "";
                LocalVariableTable lvt = getMethod().getLocalVariableTable();
                if (lvt != null) {
                    LocalVariable lv = lvt.getLocalVariable(reg.intValue(), 0);
                    if (lv != null) {
                        name = lv.getName();
                    }
                }
                int parm = reg.intValue();
                if (!methodIsStatic) {
                    parm--;
                }
                parm++; // users expect 1 based parameters

                String infName = definers.keySet().iterator().next().getClassName();
                bugReporter.reportBug(new BugInstance(this, BugType.OCP_OVERLY_CONCRETE_PARAMETER.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                        .addSourceLine(this, 0).addString(getCardinality(parm) + " parameter '" + name + "' could be declared as " + infName + " instead"));
            }
        }
    }

    /**
     * returns a string defining what parameter in the signature a certain one is, for the bug report
     *
     * @param num
     *            the parameter number
     * @return a string describing in english the parameter position
     */
    private static String getCardinality(int num) {
        if (num == 1) {
            return "1st";
        }
        if (num == 2) {
            return "2nd";
        }
        if (num == 3) {
            return "3rd";
        }
        return num + "th";
    }

    /**
     * builds a map of method information for each method of each interface that each parameter implements of this method
     *
     * @return a map by parameter id of all the method signatures that interfaces of that parameter implements
     *
     * @throws ClassNotFoundException
     *             if the class can't be loaded
     */
    private boolean buildParameterDefiners() throws ClassNotFoundException {

        Method m = getMethod();

        Type[] parms = m.getArgumentTypes();
        if (parms.length == 0) {
            return false;
        }

        ParameterAnnotationEntry[] annotations = m.getParameterAnnotationEntries();

        boolean hasPossiblyOverlyConcreteParm = false;

        for (int i = 0; i < parms.length; i++) {
            if ((annotations.length <= i) || (annotations[i] == null) || (annotations[i].getAnnotationEntries().length == 0)) {
                String parm = parms[i].getSignature();
                if (parm.startsWith("L")) {
                    String clsName = SignatureUtils.stripSignature(parm);
                    if (clsName.startsWith("java.lang.")) {
                        continue;
                    }

                    JavaClass cls = Repository.lookupClass(clsName);
                    if (cls.isClass() && (!cls.isAbstract())) {
                        Map<JavaClass, List<MethodInfo>> definers = getClassDefiners(cls);

                        if (definers.size() > 0) {
                            parameterDefiners.put(Integer.valueOf(i + (methodIsStatic ? 0 : 1)), definers);
                            hasPossiblyOverlyConcreteParm = true;
                        }
                    }
                }
            }
        }

        return hasPossiblyOverlyConcreteParm;
    }

    /**
     * returns a map of method information for each public method for each interface this class implements
     *
     * @param cls
     *            the class whose interfaces to record
     *
     * @return a map of (method name)(method sig) by interface
     * @throws ClassNotFoundException
     *             if unable to load the class
     */
    private static Map<JavaClass, List<MethodInfo>> getClassDefiners(final JavaClass cls) throws ClassNotFoundException {
        Map<JavaClass, List<MethodInfo>> definers = new HashMap<>();

        for (JavaClass ci : cls.getAllInterfaces()) {
            if ("java.lang.Comparable".equals(ci.getClassName())) {
                continue;
            }
            List<MethodInfo> methodInfos = getPublicMethodInfos(ci);
            if (methodInfos.size() > 0) {
                definers.put(ci, methodInfos);
            }
        }
        return definers;
    }

    /**
     * returns a list of method information of all public or protected methods in this class
     *
     * @param cls
     *            the class to look for methods
     * @return a map of (method name)(method signature)
     */
    private static List<MethodInfo> getPublicMethodInfos(final JavaClass cls) {
        List<MethodInfo> methodInfos = new ArrayList<>();
        Method[] methods = cls.getMethods();
        for (Method m : methods) {
            if ((m.getAccessFlags() & (Constants.ACC_PUBLIC | Constants.ACC_PROTECTED)) != 0) {
                ExceptionTable et = m.getExceptionTable();
                methodInfos.add(new MethodInfo(m.getName(), m.getSignature(), et == null ? null : et.getExceptionNames()));
            }
        }
        return methodInfos;
    }

    /**
     * parses through the interface that 'may' define a parameter defined by reg, and look to see if we can rule it out, because a method is called on the
     * object that can't be satisfied by the interface, if so remove that candidate interface.
     *
     * @param reg
     *            the parameter register number to look at
     */
    private void removeUselessDefiners(final int reg) {

        Map<JavaClass, List<MethodInfo>> definers = parameterDefiners.get(Integer.valueOf(reg));
        if ((definers != null) && (definers.size() > 0)) {
            String methodSig = getSigConstantOperand();
            String methodName = getNameConstantOperand();
            MethodInfo methodInfo = new MethodInfo(methodName, methodSig, null);

            Iterator<List<MethodInfo>> it = definers.values().iterator();
            while (it.hasNext()) {
                boolean methodDefined = false;
                List<MethodInfo> methodSigs = it.next();

                for (MethodInfo mi : methodSigs) {
                    if (methodInfo.equals(mi)) {
                        methodDefined = true;
                        String[] exceptions = mi.getMethodExceptions();
                        if (exceptions != null) {
                            for (String ex : exceptions) {
                                if (!isExceptionHandled(ex)) {
                                    methodDefined = false;
                                    break;
                                }
                            }
                        }
                        break;
                    }
                }
                if (!methodDefined) {
                    it.remove();
                }
            }
            if (definers.isEmpty()) {
                parameterDefiners.remove(Integer.valueOf(reg));
            }
        }
    }

    /**
     * returns whether this exception is handled either in a try/catch or throws clause at this pc
     *
     * @param ex
     *            the name of the exception
     *
     * @return whether the exception is handled
     */
    private boolean isExceptionHandled(String ex) {
        try {
            JavaClass thrownEx = Repository.lookupClass(ex);
            // First look at the throws clause
            ExceptionTable et = getMethod().getExceptionTable();
            if (et != null) {
                String[] throwClauseExNames = et.getExceptionNames();
                for (String throwClauseExName : throwClauseExNames) {
                    JavaClass throwClauseEx = Repository.lookupClass(throwClauseExName);
                    if (thrownEx.instanceOf(throwClauseEx)) {
                        return true;
                    }
                }
            }
            // Next look at the try catch blocks
            CodeException[] catchExs = getCode().getExceptionTable();
            if (catchExs != null) {
                int pc = getPC();
                for (CodeException catchEx : catchExs) {
                    if ((pc >= catchEx.getStartPC()) && (pc <= catchEx.getEndPC())) {
                        int type = catchEx.getCatchType();
                        if (type != 0) {
                            String catchExName = getConstantPool().getConstantString(type, Constants.CONSTANT_Class);
                            JavaClass catchException = Repository.lookupClass(catchExName);
                            if (thrownEx.instanceOf(catchException)) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        }
        return false;
    }

    private void removeUselessDefiners(String parmSig, final int reg) {
        if (parmSig.startsWith("L")) {
            parmSig = SignatureUtils.stripSignature(parmSig);
            if (Values.DOTTED_JAVA_LANG_OBJECT.equals(parmSig)) {
                parameterDefiners.remove(Integer.valueOf(reg));
                return;
            }

            Map<JavaClass, List<MethodInfo>> definers = parameterDefiners.get(Integer.valueOf(reg));
            if ((definers != null) && (definers.size() > 0)) {
                Iterator<JavaClass> it = definers.keySet().iterator();
                while (it.hasNext()) {
                    JavaClass definer = it.next();
                    if (!definer.getClassName().equals(parmSig)) {
                        it.remove();
                    }
                }

                if (definers.isEmpty()) {
                    parameterDefiners.remove(Integer.valueOf(reg));
                }
            }
        }
    }

    /**
     * returns whether this class is used to convert types of some sort, such that you don't want to suggest reducing the class specified to be more generic
     *
     * @param cls
     *            the class to check
     * @return whether this class is used in conversions
     */
    private boolean isaConversionClass(JavaClass cls) {
        for (AnnotationEntry entry : cls.getAnnotationEntries()) {
            if (CONVERSION_ANNOTATIONS.contains(entry.getAnnotationType())) {
                return true;
            }

            // this ignores the fact that this class might be a grand child, but meh
            if (CONVERSION_SUPER_CLASSES.contains(cls.getSuperclassName())) {
                return true;
            }
        }

        return false;
    }

    /**
     * an inner helper class that holds basic information about a method
     */
    static class MethodInfo {
        private final String methodName;
        private final String methodSig;
        private final String[] methodExceptions;

        MethodInfo(String name, String sig, String[] excs) {
            methodName = name;
            methodSig = sig;
            methodExceptions = excs;
        }

        String getMethodName() {
            return methodName;
        }

        String getMethodSignature() {
            return methodSig;
        }

        String[] getMethodExceptions() {
            return methodExceptions;
        }

        @Override
        public int hashCode() {
            return methodName.hashCode() ^ methodSig.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof MethodInfo)) {
                return false;
            }

            MethodInfo that = (MethodInfo) o;

            if (!methodName.equals(that.methodName)) {
                return false;
            }
            if (!methodSig.equals(that.methodSig)) {
                return false;
            }

            return true;
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }
}
