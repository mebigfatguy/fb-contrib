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

import java.util.HashSet;
import java.util.Set;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XField;

/**
 * looks for method calls that passes the same argument to two different
 * parameters of the same method. It doesn't report method calls where the
 * arguments are constants.
 */
@CustomUserValue
public class StutteredMethodArguments extends BytecodeScanningDetector {
    private static Set<FQMethod> ignorableSignatures = new HashSet<FQMethod>();

    static {
        ignorableSignatures.add(new FQMethod("java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"));
    }

    private final BugReporter bugReporter;
    private OpcodeStack stack;
    private String processedPackageName;
    private String processedMethodName;

    /**
     * constructs a SMA detector given the reporter to report bugs on.
     * 
     * @param bugReporter
     *            the sync of bug reports
     */
    public StutteredMethodArguments(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * overrides the visitor to create the opcode stack
     * 
     * @param classContext
     *            the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            processedPackageName = classContext.getJavaClass().getPackageName();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            processedPackageName = null;
        }
    }

    /**
     * overrides the visitor to reset the stack object
     * 
     * @param obj
     *            the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        try {
            stack.resetForMethodEntry(this);
            processedMethodName = getMethod().getName();
            super.visitCode(obj);
        } finally {
            processedMethodName = null;
        }

    }

    /**
     * overrides the visitor to look for method calls that pass the same value
     * for two different arguments
     * 
     * @param seen
     *            the currently parsed op code
     */
    @Override
    public void sawOpcode(int seen) {
        String fieldSource = null;

        try {
            stack.precomputation(this);

            switch (seen) {
            case INVOKEVIRTUAL:
            case INVOKESTATIC:
            case INVOKEINTERFACE:
            case INVOKESPECIAL:
                String clsName = getClassConstantOperand();
                String packageName;
                int slashPos = clsName.lastIndexOf('/');
                if (slashPos >= 0) {
                    packageName = clsName.substring(0, slashPos);
                } else {
                    packageName = "";
                }

                if (SignatureUtils.similarPackages(processedPackageName, packageName, 2)) {
                    String methodName = getNameConstantOperand();
                    String signature = getSigConstantOperand();
                    FQMethod methodInfo = new FQMethod(clsName , methodName, signature);
                    if ((!processedMethodName.equals(methodName)) && !ignorableSignatures.contains(methodInfo)) {
                        Type[] parms = Type.getArgumentTypes(signature);
                        if (parms.length > 1) {
                            if (stack.getStackDepth() > parms.length) {
                                if (duplicateArguments(stack, parms)) {
                                    bugReporter.reportBug(new BugInstance(this, BugType.SMA_STUTTERED_METHOD_ARGUMENTS.name(), NORMAL_PRIORITY).addClass(this)
                                            .addMethod(this).addSourceLine(this));
                                }
                            }
                        }
                    }
                }
                break;

            case GETFIELD:
            case GETSTATIC:
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    int reg = item.getRegisterNumber();
                    if (reg >= 0) {
                        fieldSource = String.valueOf(reg);
                    } else {
                        XField f = item.getXField();
                        if (f != null) {
                            fieldSource = f.getClassName() + ':' + f.getName();
                        } else {
                            fieldSource = "";
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
            if (fieldSource != null) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    item.setUserValue(fieldSource);
                }
            }
        }
    }

    /**
     * looks for duplicate arguments that are not constants
     * 
     * @param opStack
     *            the stack to look thru
     * @param parms
     *            the arguments to the method being called
     * @return if there are duplicates
     */
    private static boolean duplicateArguments(OpcodeStack opStack, Type... parms) {
        Set<String> args = new HashSet<String>();
        for (int i = 0; i < parms.length; i++) {
            OpcodeStack.Item item = opStack.getStackItem(i);

            String signature = item.getSignature();
            if (signature.startsWith("L") && !signature.startsWith("Ljava/lang/") && (item.getConstant() == null)) {
                String arg = null;
                int reg = item.getRegisterNumber();
                if (reg >= 0) {
                    arg = String.valueOf(reg);
                } else {
                    XField f = item.getXField();
                    if (f != null) {
                        String fieldSource = (String) item.getUserValue();
                        if (fieldSource == null) {
                            fieldSource = "";
                        }
                        arg = fieldSource + '|' + f.getClassName() + ':' + f.getName();
                    }
                }

                if (arg != null) {
                    arg += "--" + parms[i].getSignature();
                    if (args.contains(arg)) {
                        return true;
                    }
                    args.add(arg);
                }
            }
        }

        return false;
    }
}
