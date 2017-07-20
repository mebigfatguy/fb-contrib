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

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.bcel.OpcodeStackDetector;

/**
 * Looks for methods that pass a primitive wrapper class object, to the same classes Constructor.
 */
public class NeedlessAutoboxing extends OpcodeStackDetector {
    enum State {
        SEEN_NOTHING, SEEN_VALUE, SEEN_VALUEOFSTRING, SEEN_PARSE, SEEN_CTOR, SEEN_VALUEOFPRIMITIVE, SEEN_ICONST, SEEN_GETSTATIC
    }

    private static final Map<String, BoxParms> boxClasses = new HashMap<>();

    private static final Map<String, String> parseClasses = new HashMap<>();

    private BugReporter bugReporter;
    private State state;
    private String boxClass;
    private BitSet ternaryPCs;

    static {
        addBoxClass(boxClasses, Values.SLASHED_JAVA_LANG_BOOLEAN, "boolean", Values.SIG_PRIMITIVE_BOOLEAN);
        addBoxClass(boxClasses, Values.SLASHED_JAVA_LANG_CHARACTER, "char", Values.SIG_PRIMITIVE_CHAR);
        addBoxClass(boxClasses, Values.SLASHED_JAVA_LANG_BYTE, "byte", Values.SIG_PRIMITIVE_BYTE);
        addBoxClass(boxClasses, Values.SLASHED_JAVA_LANG_SHORT, "short", Values.SIG_PRIMITIVE_SHORT);
        addBoxClass(boxClasses, Values.SLASHED_JAVA_LANG_INTEGER, "int", Values.SIG_PRIMITIVE_INT);
        addBoxClass(boxClasses, Values.SLASHED_JAVA_LANG_LONG, "long", Values.SIG_PRIMITIVE_LONG);
        addBoxClass(boxClasses, Values.SLASHED_JAVA_LANG_FLOAT, "float", Values.SIG_PRIMITIVE_FLOAT);
        addBoxClass(boxClasses, Values.SLASHED_JAVA_LANG_DOUBLE, "double", Values.SIG_PRIMITIVE_DOUBLE);
    }

    private static void addBoxClass(Map<String, BoxParms> map, String slashedClass, String primitiveName, String primitiveSig) {
        map.put(slashedClass,
                new BoxParms(new SignatureBuilder().withMethodName(primitiveName + "Value").withReturnType(primitiveSig).toString(),
                        new SignatureBuilder().withParamTypes(primitiveSig).toString(),
                        new SignatureBuilder().withParamTypes(primitiveSig).withReturnType(slashedClass).toString()));
    }

    static {
        addParseClass(parseClasses, Values.SLASHED_JAVA_LANG_BOOLEAN, "boolean", Values.SIG_PRIMITIVE_BOOLEAN);
        addParseClass(parseClasses, Values.SLASHED_JAVA_LANG_CHARACTER, "char", Values.SIG_PRIMITIVE_CHAR);
        addParseClass(parseClasses, Values.SLASHED_JAVA_LANG_BYTE, "byte", Values.SIG_PRIMITIVE_BYTE);
        addParseClass(parseClasses, Values.SLASHED_JAVA_LANG_SHORT, "short", Values.SIG_PRIMITIVE_SHORT);
        addParseClass(parseClasses, Values.SLASHED_JAVA_LANG_INTEGER, "int", Values.SIG_PRIMITIVE_INT);
        addParseClass(parseClasses, Values.SLASHED_JAVA_LANG_LONG, "long", Values.SIG_PRIMITIVE_LONG);
        addParseClass(parseClasses, Values.SLASHED_JAVA_LANG_FLOAT, "float", Values.SIG_PRIMITIVE_DOUBLE);
        addParseClass(parseClasses, Values.SLASHED_JAVA_LANG_DOUBLE, "double", Values.SIG_PRIMITIVE_DOUBLE);
    }

    private static void addParseClass(Map<String, String> map, String slashedClass, String primitiveName, String primitiveSig) {
        map.put(slashedClass, new SignatureBuilder().withMethodName("parse" + Character.toUpperCase(primitiveName.charAt(0)) + primitiveName.substring(1))
                .withParamTypes(Values.SLASHED_JAVA_LANG_STRING).withReturnType(primitiveSig).toString());
    }

    /**
     * constructs a NAB detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public NeedlessAutoboxing(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            ternaryPCs = new BitSet();
            super.visitClassContext(classContext);
        } finally {
            ternaryPCs = null;
        }
    }

    @Override
    public void visitMethod(Method obj) {
        state = State.SEEN_NOTHING;
        ternaryPCs.clear();
    }

    @Override
    public void sawOpcode(int seen) {

        if (ternaryPCs.get(getPC())) {
            ternaryPCs.clear(getPC());
            state = State.SEEN_NOTHING;
            return;
        }

        switch (state) {
            case SEEN_NOTHING:
                sawOpcodeAfterNothing(seen);
            break;

            case SEEN_VALUE:
                sawOpcodeAfterValue(seen);
            break;

            case SEEN_CTOR:
            case SEEN_VALUEOFPRIMITIVE:
                if (seen == INVOKEVIRTUAL) {
                    BoxParms boxSigs = boxClasses.get(boxClass);
                    if (boxSigs.getPrimitiveValueSignature().equals(getNameConstantOperand() + getSigConstantOperand())) {
                        bugReporter.reportBug(new BugInstance(this, BugType.NAB_NEEDLESS_BOX_TO_UNBOX.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                                .addSourceLine(this));
                    } else if (getSigConstantOperand().startsWith(SignatureBuilder.PARAM_NONE) && getNameConstantOperand().endsWith("Value")) {
                        bugReporter.reportBug(new BugInstance(this, BugType.NAB_NEEDLESS_BOX_TO_CAST.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                                .addSourceLine(this));
                    }
                }
                state = State.SEEN_NOTHING;
            break;

            case SEEN_VALUEOFSTRING:
                if (seen == INVOKEVIRTUAL) {
                    BoxParms boxSigs = boxClasses.get(boxClass);
                    if (boxSigs.getPrimitiveValueSignature().equals(getNameConstantOperand() + getSigConstantOperand())) {
                        bugReporter.reportBug(new BugInstance(this, BugType.NAB_NEEDLESS_BOXING_PARSE.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                                .addSourceLine(this));
                    }
                }
                state = State.SEEN_NOTHING;
            break;

            case SEEN_PARSE:
                if (seen == INVOKESTATIC) {
                    if (boxClass.equals(getClassConstantOperand()) && "valueOf".equals(getNameConstantOperand())) {
                        bugReporter.reportBug(new BugInstance(this, BugType.NAB_NEEDLESS_BOXING_VALUEOF.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                                .addSourceLine(this));
                    }
                } else if ((seen == INVOKESPECIAL) && Values.CONSTRUCTOR.equals(getNameConstantOperand()) && boxClass.equals(getClassConstantOperand())) {
                    bugReporter.reportBug(new BugInstance(this, BugType.NAB_NEEDLESS_BOXING_STRING_CTOR.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                            .addSourceLine(this));
                }
                state = State.SEEN_NOTHING;
            break;

            case SEEN_ICONST:
                if ((seen == INVOKESTATIC) && Values.SLASHED_JAVA_LANG_BOOLEAN.equals(getClassConstantOperand()) && "valueOf".equals(getNameConstantOperand())
                        && SignatureBuilder.SIG_PRIMITIVE_BOOLEAN_TO_BOOLEAN.equals(getSigConstantOperand())) {
                    bugReporter.reportBug(new BugInstance(this, BugType.NAB_NEEDLESS_BOOLEAN_CONSTANT_CONVERSION.name(), NORMAL_PRIORITY).addClass(this)
                            .addMethod(this).addSourceLine(this));
                }
                state = State.SEEN_NOTHING;
                sawOpcode(seen);
            break;

            case SEEN_GETSTATIC:
                if ((seen == INVOKEVIRTUAL) && Values.SLASHED_JAVA_LANG_BOOLEAN.equals(getClassConstantOperand())
                        && "booleanValue".equals(getNameConstantOperand()) && SignatureBuilder.SIG_VOID_TO_BOOLEAN.equals(getSigConstantOperand())) {
                    bugReporter.reportBug(new BugInstance(this, BugType.NAB_NEEDLESS_BOOLEAN_CONSTANT_CONVERSION.name(), NORMAL_PRIORITY).addClass(this)
                            .addMethod(this).addSourceLine(this));
                }
                state = State.SEEN_NOTHING;
                sawOpcode(seen);
            break;
        }
    }

    private void sawOpcodeAfterNothing(int seen) {
        BoxParms boxSigs;
        switch (seen) {
            case INVOKEVIRTUAL:
                boxClass = getClassConstantOperand();
                boxSigs = boxClasses.get(boxClass);
                if (boxSigs != null) {
                    String methodInfo = getNameConstantOperand() + getSigConstantOperand();
                    if (boxSigs.getPrimitiveValueSignature().equals(methodInfo)) {
                        state = State.SEEN_VALUE;
                    }
                }
            break;

            case INVOKESTATIC:
                boxClass = getClassConstantOperand();
                boxSigs = boxClasses.get(boxClass);
                if (boxSigs != null) {
                    if ("valueOf".equals(getNameConstantOperand())) {
                        String sig = getSigConstantOperand();
                        if (sig.startsWith(SignatureBuilder.PARAM_STRING)) {
                            if (!Values.SLASHED_JAVA_LANG_BOOLEAN.equals(boxClass) || (getClassContext().getJavaClass().getMajor() >= Const.MAJOR_1_5)) {
                                state = State.SEEN_VALUEOFSTRING;
                            }
                        } else {
                            state = State.SEEN_VALUEOFPRIMITIVE;
                        }
                    } else {
                        String parseSig = parseClasses.get(boxClass);
                        if (parseSig != null) {
                            String methodInfo = getNameConstantOperand() + getSigConstantOperand();
                            if (parseSig.equals(methodInfo)) {
                                state = State.SEEN_PARSE;
                            }
                        }
                    }
                }
            break;

            case INVOKESPECIAL:
                boxClass = getClassConstantOperand();
                boxSigs = boxClasses.get(boxClass);
                if ((boxSigs != null) && Values.CONSTRUCTOR.equals(getNameConstantOperand()) && boxSigs.getCtorSignature().equals(getSigConstantOperand())) {
                    state = State.SEEN_CTOR;
                }
            break;

            case ICONST_0:
            case ICONST_1:
                if (state == State.SEEN_NOTHING) {
                    state = State.SEEN_ICONST;
                }
            break;

            case GETSTATIC:
                String clsName = getClassConstantOperand();
                if (Values.SLASHED_JAVA_LANG_BOOLEAN.equals(clsName)) {
                    String fldName = getNameConstantOperand();
                    if ("TRUE".equals(fldName) || "FALSE".equals(fldName)) {
                        state = State.SEEN_GETSTATIC;
                    }
                }
            break;

            case GOTO:
            case GOTO_W:
                if (stack.getStackDepth() > 0) {
                    ternaryPCs.set(getBranchTarget());
                }
            break;

            default:
            break;
        }
    }

    private void sawOpcodeAfterValue(int seen) {
        if (seen == INVOKESPECIAL) {
            if (boxClass.equals(getClassConstantOperand())) {
                String methodName = getNameConstantOperand();
                if (Values.CONSTRUCTOR.equals(methodName)) {
                    String boxSig = boxClasses.get(boxClass).getCtorSignature();
                    String methodSig = getSigConstantOperand();
                    if (boxSig.equals(methodSig)) {
                        bugReporter.reportBug(new BugInstance(this, BugType.NAB_NEEDLESS_AUTOBOXING_CTOR.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                                .addSourceLine(this));
                    }
                }
            }
        } else if ((seen == INVOKESTATIC) && boxClass.equals(getClassConstantOperand())) {
            String methodName = getNameConstantOperand();
            if ("valueOf".equals(methodName)) {
                String boxSig = boxClasses.get(boxClass).getValueOfSignature();
                String methodSig = getSigConstantOperand();
                if (boxSig.equals(methodSig)) {
                    bugReporter.reportBug(new BugInstance(this, BugType.NAB_NEEDLESS_AUTOBOXING_VALUEOF.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                            .addSourceLine(this));
                }
            }
        }
        state = State.SEEN_NOTHING;
    }

    static class BoxParms {

        private String primitiveValueSignature;
        private String ctorSignature;
        private String valueOfSignature;

        BoxParms(String primValueSig, String ctorSig, String valueOfSig) {
            primitiveValueSignature = primValueSig;
            ctorSignature = ctorSig;
            valueOfSignature = valueOfSig;
        }

        public String getPrimitiveValueSignature() {
            return primitiveValueSignature;
        }

        public String getCtorSignature() {
            return ctorSignature;
        }

        public String getValueOfSignature() {
            return valueOfSignature;
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }
}
