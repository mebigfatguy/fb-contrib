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

import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for methods that pass single character string constants as parameters to methods that alternatively have an overridden method that accepts a character
 * instead. It is more performant for the method to handle a single character than a String.
 */
@CustomUserValue
public class UseCharacterParameterizedMethod extends BytecodeScanningDetector {

    /**
     * holds a user value for a StringBuilder or StringBuffer on the stack that is an online append ideally there would be an UNKNOWN option, rather than null,
     * but findbugs seems to have a nasty bug with static fields holding onto uservalues across detectors
     */
    enum UCPMUserValue {
        INLINE
    }

    private final static Map<FQMethod, Object> characterMethods;

    static {
        Map<FQMethod, Object> methodsMap = new HashMap<FQMethod, Object>();
        // The values are where the parameter will be on the stack - For
        // example, a value of 0 means the String literal to check
        // was the last parameter, and a stack offset of 2 means it was the 3rd
        // to last.
        methodsMap.put(new FQMethod(Values.SLASHED_JAVA_LANG_STRING, "indexOf", "(Ljava/lang/String;)I"), Values.ZERO);
        methodsMap.put(new FQMethod(Values.SLASHED_JAVA_LANG_STRING, "indexOf", "(Ljava/lang/String;I)I"), Values.ONE);
        methodsMap.put(new FQMethod(Values.SLASHED_JAVA_LANG_STRING, "lastIndexOf", "(Ljava/lang/String;)I"), Values.ZERO);
        methodsMap.put(new FQMethod(Values.SLASHED_JAVA_LANG_STRING, "lastIndexOf", "(Ljava/lang/String;I)I"), Values.ONE);
        methodsMap.put(new FQMethod("java/io/PrintStream", "print", "(Ljava/lang/String;)V"), Values.ZERO);
        methodsMap.put(new FQMethod("java/io/PrintStream", "println", "(Ljava/lang/String;)V"), Values.ZERO);
        methodsMap.put(new FQMethod("java/io/StringWriter", "write", "(Ljava/lang/String;)V"), Values.ZERO);
        methodsMap.put(new FQMethod("java/lang/StringBuffer", "append", "(Ljava/lang/String;)Ljava/lang/StringBuffer;"), Values.ZERO);
        methodsMap.put(new FQMethod("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"), Values.ZERO);

        // same thing as above, except now with two params
        methodsMap.put(new FQMethod("java/lang/String", "replace", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;"), new IntPair(0, 1));

        characterMethods = Collections.unmodifiableMap(methodsMap);
    }

    private static class IntPair {
        final int firstStringParam, secondStringParam;

        IntPair(int firstStringParam, int secondStringParam) {
            this.firstStringParam = firstStringParam;
            this.secondStringParam = secondStringParam;
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }

    private final BugReporter bugReporter;
    private OpcodeStack stack;

    /**
     * constructs a UCPM detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public UseCharacterParameterizedMethod(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * overrides the visitor to create and clear the opcode stack
     *
     * @param context
     *            the context object for the currently parsed class
     */
    @Override
    public void visitClassContext(final ClassContext context) {
        try {
            stack = new OpcodeStack();
            super.visitClassContext(context);
        } finally {
            stack = null;
        }
    }

    /**
     * looks for methods that contain a LDC opcode
     *
     * @param obj
     *            the context object of the current method
     * @return if the class uses LDC instructions
     */

    private boolean prescreen(Method obj) {
        BitSet bytecodeSet = getClassContext().getBytecodeSet(obj);
        return (bytecodeSet != null) && ((bytecodeSet.get(Constants.LDC) || (bytecodeSet.get(Constants.LDC_W))));
    }

    /**
     * prescreens the method, and reset the stack
     *
     * @param obj
     *            the context object for the currently parsed method
     */
    @Override
    public void visitCode(Code obj) {
        if (prescreen(getMethod())) {
            stack.resetForMethodEntry(this);
            super.visitCode(obj);
        }
    }

    /**
     * implements the visitor to look for method calls that pass a constant string as a parameter when the string is only one character long, and there is an
     * alternate method passing a character.
     */
    @Override
    public void sawOpcode(int seen) {

        try {
            stack.precomputation(this);

            if ((seen == INVOKEVIRTUAL) || (seen == INVOKEINTERFACE)) {
                FQMethod key = new FQMethod(getClassConstantOperand(), getNameConstantOperand(), getSigConstantOperand());

                Object posObject = characterMethods.get(key);
                if (posObject instanceof Integer) {
                    if (checkSingleParamMethod(((Integer) posObject).intValue()) && !isInlineAppend(key)) {
                        reportBug();
                    }
                } else if ((posObject instanceof IntPair) && checkDoubleParamMethod((IntPair) posObject)) {
                    reportBug();
                }

            } else if (seen == DUP) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item itm = stack.getStackItem(0);
                    String duppedSig = itm.getSignature();
                    if ("Ljava/lang/StringBuilder;".equals(duppedSig) || "Ljava/lang/StringBuffer;".equals(duppedSig)) {
                        itm.setUserValue(UCPMUserValue.INLINE);
                    }
                }
            } else if (((seen == ASTORE) || ((seen >= ASTORE_0) && (seen <= ASTORE_3)) || (seen == PUTFIELD) || (seen == PUTSTATIC))
                    && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item itm = stack.getStackItem(0);
                itm.setUserValue(null);
            }
        } finally {
            UCPMUserValue uv = callHasInline(seen);

            stack.sawOpcode(this, seen);
            if ((uv == UCPMUserValue.INLINE) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item itm = stack.getStackItem(0);
                itm.setUserValue(uv);
            }
        }
    }

    private void reportBug() {
        bugReporter.reportBug(new BugInstance(this, BugType.UCPM_USE_CHARACTER_PARAMETERIZED_METHOD.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                .addSourceLine(this));
    }

    private boolean checkDoubleParamMethod(IntPair posObject) {
        return checkSingleParamMethod(posObject.firstStringParam) && checkSingleParamMethod(posObject.secondStringParam);
    }

    private boolean checkSingleParamMethod(int paramPos) {
        if (stack.getStackDepth() > paramPos) {
            OpcodeStack.Item itm = stack.getStackItem(paramPos);
            // casting to CharSequence is safe as FindBugs 3 (and fb-contrib 6)
            // require Java 1.7 or later to run
            // it's also needed with the addition of support for replace (which
            // takes charSequences
            CharSequence con = (CharSequence) itm.getConstant();
            if ((con != null) && (con.length() == 1)) {
                return true;
            }
        }
        return false;
    }

    /**
     * looks to see if we are in a inline string append like "(" + a + ")";
     *
     * @param fqm
     *            the method that is being called
     * @return whether we are in an inline string append
     */
    private boolean isInlineAppend(FQMethod fqm) {
        if (!"java/lang/StringBuilder".equals(fqm.getClassName()) && !"java/lang/StringBuffer".equals(fqm.getClassName())) {
            return false;
        }

        if (stack.getStackDepth() <= 1) {
            return true;
        }

        OpcodeStack.Item itm = stack.getStackItem(1);
        return itm.getUserValue() == UCPMUserValue.INLINE;
    }

    /**
     * checks to see if the current opcode is an INVOKEVIRTUAL call that has a INLINE userValue on the caller and a return value. If so, return it.
     *
     * @param seen
     *            the currently parsed opcode
     * @return whether the caller has an INLINE tag on it
     *
     */
    private UCPMUserValue callHasInline(int seen) {
        if (seen != INVOKEVIRTUAL) {
            return null;
        }

        String sig = getSigConstantOperand();
        String returnSig = Type.getReturnType(sig).getSignature();
        if ("Ljava/lang/StringBuilder;".equals(returnSig) || "Ljava/lang/StringBuffer;".equals(returnSig)) {
            int parmCount = Type.getArgumentTypes(sig).length;
            if (stack.getStackDepth() > parmCount) {
                OpcodeStack.Item itm = stack.getStackItem(parmCount);
                return (UCPMUserValue) itm.getUserValue();
            }
        }

        return null;
    }
}
