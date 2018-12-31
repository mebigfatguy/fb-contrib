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

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

import org.apache.bcel.classfile.Code;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XMethod;

/**
 * looks for string fields that appear to be built with parsing or calling toString() on another object, or from objects that are fields.
 */
@CustomUserValue
public class StringifiedTypes extends BytecodeScanningDetector {

    private static Map<FQMethod, int[]> COLLECTION_PARMS = new HashMap<>();

    static {
        int[] parm0 = new int[] { 0 };
        int[] parm0N1 = new int[] { -1, 0 };
        int[] parm01N1 = new int[] { -1, 0, 1 };

        String objectToInt = new SignatureBuilder().withParamTypes(Values.SLASHED_JAVA_LANG_OBJECT).withReturnType(Values.SIG_PRIMITIVE_INT).toString();

        COLLECTION_PARMS.put(new FQMethod(Values.SLASHED_JAVA_UTIL_LIST, "contains", SignatureBuilder.SIG_OBJECT_TO_BOOLEAN), parm0);
        COLLECTION_PARMS.put(new FQMethod(Values.SLASHED_JAVA_UTIL_LIST, "add", SignatureBuilder.SIG_OBJECT_TO_BOOLEAN), parm0);
        COLLECTION_PARMS.put(new FQMethod(Values.SLASHED_JAVA_UTIL_LIST, "remove", SignatureBuilder.SIG_OBJECT_TO_BOOLEAN), parm0);
        COLLECTION_PARMS.put(
                new FQMethod(Values.SLASHED_JAVA_UTIL_LIST, "set", new SignatureBuilder()
                        .withParamTypes(Values.SIG_PRIMITIVE_INT, Values.SLASHED_JAVA_LANG_OBJECT).withReturnType(Values.SLASHED_JAVA_LANG_OBJECT).toString()),
                parm0N1);
        COLLECTION_PARMS.put(new FQMethod(Values.SLASHED_JAVA_UTIL_LIST, "add",
                new SignatureBuilder().withParamTypes(Values.SIG_PRIMITIVE_INT, Values.SLASHED_JAVA_LANG_OBJECT).toString()), parm0);
        COLLECTION_PARMS.put(new FQMethod(Values.SLASHED_JAVA_UTIL_LIST, "indexOf", objectToInt), parm0);
        COLLECTION_PARMS.put(new FQMethod(Values.SLASHED_JAVA_UTIL_LIST, "lastIndexOf", objectToInt), parm0);

        COLLECTION_PARMS.put(new FQMethod(Values.SLASHED_JAVA_UTIL_SET, "contains", SignatureBuilder.SIG_OBJECT_TO_BOOLEAN), parm0);
        COLLECTION_PARMS.put(new FQMethod(Values.SLASHED_JAVA_UTIL_SET, "add", SignatureBuilder.SIG_OBJECT_TO_BOOLEAN), parm0);
        COLLECTION_PARMS.put(new FQMethod(Values.SLASHED_JAVA_UTIL_SET, "remove", SignatureBuilder.SIG_OBJECT_TO_BOOLEAN), parm0);

        COLLECTION_PARMS.put(new FQMethod(Values.SLASHED_JAVA_UTIL_MAP, "containsKey", SignatureBuilder.SIG_OBJECT_TO_BOOLEAN), parm0);
        COLLECTION_PARMS.put(new FQMethod(Values.SLASHED_JAVA_UTIL_MAP, "containsValue", SignatureBuilder.SIG_OBJECT_TO_BOOLEAN), parm0);
        COLLECTION_PARMS.put(new FQMethod(Values.SLASHED_JAVA_UTIL_MAP, "get", SignatureBuilder.SIG_OBJECT_TO_OBJECT), parm0N1);
        COLLECTION_PARMS.put(new FQMethod(Values.SLASHED_JAVA_UTIL_MAP, "put", SignatureBuilder.SIG_TWO_OBJECTS_TO_OBJECT), parm01N1);
        COLLECTION_PARMS.put(new FQMethod(Values.SLASHED_JAVA_UTIL_MAP, "remove", SignatureBuilder.SIG_OBJECT_TO_OBJECT), parm0N1);
    }

    private static final Map<String, Integer> STRING_PARSE_METHODS = new HashMap<>();

    static {
        STRING_PARSE_METHODS.put("indexOf", Values.NORMAL_BUG_PRIORITY);
        STRING_PARSE_METHODS.put("lastIndexOf", Values.NORMAL_BUG_PRIORITY);
        STRING_PARSE_METHODS.put("substring", Values.NORMAL_BUG_PRIORITY);
        STRING_PARSE_METHODS.put("split", Values.NORMAL_BUG_PRIORITY);
        STRING_PARSE_METHODS.put("startsWith", Values.LOW_BUG_PRIORITY);
        STRING_PARSE_METHODS.put("endsWith", Values.LOW_BUG_PRIORITY);
    }
    private static final String FROM_FIELD = "FROM_FIELD";

    private static final FQMethod MAP_PUT = new FQMethod(Values.SLASHED_JAVA_UTIL_MAP, "put", SignatureBuilder.SIG_TWO_OBJECTS_TO_OBJECT);

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

    @Override
    public void sawOpcode(int seen) {
        String userValue = null;
        int[] checkParms = null;
        try {
            stack.precomputation(this);
            int stackDepth = stack.getStackDepth();
            switch (seen) {
                case INVOKEVIRTUAL: {
                    String clsName = getClassConstantOperand();
                    String methodName = getNameConstantOperand();
                    String sig = getSigConstantOperand();
                    boolean isStringBuilder = SignatureUtils.isPlainStringConvertableClass(clsName);

                    if (Values.TOSTRING.equals(methodName) && SignatureBuilder.SIG_VOID_TO_STRING.equals(sig)) {
                        if (isStringBuilder) {
                            if (stackDepth > 0) {
                                OpcodeStack.Item item = stack.getStackItem(0);
                                userValue = (String) item.getUserValue();
                            }
                        } else {
                            userValue = Values.TOSTRING;
                        }
                    } else if (isStringBuilder) {
                        if ("append".equals(methodName)) {
                            if (stackDepth > 0) {
                                OpcodeStack.Item item = stack.getStackItem(0);
                                userValue = (String) item.getUserValue();
                                if ((userValue == null) && !Values.SIG_JAVA_LANG_STRING.equals(item.getSignature())) {
                                    userValue = Values.TOSTRING;
                                    if (stackDepth > 1) {
                                        item = stack.getStackItem(1);
                                        int reg = item.getRegisterNumber();
                                        if (reg >= 0) {
                                            toStringStringBuilders.set(reg);
                                        }
                                    }
                                }
                            }
                        } else if ((stackDepth > 1) && "setLength".equals(methodName)) {
                            OpcodeStack.Item item = stack.getStackItem(1);
                            item.setUserValue(null);
                            int reg = item.getRegisterNumber();
                            if (reg >= 0) {
                                toStringStringBuilders.clear(reg);
                            }
                        }
                    } else if (Values.SLASHED_JAVA_LANG_STRING.equals(clsName)) {
                        Integer priority = STRING_PARSE_METHODS.get(methodName);
                        if (priority != null) {
                            int numParameters = SignatureUtils.getNumParameters(sig);
                            if (stackDepth > numParameters) {
                                OpcodeStack.Item item = stack.getStackItem(numParameters);
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

                    int numParameters = SignatureUtils.getNumParameters(sig);
                    if (stackDepth > numParameters) {
                        FQMethod cm = new FQMethod(clsName, methodName, sig);
                        checkParms = COLLECTION_PARMS.get(cm);
                        if (checkParms != null) {
                            OpcodeStack.Item item = stack.getStackItem(numParameters);
                            if (item.getXField() == null) {
                                if (MAP_PUT.equals(cm)) {
                                    OpcodeStack.Item itm = stack.getStackItem(1);
                                    XMethod xm = itm.getReturnValueOf();
                                    if (xm != null) {
                                        if (Values.DOTTED_JAVA_LANG_STRINGBUILDER.equals(xm.getClassName())) {
                                            bugReporter.reportBug(new BugInstance(this, BugType.STT_TOSTRING_MAP_KEYING.name(), NORMAL_PRIORITY).addClass(this)
                                                    .addMethod(this).addSourceLine(this));
                                        }
                                    }
                                }
                            } else {
                                for (int parm : checkParms) {
                                    if ((parm >= 0) && Values.TOSTRING.equals(stack.getStackItem(parm).getUserValue())) {
                                        bugReporter.reportBug(new BugInstance(this, BugType.STT_TOSTRING_STORED_IN_FIELD.name(), NORMAL_PRIORITY).addClass(this)
                                                .addMethod(this).addSourceLine(this));
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
                break;

                case PUTFIELD:
                    if (stackDepth > 0) {
                        OpcodeStack.Item item = stack.getStackItem(0);
                        if (Values.TOSTRING.equals(item.getUserValue())) {
                            bugReporter.reportBug(new BugInstance(this, BugType.STT_TOSTRING_STORED_IN_FIELD.name(), NORMAL_PRIORITY).addClass(this)
                                    .addMethod(this).addSourceLine(this));
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
                        userValue = Values.TOSTRING;
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

                default:
                break;
            }
        } finally {
            stack.sawOpcode(this, seen);
            if ((userValue != null) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item item = stack.getStackItem(0);
                item.setUserValue(userValue);
            }
            if ((checkParms != null) && (checkParms[0] == -1) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item item = stack.getStackItem(0);
                item.setUserValue(FROM_FIELD);
            }
        }
    }
}
