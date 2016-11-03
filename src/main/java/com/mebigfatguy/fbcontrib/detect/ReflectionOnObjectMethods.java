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
import java.util.Set;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for method calls through reflection on methods found in java.lang.Object. As these methods are always available, there's no reason to do this.
 */
@CustomUserValue
public class ReflectionOnObjectMethods extends BytecodeScanningDetector {

    public static final String SIG_STRING_AND_CLASS_ARRAY_TO_METHOD = new SignatureBuilder().withParamTypes(Values.SLASHED_JAVA_LANG_STRING, SignatureUtils.toArraySignature(Values.SLASHED_JAVA_LANG_CLASS))
        .withReturnType(Method.class).toString();

    private static final Set<String> objectSigs = UnmodifiableSet.create(
            // "clone()", // clone is declared protected
            "equals(Ljava/lang/Object;)", "finalize()", "getClass()", "hashCode()", "notify()", "notifyAll()", "toString()", "wait", "wait(J)", "wait(JI)");

    private final BugReporter bugReporter;
    private OpcodeStack stack;
    private Map<Integer, String[]> localClassTypes;
    private Map<String, String[]> fieldClassTypes;
    /** This object is not thread-safe, but can be reused, provided that all necessary fields are overwritten. */
    private final SignatureBuilder sigWithoutReturn = new SignatureBuilder().withoutReturnType();

    /**
     * constructs a ROOM detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public ReflectionOnObjectMethods(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to create the stack and local and field maps for Class arrays to be used for getting the reflection method
     *
     * @param classContext
     *            the context object of the currently parse class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            localClassTypes = new HashMap<Integer, String[]>();
            fieldClassTypes = new HashMap<String, String[]>();
            JavaClass cls = classContext.getJavaClass();
            Method staticInit = findStaticInitializer(cls);
            if (staticInit != null) {
                setupVisitorForClass(cls);
                doVisitMethod(staticInit);
            }

            super.visitClassContext(classContext);
        } finally {
            stack = null;
            localClassTypes = null;
            fieldClassTypes = null;
        }
    }

    /**
     * implements the visitor to reset the opcode stack and clear the local variable map@
     *
     * @param obj
     *            the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        localClassTypes.clear();
        super.visitCode(obj);
    }

    /**
     * implements the visitor to look for calls that invoke a method through reflection where the method is defined in java.lang.Object
     *
     * @param seen
     *            the currently parsed opcode
     */
    @Override
    public void sawOpcode(int seen) {
        Integer arraySize = null;
        String[] loadedTypes = null;

        try {
            stack.precomputation(this);

            switch (seen) {
                case ANEWARRAY: {
                    if (Values.SLASHED_JAVA_LANG_CLASS.equals(getClassConstantOperand()) && (stack.getStackDepth() >= 1)) {
                        OpcodeStack.Item item = stack.getStackItem(0);
                        arraySize = (Integer) item.getConstant();
                    }
                }
                break;

                case AASTORE: {
                    if (stack.getStackDepth() >= 3) {
                        OpcodeStack.Item arrayItem = stack.getStackItem(2);
                        String[] arrayTypes = (String[]) arrayItem.getUserValue();
                        if (arrayTypes != null) {
                            OpcodeStack.Item valueItem = stack.getStackItem(0);
                            String type = (String) valueItem.getConstant();
                            if (type != null) {
                                OpcodeStack.Item indexItem = stack.getStackItem(1);
                                Integer index = (Integer) indexItem.getConstant();
                                if (index != null) {
                                    arrayTypes[index.intValue()] = type;
                                }
                            }
                        }
                    }
                }
                break;

                case PUTFIELD:
                case PUTSTATIC: {
                    String name = getNameConstantOperand();
                    if (stack.getStackDepth() >= 1) {
                        OpcodeStack.Item item = stack.getStackItem(0);
                        String[] arrayTypes = (String[]) item.getUserValue();
                        if (arrayTypes != null) {
                            fieldClassTypes.put(name, arrayTypes);
                            return;
                        }
                    }
                    fieldClassTypes.remove(name);
                }
                break;

                case GETFIELD:
                case GETSTATIC: {
                    String name = getNameConstantOperand();
                    loadedTypes = fieldClassTypes.get(name);
                }
                break;

                case ASTORE_0:
                case ASTORE_1:
                case ASTORE_2:
                case ASTORE_3:
                case ASTORE: {
                    Integer reg = Integer.valueOf(RegisterUtils.getAStoreReg(this, seen));
                    if (stack.getStackDepth() >= 1) {
                        OpcodeStack.Item item = stack.getStackItem(0);
                        String[] arrayTypes = (String[]) item.getUserValue();
                        if (arrayTypes != null) {
                            localClassTypes.put(reg, arrayTypes);
                            return;
                        }
                    }
                    localClassTypes.remove(reg);
                }
                break;

                case ALOAD_0:
                case ALOAD_1:
                case ALOAD_2:
                case ALOAD_3:
                case ALOAD: {
                    int reg = RegisterUtils.getAStoreReg(this, seen);
                    loadedTypes = localClassTypes.get(Integer.valueOf(reg));
                }
                break;

                case INVOKEVIRTUAL: {
                    String cls = getClassConstantOperand();
                    if (Values.SLASHED_JAVA_LANG_CLASS.equals(cls)) {
                        String method = getNameConstantOperand();
                        if ("getMethod".equals(method)) {
                            String sig = getSigConstantOperand();
                            if (SIG_STRING_AND_CLASS_ARRAY_TO_METHOD.equals(sig) && (stack.getStackDepth() >= 2)) {
                                OpcodeStack.Item clsArgs = stack.getStackItem(0);
                                String[] arrayTypes = (String[]) clsArgs.getUserValue();
                                if ((arrayTypes != null) || clsArgs.isNull()) {
                                    OpcodeStack.Item methodItem = stack.getStackItem(1);
                                    String methodName = (String) methodItem.getConstant();
                                    if (methodName != null) {
                                        String reflectionSig = sigWithoutReturn.withMethodName(methodName).withParamTypes(arrayTypes).build();
                                        if (objectSigs.contains(reflectionSig)) {
                                            loadedTypes = (arrayTypes == null) ? new String[0] : arrayTypes;
                                        }
                                    }
                                }
                            }
                        }
                    } else if ("java/lang/reflect/Method".equals(cls)) {
                        String method = getNameConstantOperand();
                        if ("invoke".equals(method) && (stack.getStackDepth() >= 3)) {
                            OpcodeStack.Item methodItem = stack.getStackItem(2);
                            String[] arrayTypes = (String[]) methodItem.getUserValue();
                            if (arrayTypes != null) {
                                bugReporter.reportBug(new BugInstance(this, BugType.ROOM_REFLECTION_ON_OBJECT_METHODS.name(), NORMAL_PRIORITY).addClass(this)
                                        .addMethod(this).addSourceLine(this));
                            }
                        }
                    }
                }
                break;

                default:
                break;
            }
        } finally {
            TernaryPatcher.pre(stack, seen);
            stack.sawOpcode(this, seen);
            TernaryPatcher.post(stack, seen);

            if (stack.getStackDepth() >= 1) {
                if (arraySize != null) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    item.setUserValue(new String[arraySize.intValue()]);
                } else if (loadedTypes != null) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    item.setUserValue(loadedTypes);
                }
            }
        }
    }

    /**
     * finds the method that is the static initializer for the class
     *
     * @param cls
     *            the class to find the initializer for
     *
     * @return the Method of the static initializer or null if this class has none
     */
    private static Method findStaticInitializer(JavaClass cls) {
        Method[] methods = cls.getMethods();
        for (Method m : methods) {
            if (Values.STATIC_INITIALIZER.equals(m.getName())) {
                return m;
            }
        }

        return null;
    }
}
