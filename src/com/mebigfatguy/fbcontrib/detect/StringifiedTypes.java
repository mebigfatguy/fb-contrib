/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2015 Dave Brosius
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

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for string fields that appear to be built with parsing or calling
 * toString() on another object, or from objects that are fields.
 */
@CustomUserValue
public class StringifiedTypes extends BytecodeScanningDetector {

    private static Map<FQMethod, int[]> COLLECTION_PARMS = new HashMap<FQMethod, int[]>();

    static {
        int[] parm0 = new int[] { 0 };
        int[] parm0N1 = new int[] { -1, 0 };
        int[] parm01N1 = new int[] { -1, 0, 1 };

        COLLECTION_PARMS.put(new FQMethod("java/util/List", "contains", "(Ljava/lang/Object;)Z"), parm0);
        COLLECTION_PARMS.put(new FQMethod("java/util/List", "add", "(Ljava/lang/Object;)Z"), parm0);
        COLLECTION_PARMS.put(new FQMethod("java/util/List", "remove", "(Ljava/lang/Object;)Z"), parm0);
        COLLECTION_PARMS.put(new FQMethod("java/util/List", "set", "(ILjava/lang/Object;)Ljava/lang/Object;"), parm0N1);
        COLLECTION_PARMS.put(new FQMethod("java/util/List", "add", "(ILjava/lang/Object;)V"), parm0);
        COLLECTION_PARMS.put(new FQMethod("java/util/List", "indexOf", "(Ljava/lang/Object;)I"), parm0);
        COLLECTION_PARMS.put(new FQMethod("java/util/List", "lastIndexOf", "(Ljava/lang/Object;)I"), parm0);

        COLLECTION_PARMS.put(new FQMethod("java/util/Set", "contains", "(Ljava/lang/Object;)Z"), parm0);
        COLLECTION_PARMS.put(new FQMethod("java/util/Set", "add", "(Ljava/lang/Object;)Z"), parm0);
        COLLECTION_PARMS.put(new FQMethod("java/util/Set", "remove", "(Ljava/lang/Object;)Z"), parm0);

        COLLECTION_PARMS.put(new FQMethod("java/util/Map", "containsKey", "(Ljava/lang/Object;)Z"), parm0);
        COLLECTION_PARMS.put(new FQMethod("java/util/Map", "containsValue", "(Ljava/lang/Object;)Z"), parm0);
        COLLECTION_PARMS.put(new FQMethod("java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;"), parm0N1);
        COLLECTION_PARMS.put(new FQMethod("java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"), parm01N1);
        COLLECTION_PARMS.put(new FQMethod("java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"), parm01N1);
        COLLECTION_PARMS.put(new FQMethod("java/util/Map", "remove", "(Ljava/lang/Object;)Ljava/lang/Object;"), parm0N1);
    }

    private static final Map<String, Integer> STRING_PARSE_METHODS = new HashMap<String, Integer>();

    static {
        STRING_PARSE_METHODS.put("indexOf", Integer.valueOf(NORMAL_PRIORITY));
        STRING_PARSE_METHODS.put("lastIndexOf", Integer.valueOf(NORMAL_PRIORITY));
        STRING_PARSE_METHODS.put("substring", Integer.valueOf(NORMAL_PRIORITY));
        STRING_PARSE_METHODS.put("split", Integer.valueOf(NORMAL_PRIORITY));
        STRING_PARSE_METHODS.put("startsWith", Integer.valueOf(LOW_PRIORITY));
        STRING_PARSE_METHODS.put("endsWith", Integer.valueOf(LOW_PRIORITY));
    }

    private static final String TO_STRING = "toString";
    private static final String FROM_FIELD = "FROM_FIELD";

    private BugReporter bugReporter;
    private OpcodeStack stack;
    private BitSet toStringStringBuilders;

    public StringifiedTypes(BugReporter reporter) {
        bugReporter = reporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            toStringStringBuilders = new BitSet();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            toStringStringBuilders = null;
        }
    }

    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        toStringStringBuilders.clear();
        super.visitCode(obj);
    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "SF_SWITCH_NO_DEFAULT",
        justification = "We don't need or want to handle every opcode"
    )
    @Override
    public void sawOpcode(int seen) {
        String userValue = null;
        int[] checkParms = null;
        try {
            stack.precomputation(this);
            switch (seen) {
            case INVOKEVIRTUAL: {
                String clsName = getClassConstantOperand();
                String methodName = getNameConstantOperand();
                String sig = getSigConstantOperand();
                boolean isStringBuilder = "java/lang/StringBuilder".equals(clsName) || "java/lang/StringBuffer".equals(clsName);

                if (TO_STRING.equals(methodName) && "()Ljava/lang/String;".equals(sig)) {
                    if (isStringBuilder) {
                        if (stack.getStackDepth() > 0) {
                            OpcodeStack.Item item = stack.getStackItem(0);
                            userValue = (String) item.getUserValue();
                        }
                    } else {
                        userValue = TO_STRING;
                    }
                } else if (isStringBuilder) {
                    if ("append".equals(methodName)) {
                        if (stack.getStackDepth() > 0) {
                            OpcodeStack.Item item = stack.getStackItem(0);
                            userValue = (String) item.getUserValue();
                            if (userValue == null) {
                                if (!"Ljava/lang/String;".equals(item.getSignature())) {
                                    userValue = TO_STRING;
                                    if (stack.getStackDepth() > 1) {
                                        item = stack.getStackItem(1);
                                        int reg = item.getRegisterNumber();
                                        if (reg >= 0) {
                                            toStringStringBuilders.set(reg);
                                        }
                                    }
                                }
                            }
                        }
                    } else if ("setLength".equals(methodName)) {
                        if (stack.getStackDepth() > 1) {
                            OpcodeStack.Item item = stack.getStackItem(1);
                            item.setUserValue(null);
                            int reg = item.getRegisterNumber();
                            if (reg >= 0) {
                                toStringStringBuilders.clear(reg);
                            }
                        }
                    }
                } else if ("java/lang/String".equals(clsName)) {
                    Integer priority = STRING_PARSE_METHODS.get(methodName);
                    if (priority != null) {
                        Type[] parmTypes = Type.getArgumentTypes(sig);
                        if (stack.getStackDepth() > parmTypes.length) {
                            OpcodeStack.Item item = stack.getStackItem(parmTypes.length);
                            if ((item.getXField() != null) || FROM_FIELD.equals(item.getUserValue())) {
                                bugReporter.reportBug(new BugInstance(this, BugType.STT_STRING_PARSING_A_FIELD.name(), priority.intValue()).addClass(this)
                                        .addMethod(this).addSourceLine(this));
                            }
                        }
                    }
                }
            }
                break;

            case INVOKEINTERFACE: {
                String clsName = getClassConstantOperand();
                String methodName = getNameConstantOperand();
                String sig = getSigConstantOperand();

                Type[] parmTypes = Type.getArgumentTypes(sig);
                if (stack.getStackDepth() > parmTypes.length) {
                    FQMethod cm = new FQMethod(clsName, methodName, sig);
                    checkParms = COLLECTION_PARMS.get(cm);
                    if (checkParms != null) {
                        OpcodeStack.Item item = stack.getStackItem(parmTypes.length);
                        if (item.getXField() != null) {
                            for (int parm : checkParms) {
                                if (parm >= 0) {
                                    item = stack.getStackItem(parm);
                                    if (TO_STRING.equals(item.getUserValue())) {
                                        bugReporter.reportBug(new BugInstance(this, BugType.STT_TOSTRING_STORED_IN_FIELD.name(), NORMAL_PRIORITY).addClass(this)
                                                .addMethod(this).addSourceLine(this));
                                        break;
                                    }
                                }
                            }
                        } else {
                            checkParms = null;
                        }
                    }
                }
            }
                break;

            case PUTFIELD:
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    if ("toString".equals(item.getUserValue())) {
                        bugReporter.reportBug(new BugInstance(this, BugType.STT_TOSTRING_STORED_IN_FIELD.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                                .addSourceLine(this));
                    }
                }
                break;

            case ALOAD:
            case ALOAD_0:
            case ALOAD_1:
            case ALOAD_2:
            case ALOAD_3: {
                int reg = RegisterUtils.getALoadReg(this, seen);
                if (toStringStringBuilders.get(reg)) {
                    userValue = TO_STRING;
                }
            }
                break;

            case ASTORE:
            case ASTORE_0:
            case ASTORE_1:
            case ASTORE_2:
            case ASTORE_3: {
                int reg = RegisterUtils.getAStoreReg(this, seen);
                toStringStringBuilders.clear(reg);
            }
                break;

            }
        } finally {
            stack.sawOpcode(this, seen);
            if (userValue != null) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    item.setUserValue(userValue);
                }
            }
            if ((checkParms != null) && (checkParms[0] == -1)) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    item.setUserValue(FROM_FIELD);
                }
            }
        }
    }
}
