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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.StopOpcodeParsingException;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XField;

/**
 * looks for uses of sets or maps where the key is an enum. In these cases, it is more efficient to use EnumSet or EnumMap. It is a jdk1.5 only detector.
 */
@CustomUserValue
public class UseEnumCollections extends BytecodeScanningDetector {
    private static final Set<String> nonEnumCollections = UnmodifiableSet.create("Ljava/util/HashSet;", "Ljava/util/HashMap;", "Ljava/util/TreeMap;",
            "Ljava/util/ConcurrentHashMap;", "Ljava/util/IdentityHashMap;", "Ljava/util/WeakHashMap;");

    private final BugReporter bugReporter;
    private OpcodeStack stack;
    private Set<String> checkedFields;
    private Map<Integer, Boolean> enumRegs;
    private Map<String, Boolean> enumFields;

    /**
     * constructs a UEC detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public UseEnumCollections(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to check that the class is greater or equal than 1.5, and set and clear the stack
     *
     * @param classContext
     *            the context object for the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            JavaClass cls = classContext.getJavaClass();
            if (cls.getMajor() >= Const.MAJOR_1_5) {
                stack = new OpcodeStack();
                checkedFields = new HashSet<>();
                enumRegs = new HashMap<>();
                enumFields = new HashMap<>();
                super.visitClassContext(classContext);
            }
        } finally {
            stack = null;
            checkedFields = null;
            enumRegs = null;
            enumFields = null;
        }
    }

    /**
     * implements the visitor to reset the state
     *
     * @param obj
     *            the context object for the currently parsed method
     */
    @Override
    public void visitMethod(Method obj) {
        stack.resetForMethodEntry(this);
        enumRegs.clear();
        super.visitMethod(obj);
    }

    @Override
    public void visitCode(Code obj) {
        try {
            super.visitCode(obj);
        } catch (StopOpcodeParsingException e) {
            // method is already reported
        }
    }

    @Override
    public void sawOpcode(int seen) {
        Boolean sawEnumCollectionCreation = null; // true - enum, false -
                                                  // nonenum
        try {

            stack.precomputation(this);

            if (seen == Const.INVOKESTATIC) {
                String clsName = getClassConstantOperand();
                String signature = getSigConstantOperand();
                if ("java/util/EnumSet".equals(clsName) && signature.endsWith(")Ljava/util/EnumSet;")) {
                    sawEnumCollectionCreation = Boolean.TRUE;
                }
            } else if (seen == Const.INVOKESPECIAL) {
                String clsName = getClassConstantOperand();
                String methodName = getNameConstantOperand();
                if ("java/util/EnumMap".equals(clsName) && Values.CONSTRUCTOR.equals(methodName)) {
                    sawEnumCollectionCreation = Boolean.TRUE;
                } else if (clsName.startsWith("java/util/") && (clsName.endsWith("Map") || clsName.endsWith("Set"))) {
                    sawEnumCollectionCreation = Boolean.FALSE;
                }
            } else if (OpcodeUtils.isAStore(seen)) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item itm = stack.getStackItem(0);
                    Integer reg = Integer.valueOf(RegisterUtils.getAStoreReg(this, seen));
                    Boolean uv = (Boolean) itm.getUserValue();
                    if (uv == null) {
                        enumRegs.remove(reg);
                    } else {
                        enumRegs.put(reg, uv);
                    }
                }
            } else if ((seen == Const.ALOAD) || OpcodeUtils.isALoad(seen)) {
                Integer reg = Integer.valueOf(RegisterUtils.getALoadReg(this, seen));
                sawEnumCollectionCreation = enumRegs.get(reg);
            } else if (seen == Const.PUTFIELD) {
                if (stack.getStackDepth() > 0) {
                    String fieldName = getNameConstantOperand();
                    OpcodeStack.Item itm = stack.getStackItem(0);
                    Boolean uv = (Boolean) itm.getUserValue();
                    if (uv == null) {
                        enumFields.remove(fieldName);
                    } else {
                        enumFields.put(fieldName, uv);
                    }
                }
            } else if (seen == Const.GETFIELD) {
                String fieldName = getNameConstantOperand();
                sawEnumCollectionCreation = enumFields.get(fieldName);
            } else if (seen == Const.INVOKEINTERFACE) {
                boolean bug = false;
                String clsName = getClassConstantOperand();
                String methodName = getNameConstantOperand();
                String signature = getSigConstantOperand();
                if (Values.SLASHED_JAVA_UTIL_MAP.equals(clsName) && "put".equals(methodName) && SignatureBuilder.SIG_TWO_OBJECTS_TO_OBJECT.equals(signature)) {
                    bug = isEnum(1) && !isEnumCollection(2) && !alreadyReported(2);
                } else if (Values.SLASHED_JAVA_UTIL_SET.equals(clsName) && "add".equals(methodName)
                        && SignatureBuilder.SIG_OBJECT_TO_BOOLEAN.equals(signature)) {
                    bug = isEnum(0) && !isEnumCollection(1) && !alreadyReported(1);
                }

                if (bug) {
                    bugReporter.reportBug(
                            new BugInstance(this, BugType.UEC_USE_ENUM_COLLECTIONS.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
                    throw new StopOpcodeParsingException();
                }
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        } finally {
            TernaryPatcher.pre(stack, seen);
            stack.sawOpcode(this, seen);
            TernaryPatcher.post(stack, seen);
            if ((sawEnumCollectionCreation != null) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item itm = stack.getStackItem(0);
                itm.setUserValue(sawEnumCollectionCreation);
            }
        }
    }

    /**
     * returns whether the item at the stackPos location on the stack is an enum, and doesn't implement any interfaces
     *
     * @param stackPos
     *            the position on the opstack to check
     *
     * @return whether the class is an enum
     * @throws ClassNotFoundException
     *             if the class can not be loaded
     */
    private boolean isEnum(int stackPos) throws ClassNotFoundException {
        if (stack.getStackDepth() <= stackPos) {
            return false;
        }

        OpcodeStack.Item item = stack.getStackItem(stackPos);
        if (!item.getSignature().startsWith(Values.SIG_QUALIFIED_CLASS_PREFIX)) {
            return false;
        }

        JavaClass cls = item.getJavaClass();
        if ((cls == null) || !cls.isEnum()) {
            return false;
        }

        // If the cls implements any interface, it's possible the collection
        // is based on that interface, so ignore
        return cls.getInterfaces().length == 0;
    }

    /**
     * returns whether the item at the stackpos location is an instance of an EnumSet or EnumMap
     *
     * @param stackPos
     *            the position on the opstack to check
     *
     * @return whether the class is an EnumSet or EnumMap
     */
    private boolean isEnumCollection(int stackPos) {
        if (stack.getStackDepth() <= stackPos) {
            return false;
        }

        OpcodeStack.Item item = stack.getStackItem(stackPos);

        Boolean userValue = (Boolean) item.getUserValue();
        if (userValue != null) {
            return userValue.booleanValue();
        }

        String realClass = item.getSignature();
        if ("Ljava/util/EnumSet;".equals(realClass) || "Ljava/util/EnumMap;".equals(realClass)) {
            return true;
        }

        // if can't tell here, then return true
        return !nonEnumCollections.contains(realClass);
    }

    /**
     * returns whether the collection has already been reported on
     *
     * @param stackPos
     *            the position on the opstack to check
     *
     * @return whether the collection has already been reported.
     */
    private boolean alreadyReported(int stackPos) {
        if (stack.getStackDepth() <= stackPos) {
            return false;
        }

        OpcodeStack.Item item = stack.getStackItem(stackPos);
        XField field = item.getXField();
        if (field == null) {
            return false;
        }

        String fieldName = field.getName();
        boolean checked = checkedFields.contains(fieldName);
        checkedFields.add(fieldName);
        return checked;
    }
}
