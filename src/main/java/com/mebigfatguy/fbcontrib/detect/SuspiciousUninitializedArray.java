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

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.Const;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for creation of arrays, that are not populated before being returned for a method. While it is possible that the method that called this method will do
 * the work of populated the array, it seems odd that this would be the case.
 */
@CustomUserValue
public class SuspiciousUninitializedArray extends BytecodeScanningDetector {

    private static JavaClass THREAD_LOCAL_CLASS;
    private static final String INITIAL_VALUE = "initialValue";

    static {
        try {
            THREAD_LOCAL_CLASS = Repository.lookupClass(ThreadLocal.class);
        } catch (ClassNotFoundException e) {
            THREAD_LOCAL_CLASS = null;
        }
    }

    private final BugReporter bugReporter;
    private boolean isEnum;
    private OpcodeStack stack;
    private String returnArraySig;
    private BitSet uninitializedRegs;
    private Map<Integer, Integer> arrayAliases;
    private Map<Integer, SUAUserValue> storedUVs;

    /**
     * constructs a SUA detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public SuspiciousUninitializedArray(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * overrides the visitor to reset the stack
     *
     * @param classContext
     *            the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            isEnum = classContext.getJavaClass().isEnum();
            stack = new OpcodeStack();
            uninitializedRegs = new BitSet();
            arrayAliases = new HashMap<>();
            storedUVs = new HashMap<>();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            uninitializedRegs = null;
            arrayAliases = null;
            storedUVs = null;
        }
    }

    /**
     * overrides the visitor to check to see if the method returns an array, and if so resets the stack for this method.
     *
     * @param obj
     *            the context object for the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {

        Method m = getMethod();
        if (m.isSynthetic()) {
            return;
        }

        if (isEnum && "values".equals(m.getName())) {
            return;
        }

        String sig = m.getSignature();
        int sigPos = sig.indexOf(")[");
        if (sigPos < 0) {
            return;
        }
        if (INITIAL_VALUE.equals(m.getName())) {
            try {
                if ((THREAD_LOCAL_CLASS == null) || getClassContext().getJavaClass().instanceOf(THREAD_LOCAL_CLASS)) {
                    return;
                }
            } catch (ClassNotFoundException e) {
                bugReporter.reportMissingClass(e);
                return;
            }
        }

        stack.resetForMethodEntry(this);
        returnArraySig = sig.substring(sigPos + 1);
        uninitializedRegs.clear();
        arrayAliases.clear();
        storedUVs.clear();
        super.visitCode(obj);
    }

    /**
     * overrides the visitor to annotate new array creation with a user value that denotes it as being uninitialized, and then if the array is populated to
     * remove that user value. It then finds return values that have uninitialized arrays. byte arrays are not collected as creating a blank byte array is
     * probably a reasonably normal occurrence.
     *
     * @param seen
     *            the context parameter of the currently parsed op code
     */
    @Override
    public void sawOpcode(int seen) {
        SUAUserValue userValue = null;
        try {
            stack.precomputation(this);

            switch (seen) {
                case Const.NEWARRAY: {
                    if (!isTOS0()) {
                        int typeCode = getIntConstant();
                        if ((typeCode != Const.T_BYTE)
                                && returnArraySig.equals(SignatureUtils.toArraySignature(SignatureUtils.getTypeCodeSignature(typeCode)))) {
                            userValue = SUAUserValue.UNINIT_ARRAY;
                        }
                    }
                }
                break;

                case Const.ANEWARRAY: {
                    if (!isTOS0()) {
                        String sig = SignatureUtils.toArraySignature(getClassConstantOperand());
                        if (returnArraySig.equals(sig)) {
                            userValue = SUAUserValue.UNINIT_ARRAY;
                        }
                    }
                }
                break;

                case Const.MULTIANEWARRAY: {
                    if (returnArraySig.equals(getClassConstantOperand())) {
                        userValue = SUAUserValue.UNINIT_ARRAY;
                    }
                }
                break;

                case Const.INVOKEVIRTUAL:
                case Const.INVOKEINTERFACE:
                case Const.INVOKESPECIAL:
                case Const.INVOKESTATIC:
                case Const.INVOKEDYNAMIC: {
                    String methodSig = getSigConstantOperand();
                    List<String> types = SignatureUtils.getParameterSignatures(methodSig);
                    for (int t = 0; t < types.size(); t++) {
                        String parmSig = types.get(t);
                        if (returnArraySig.equals(parmSig) || Values.SIG_JAVA_LANG_OBJECT.equals(parmSig)
                                || SignatureBuilder.SIG_OBJECT_ARRAY.equals(parmSig)) {
                            int parmIndex = types.size() - t - 1;
                            if (stack.getStackDepth() > parmIndex) {
                                OpcodeStack.Item item = stack.getStackItem(parmIndex);
                                SUAUserValue uv = (SUAUserValue) item.getUserValue();
                                if (uv != null) {
                                    int reg;
                                    if (uv.isRegister()) {
                                        reg = uv.getRegister();
                                    } else {
                                        reg = item.getRegisterNumber();
                                    }
                                    item.setUserValue(null);
                                    if (reg >= 0) {
                                        clearAliases(reg);
                                        storedUVs.remove(reg);
                                    }
                                }
                            }
                        }
                    }
                }
                break;

                case Const.AALOAD: {
                    if (stack.getStackDepth() >= 2) {
                        OpcodeStack.Item item = stack.getStackItem(1);
                        SUAUserValue uv = (SUAUserValue) item.getUserValue();
                        if ((uv != null) && (uv.isUnitializedArray())) {
                            userValue = new SUAUserValue(item.getRegisterNumber());
                        }
                    }
                }
                break;

                case Const.IASTORE:
                case Const.LASTORE:
                case Const.FASTORE:
                case Const.DASTORE:
                case Const.AASTORE:
                case Const.BASTORE:
                case Const.CASTORE:
                case Const.SASTORE: {
                    if (stack.getStackDepth() >= 3) {
                        OpcodeStack.Item item = stack.getStackItem(2);
                        SUAUserValue uv = (SUAUserValue) item.getUserValue();
                        int reg;
                        if ((uv != null) && uv.isRegister()) {
                            reg = uv.getRegister();
                        } else {
                            reg = item.getRegisterNumber();
                        }
                        item.setUserValue(null);
                        if (reg >= 0) {
                            clearAliases(reg);
                            storedUVs.remove(reg);
                        }
                    } else {
                        // error condition - stack isn't right
                        uninitializedRegs.clear();
                    }
                }
                break;

                case Const.ASTORE:
                case Const.ASTORE_0:
                case Const.ASTORE_1:
                case Const.ASTORE_2:
                case Const.ASTORE_3: {
                    int reg = RegisterUtils.getAStoreReg(this, seen);
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item item = stack.getStackItem(0);
                        SUAUserValue uv = (SUAUserValue) item.getUserValue();
                        storedUVs.put(Integer.valueOf(reg), uv);
                        uninitializedRegs.set(reg, (uv != null) && (uv.isUnitializedArray()));
                        Integer aliasReg = arrayAliases.get(reg);
                        if (aliasReg != null) {
                            uninitializedRegs.set(aliasReg, (uv != null) && (uv.isUnitializedArray()));
                        }

                        int targetReg = item.getRegisterNumber();
                        if ((targetReg >= 0) && (targetReg != reg)) {
                            arrayAliases.put(reg, targetReg);
                        } else if ((uv != null) && uv.isRegister()) {
                            arrayAliases.put(reg, uv.getRegister());
                        } else {
                            arrayAliases.remove(reg);
                        }
                    } else {
                        clearAliases(reg);
                        storedUVs.remove(Integer.valueOf(reg));
                    }
                }
                break;

                case Const.ALOAD:
                case Const.ALOAD_0:
                case Const.ALOAD_1:
                case Const.ALOAD_2:
                case Const.ALOAD_3: {
                    int reg = RegisterUtils.getALoadReg(this, seen);
                    userValue = storedUVs.get(Integer.valueOf(reg));
                }
                break;

                case Const.PUTFIELD: {
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item item = stack.getStackItem(0);
                        item.setUserValue(null);
                        int reg = item.getRegisterNumber();
                        if (reg >= 0) {
                            clearAliases(reg);
                            storedUVs.remove(reg);
                        }
                    }
                }
                break;

                case Const.ARETURN: {
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item item = stack.getStackItem(0);
                        SUAUserValue uv = (SUAUserValue) item.getUserValue();
                        if ((uv != null) && (uv.isUnitializedArray())) {
                            bugReporter.reportBug(new BugInstance(this, BugType.SUA_SUSPICIOUS_UNINITIALIZED_ARRAY.name(), NORMAL_PRIORITY).addClass(this)
                                    .addMethod(this).addSourceLine(this));
                        }
                    }
                }
                break;

                case Const.DUP:
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item item = stack.getStackItem(0);
                        userValue = (SUAUserValue) item.getUserValue();
                    }
                break;

                default:
                break;
            }
        } finally {
            TernaryPatcher.pre(stack, seen);
            stack.sawOpcode(this, seen);
            TernaryPatcher.post(stack, seen);
            if ((Const.getProduceStack(seen) > 0) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item item = stack.getStackItem(0);
                item.setUserValue(userValue);
            }
        }
    }

    private void clearAliases(int reg) {

        clearClosureAliases(reg, new BitSet());
    }

    private void clearClosureAliases(int reg, BitSet alreadyCleared) {

        if (alreadyCleared.get(reg)) {
            return;
        }

        uninitializedRegs.clear(reg);
        alreadyCleared.set(reg);
        if (uninitializedRegs.isEmpty()) {
            return;
        }

        Integer targetReg = arrayAliases.get(reg);
        if (targetReg != null) {
            clearClosureAliases(targetReg, alreadyCleared);
        }

        Set<Integer> clear = new HashSet<>();
        for (Map.Entry<Integer, Integer> entry : arrayAliases.entrySet()) {
            targetReg = entry.getValue();
            if (targetReg.equals(reg)) {
                clear.add(entry.getKey());
            }
        }

        for (Integer cr : clear) {
            clearClosureAliases(cr, alreadyCleared);
        }
    }

    private boolean isTOS0() {
        if (stack.getStackDepth() == 0) {
            return false;
        }

        OpcodeStack.Item item = stack.getStackItem(0);
        return item.mustBeZero();
    }

    static final class SUAUserValue {

        enum SUAUserValueType {
            REGISTER, UNINIT_ARRAY
        };

        public static final SUAUserValue UNINIT_ARRAY = new SUAUserValue();
        private SUAUserValueType type;
        private int reg;

        private SUAUserValue() {
            this.type = SUAUserValueType.UNINIT_ARRAY;
            reg = -1;
        }

        public SUAUserValue(int register) {
            this.type = SUAUserValueType.REGISTER;
            reg = register;
        }

        public boolean isUnitializedArray() {
            return type == SUAUserValueType.UNINIT_ARRAY;
        }

        public boolean isRegister() {
            return type == SUAUserValueType.REGISTER;
        }

        public int getRegister() {
            return reg;
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }
}
