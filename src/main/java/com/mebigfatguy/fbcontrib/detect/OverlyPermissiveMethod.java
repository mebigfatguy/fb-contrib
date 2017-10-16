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

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.apache.bcel.Constants;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.Attribute;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantCP;
import org.apache.bcel.classfile.ConstantClass;
import org.apache.bcel.classfile.ConstantInvokeDynamic;
import org.apache.bcel.classfile.ConstantMethodHandle;
import org.apache.bcel.classfile.ConstantNameAndType;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.ConstantUtf8;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.classfile.Unknown;

import com.mebigfatguy.fbcontrib.collect.MethodInfo;
import com.mebigfatguy.fbcontrib.collect.Statistics;
import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.classfile.ClassDescriptor;

/**
 * looks for methods that are declared more permissively than the code is using. For instance, declaring a method public, when it could just be declared
 * private.
 */
public class OverlyPermissiveMethod extends BytecodeScanningDetector {

    private static Map<Integer, String> DECLARED_ACCESS = new HashMap<>();

    static {
        DECLARED_ACCESS.put(Integer.valueOf(Constants.ACC_PRIVATE), "private");
        DECLARED_ACCESS.put(Integer.valueOf(Constants.ACC_PROTECTED), "protected");
        DECLARED_ACCESS.put(Integer.valueOf(Constants.ACC_PUBLIC), "public");
        DECLARED_ACCESS.put(Integer.valueOf(0), "package private");
    }

    private BugReporter bugReporter;
    private OpcodeStack stack;
    private JavaClass cls;
    private String callingPackage;
    private String callingClass;

    /**
     * constructs a OPM detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public OverlyPermissiveMethod(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            cls = classContext.getJavaClass();
            ClassDescriptor cd = classContext.getClassDescriptor();
            callingClass = cd.getClassName();
            callingPackage = cd.getPackageName();
            stack = new OpcodeStack();
            super.visitClassContext(classContext);
        } finally {
            callingPackage = null;
            stack = null;
        }
    }

    @Override
    public void visitCode(Code obj) {
        Method m = getMethod();
        String methodName = m.getName();
        String sig = m.getSignature();

        if (isAssumedPublic(methodName)) {
            MethodInfo mi = Statistics.getStatistics().getMethodStatistics(cls.getClassName(), methodName, sig);
            mi.addCallingAccess(Constants.ACC_PUBLIC);
        } else {
            if (!hasRuntimeAnnotations(m) && !isGetterSetter(methodName, sig)) {
                MethodInfo mi = Statistics.getStatistics().getMethodStatistics(cls.getClassName(), methodName, sig);
                mi.addCallingAccess(Constants.ACC_PUBLIC);
            }
            stack.resetForMethodEntry(this);
            super.visitCode(obj);
        }
    }

    @Override
    public void sawOpcode(int seen) {
        try {
            stack.precomputation(this);

            switch (seen) {
                case INVOKEVIRTUAL:
                case INVOKEINTERFACE:
                case INVOKESTATIC:
                case INVOKESPECIAL: {
                    String calledClass = getClassConstantOperand();
                    String sig = getSigConstantOperand();
                    MethodInfo mi = Statistics.getStatistics().getMethodStatistics(calledClass, getNameConstantOperand(), sig);
                    if (mi != null) {
                        if (seen == INVOKEINTERFACE) {
                            mi.addCallingAccess(Constants.ACC_PUBLIC);
                        } else {
                            String calledPackage;
                            int slashPos = calledClass.lastIndexOf('/');
                            if (slashPos >= 0) {
                                calledPackage = calledClass.substring(0, slashPos);
                            } else {
                                calledPackage = "";
                            }
                            boolean sameClass = calledClass.equals(callingClass);
                            boolean samePackage = calledPackage.equals(callingPackage);

                            if (sameClass) {
                                mi.addCallingAccess(Constants.ACC_PRIVATE);
                            } else if (samePackage) {
                                mi.addCallingAccess(0);
                            } else {
                                if (seen == INVOKESTATIC) {
                                    mi.addCallingAccess(Constants.ACC_PUBLIC);
                                } else if (isCallingOnThis(sig)) {
                                    mi.addCallingAccess(Constants.ACC_PROTECTED);
                                } else {
                                    mi.addCallingAccess(Constants.ACC_PUBLIC);
                                }
                            }
                        }
                    }
                }
                break;

                case INVOKEDYNAMIC:
                    // smells like a hack. Not sure how to do this better as bcel and findbugs are older than dirt
                    ConstantInvokeDynamic id = (ConstantInvokeDynamic) getConstantRefOperand();

                    BootstrapMethod bm = getBootstrapMethod(id.getBootstrapMethodAttrIndex());
                    if (bm != null) {
                        ConstantPool pool = getConstantPool();
                        ConstantMethodHandle mh = bm.getFirstMethodHandle(pool);
                        if (mh != null) {
                            ConstantCP ref = (ConstantCP) pool.getConstant(mh.getReferenceIndex());
                            ConstantClass cc = (ConstantClass) pool.getConstant(ref.getClassIndex());
                            String clz = ((ConstantUtf8) pool.getConstant(cc.getNameIndex())).getBytes();
                            ConstantNameAndType nameAndType = (ConstantNameAndType) pool.getConstant(ref.getNameAndTypeIndex());
                            String sig = ((ConstantUtf8) pool.getConstant(nameAndType.getSignatureIndex())).getBytes();
                            String name = ((ConstantUtf8) pool.getConstant(nameAndType.getNameIndex())).getBytes();
                            MethodInfo mi = Statistics.getStatistics().getMethodStatistics(clz, name, sig);
                            mi.addCallingAccess(Constants.ACC_PUBLIC);
                        }
                    }

                break;

                default:
                break;
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    private boolean hasRuntimeAnnotations(Method obj) {
        AnnotationEntry[] annotations = obj.getAnnotationEntries();
        if (annotations != null) {
            for (AnnotationEntry entry : annotations) {
                if (entry.isRuntimeVisible()) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isAssumedPublic(String methodName) {
        return (cls.isEnum() && "valueOf".equals(methodName));
    }

    private boolean isGetterSetter(String methodName, String methodSignature) {
        if (methodName.startsWith("get") || methodName.startsWith("set")) {
            int numParameters = SignatureUtils.getNumParameters(methodSignature);
            boolean voidReturn = Values.SIG_VOID.equals(SignatureUtils.getReturnSignature(methodSignature));

            if ((numParameters == 0) && !voidReturn && (methodName.charAt(0) == 'g')) {
                return true;
            }

            if ((numParameters == 1) && voidReturn && (methodName.charAt(0) == 's')) {
                return true;
            }
        }

        return false;
    }

    /**
     * checks to see if an instance method is called on the 'this' object
     *
     * @param sig
     *            the signature of the method called to find the called-on object
     * @return when it is called on this or not
     */
    private boolean isCallingOnThis(String sig) {
        if (getMethod().isStatic()) {
            return false;
        }

        int numParameters = SignatureUtils.getNumParameters(sig);
        if (stack.getStackDepth() <= numParameters) {
            return false;
        }

        OpcodeStack.Item item = stack.getStackItem(numParameters);
        return item.getRegisterNumber() == 0;
    }

    /**
     * after collecting all method calls, build a report of all methods that have been called, but in a way that is less permissive then is defined.
     */
    @Override
    public void report() {
        for (Map.Entry<FQMethod, MethodInfo> entry : Statistics.getStatistics()) {
            MethodInfo mi = entry.getValue();

            int declaredAccess = mi.getDeclaredAccess();
            if ((declaredAccess & Constants.ACC_PRIVATE) != 0) {
                continue;
            }

            if (mi.wasCalledPublicly() || !mi.wasCalled()) {
                continue;
            }

            FQMethod key = entry.getKey();

            String methodName = key.getMethodName();
            if (isGetterSetter(methodName, key.getSignature())) {
                continue;
            }

            if (isOverlyPermissive(declaredAccess) && !isConstrainedByInterface(key)) {
                try {
                    String clsName = key.getClassName();
                    if (!isDerived(Repository.lookupClass(clsName), key)) {

                        BugInstance bi = new BugInstance(this, BugType.OPM_OVERLY_PERMISSIVE_METHOD.name(), LOW_PRIORITY).addClass(clsName).addMethod(clsName,
                                key.getMethodName(), key.getSignature(), (declaredAccess & Constants.ACC_STATIC) != 0);

                        String descr = String.format("- Method declared %s but could be declared %s", getDeclaredAccessValue(declaredAccess),
                                getRequiredAccessValue(mi));
                        bi.addString(descr);

                        bugReporter.reportBug(bi);
                    }
                } catch (ClassNotFoundException cnfe) {
                    bugReporter.reportMissingClass(cnfe);
                }
            }
        }
    }

    private static boolean isOverlyPermissive(int declaredAccess) {
        return (declaredAccess & Constants.ACC_PUBLIC) != 0;
    }

    private boolean isConstrainedByInterface(FQMethod fqMethod) {

        try {
            JavaClass cls = Repository.lookupClass(fqMethod.getClassName());
            if (cls.isInterface()) {
                return true;
            }

            for (JavaClass inf : cls.getAllInterfaces()) {
                for (Method method : inf.getMethods()) {
                    if (method.getName().equals(fqMethod.getMethodName()) && method.getSignature().equals(fqMethod.getSignature())) {
                        return true;
                    }
                }
            }

            return false;
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
            return true;
        }
    }

    /**
     * looks to see if this method described by key is derived from a superclass or interface
     *
     * @param cls
     *            the class that the method is defined in
     * @param key
     *            the information about the method
     * @return whether this method derives from something or not
     */
    private boolean isDerived(JavaClass cls, FQMethod key) {
        try {
            for (JavaClass infCls : cls.getInterfaces()) {
                for (Method infMethod : infCls.getMethods()) {
                    if (key.getMethodName().equals(infMethod.getName())) {
                        if (infMethod.getGenericSignature() != null) {
                            if (SignatureUtils.compareGenericSignature(infMethod.getGenericSignature(), key.getSignature())) {
                                return true;
                            }
                        } else if (infMethod.getSignature().equals(key.getSignature())) {
                            return true;
                        }
                    }
                }
            }

            JavaClass superClass = cls.getSuperClass();
            if ((superClass == null) || Values.DOTTED_JAVA_LANG_OBJECT.equals(superClass.getClassName())) {
                return false;
            }

            for (Method superMethod : superClass.getMethods()) {
                if (key.getMethodName().equals(superMethod.getName())) {
                    if (superMethod.getGenericSignature() != null) {
                        if (SignatureUtils.compareGenericSignature(superMethod.getGenericSignature(), key.getSignature())) {
                            return true;
                        }
                    } else if (superMethod.getSignature().equals(key.getSignature())) {
                        return true;
                    }
                }
            }

            return isDerived(superClass, key);
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
            return true;
        }
    }

    private static String getDeclaredAccessValue(int declaredAccess) {
        return DECLARED_ACCESS.get(Integer.valueOf(declaredAccess & (Constants.ACC_PRIVATE | Constants.ACC_PROTECTED | Constants.ACC_PUBLIC)));
    }

    private static Object getRequiredAccessValue(MethodInfo mi) {
        if (mi.wasCalledProtectedly()) {
            return "protected";
        }
        if (mi.wasCalledPackagely()) {
            return "package private";
        }
        return "private";
    }

    private BootstrapMethod getBootstrapMethod(int bootstrapIndex) {
        for (Attribute a : cls.getAttributes()) {
            if ("BootstrapMethods".equals(a.getName())) {
                if (a instanceof Unknown) {
                    Unknown ua = (Unknown) a;
                    byte[] data = ua.getBytes();
                    ByteBuffer buffer = ByteBuffer.wrap(data, 0, data.length);
                    int numMethods = buffer.getShort();
                    if (bootstrapIndex >= numMethods) {
                        return null;
                    }

                    for (int i = 0; i < numMethods; i++) {
                        BootstrapMethod m = new BootstrapMethod(buffer);
                        if (i == bootstrapIndex) {
                            return m;
                        }
                    }
                } else {
                    throw new RuntimeException(
                            "Incompatible bcel version, apparently bcel has been upgraded to not use 'Unknown' for 'BootstrapMethods', but uses: "
                                    + a.getName());
                }
                return null;
            }
        }

        return null;
    }

    /**
     * represents a bootstrap method
     */
    static class BootstrapMethod {
        int bootstrapMethodRef;
        int[] args;

        public BootstrapMethod(ByteBuffer buffer) {
            bootstrapMethodRef = buffer.getShort();
            int numBootstrapArgs = buffer.getShort();
            args = new int[numBootstrapArgs];
            for (int a = 0; a < numBootstrapArgs; a++) {
                args[a] = buffer.getShort();
            }
        }

        public int getBootstrapMethodRef() {
            return bootstrapMethodRef;
        }

        public int[] getArgs() {
            return args;
        }

        public ConstantMethodHandle getFirstMethodHandle(ConstantPool pool) {
            for (int arg : args) {
                Constant c = pool.getConstant(arg);
                if (c instanceof ConstantMethodHandle) {
                    return ((ConstantMethodHandle) c);
                }
            }

            return null;
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }
}
