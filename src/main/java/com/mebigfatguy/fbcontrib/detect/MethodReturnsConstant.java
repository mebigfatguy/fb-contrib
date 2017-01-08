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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.StopOpcodeParsingException;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XField;

/**
 * looks for private methods that can only return one constant value. either the class should not return a value, or perhaps a branch was missed.
 */
@CustomUserValue
public class MethodReturnsConstant extends BytecodeScanningDetector {
    private final BugReporter bugReporter;
    private OpcodeStack stack;
    private Integer returnRegister;
    private Map<Integer, Object> registerConstants;
    private Set<Method> overloadedMethods;
    private Object returnConstant;
    private int returnPC;

    /**
     * constructs a MRC detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public MethodReturnsConstant(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to collect all methods that are overloads. These methods should be ignored, as you may differentiate constants based on parameter
     * type, or value.
     *
     * @param classContext
     *            the currently parsed class object
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            registerConstants = new HashMap<>();
            overloadedMethods = collectOverloadedMethods(classContext.getJavaClass());
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            registerConstants = null;
            overloadedMethods = null;
        }
    }

    /**
     * implements the visitor to reset the stack and proceed for private methods
     *
     * @param obj
     *            the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        Method m = getMethod();

        if (overloadedMethods.contains(m)) {
            return;
        }

        int aFlags = m.getAccessFlags();
        if ((((aFlags & Constants.ACC_PRIVATE) != 0) || ((aFlags & Constants.ACC_STATIC) != 0)) && ((aFlags & Constants.ACC_SYNTHETIC) == 0)
                && (!m.getSignature().endsWith(")Z"))) {
            stack.resetForMethodEntry(this);
            returnRegister = Values.NEGATIVE_ONE;
            returnConstant = null;
            registerConstants.clear();
            returnPC = -1;

            try {
                super.visitCode(obj);
                if ((returnConstant != null)) {
                    BugInstance bi = new BugInstance(this, BugType.MRC_METHOD_RETURNS_CONSTANT.name(),
                            ((aFlags & Constants.ACC_PRIVATE) != 0) ? NORMAL_PRIORITY : LOW_PRIORITY).addClass(this).addMethod(this);
                    if (returnPC >= 0) {
                        bi.addSourceLine(this, returnPC);
                    }

                    bi.addString(returnConstant.toString());
                    bugReporter.reportBug(bi);
                }
            } catch (StopOpcodeParsingException e) {
                // method was not suspect
            }
        }
    }

    /**
     * implements the visitor to look for methods that return a constant
     *
     * @param seen
     *            the opcode of the currently parsed instruction
     */
    @Override
    public void sawOpcode(int seen) {
        boolean sawSBToString = false;
        try {

            stack.precomputation(this);
            if ((seen >= IRETURN) && (seen <= ARETURN)) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);

                    Integer register = Integer.valueOf(item.getRegisterNumber());
                    if (registerConstants.containsKey(register) && (registerConstants.get(register) == null)) {
                        throw new StopOpcodeParsingException();
                    }

                    String returnSig = item.getSignature();
                    if ((returnSig != null) && returnSig.startsWith(Values.SIG_ARRAY_PREFIX)) {
                        XField f = item.getXField();
                        if ((f == null) || (!f.isStatic())) {
                            throw new StopOpcodeParsingException();
                        }
                    }

                    Object constant = item.getConstant();
                    if (constant == null) {
                        throw new StopOpcodeParsingException();
                    }
                    if (Boolean.TRUE.equals(item.getUserValue()) && ("".equals(constant))) {
                        throw new StopOpcodeParsingException();
                    }
                    if ((returnConstant != null) && (!returnConstant.equals(constant))) {
                        throw new StopOpcodeParsingException();
                    }

                    returnRegister = Integer.valueOf(item.getRegisterNumber());
                    returnConstant = constant;
                    returnPC = getPC();
                }
            } else if ((seen == GOTO) || (seen == GOTO_W)) {
                if (stack.getStackDepth() > 0) {
                    // Trinaries confuse us too much, if the code has a ternary well - oh well
                    throw new StopOpcodeParsingException();
                }
            } else if (seen == INVOKEVIRTUAL) {
                String clsName = getClassConstantOperand();
                if (SignatureUtils.isAppendableStringClassName(clsName)) {
                    sawSBToString = "toString".equals(getNameConstantOperand());
                }
            } else if (((seen >= ISTORE) && (seen <= ASTORE_3)) || (seen == IINC)) {
                Integer register = Integer.valueOf(getRegisterOperand());
                if ((returnRegister.intValue() != -1) && (register.equals(returnRegister))) {
                    throw new StopOpcodeParsingException();
                }

                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    Object constant = item.getConstant();
                    if (registerConstants.containsKey(register)) {
                        if ((constant == null) || !constant.equals(registerConstants.get(register))) {
                            registerConstants.put(register, null);
                        }
                    } else {
                        if (item.getSignature().contains(Values.SIG_ARRAY_PREFIX)) {
                            registerConstants.put(register, null);
                        } else {
                            registerConstants.put(register, constant);
                        }
                    }
                } else {
                    registerConstants.put(register, null);
                }

                if (returnRegister.equals(register)) {
                    Object constant = registerConstants.get(returnRegister);
                    if (constant != null) {
                        throw new StopOpcodeParsingException();
                    }
                }
            }

        } finally {
            TernaryPatcher.pre(stack, seen);
            stack.sawOpcode(this, seen);
            TernaryPatcher.post(stack, seen);
            if (sawSBToString && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item item = stack.getStackItem(0);
                item.setUserValue(Boolean.TRUE);
            }
        }
    }

    /**
     * adds all methods of a class that are overloaded to a set. This method is O(nlogn) so for large classes might be slow. Assuming on average it's better
     * than other choices. When a match is found in the j index, it is removed from the array, so it is not scanned with the i index
     *
     * @param cls
     *            the class to look for overloaded methods
     * @return the set of methods that are overloaded
     */
    private Set<Method> collectOverloadedMethods(JavaClass cls) {

        Set<Method> overloads = new HashSet<>();

        Method[] methods = cls.getMethods();
        int numMethods = methods.length;

        for (int i = 0; i < numMethods; i++) {
            boolean foundOverload = false;

            for (int j = i + 1; j < numMethods; j++) {

                if (methods[i].getName().equals(methods[j].getName())) {
                    overloads.add(methods[j]);
                    foundOverload = true;
                }
            }
            if (foundOverload) {
                overloads.add(methods[i]);
            }
        }

        return overloads;
    }
}
