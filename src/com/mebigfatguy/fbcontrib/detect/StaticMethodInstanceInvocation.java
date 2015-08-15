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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;

import org.apache.bcel.Constants;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.ToString;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for methods that make static method calls using an instance reference.
 * For documentation purposes, it is better to call the method using the class
 * name. This may represent a change in definition that should be noticed.
 */
public class StaticMethodInstanceInvocation extends BytecodeScanningDetector {
    private final BugReporter bugReporter;
    private OpcodeStack stack;
    private List<PopInfo> popStack;

    /**
     * constructs a SMII detector given the reporter to report bugs on
     * 
     * @param bugReporter
     *            the sync of bug reports
     */
    public StaticMethodInstanceInvocation(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            popStack = new ArrayList<PopInfo>();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            popStack = null;
        }
    }

    /**
     * looks for methods that contain a INVOKESTATIC opcodes
     *
     * @param method
     *            the context object of the current method
     * @return if the class uses synchronization
     */
    private boolean prescreen(Method method) {
        BitSet bytecodeSet = getClassContext().getBytecodeSet(method);
        return (bytecodeSet != null) && (bytecodeSet.get(Constants.INVOKESTATIC));
    }

    /**
     * implement the visitor to reset the stack
     *
     * @param obj
     *            the context object of the currently parsed method
     */
    @Override
    public void visitCode(Code obj) {
        Method m = getMethod();
        if (prescreen(m)) {
            stack.resetForMethodEntry(this);
            popStack.clear();
            super.visitCode(obj);
        }
    }

    /**
     * implements the visitor to look for static method calls from instance
     * variables
     *
     * @param seen
     *            the opcode of the currently visited instruction
     */
    @Override
    public void sawOpcode(int seen) {
        try {
            stack.precomputation(this);

            int sDepth = stack.getStackDepth();
            Iterator<PopInfo> it = popStack.iterator();

            while (it.hasNext()) {
                if (sDepth < it.next().popDepth) {
                    it.remove();
                }
            }

            if ((seen == INVOKESTATIC) && (popStack.size() > 0)) {
                String method = getNameConstantOperand();
                if (method.indexOf('$') < 0) {
                    PopInfo pInfo = popStack.get(0);
                    Type[] args = Type.getArgumentTypes(getSigConstantOperand());
                    if ((args.length > 0) || (pInfo.popPC == (getPC() - 1))) {
                        if (args.length == (stack.getStackDepth() - pInfo.popDepth)) {
                            if (classDefinesStaticMethod(pInfo.popSignature.substring(1, pInfo.popSignature.length() - 1))) {
                                int lineNumber = -1;
                                if (lineNumberTable != null) {
                                    lineNumber = lineNumberTable.getSourceLine(getPC());
                                }
                                if (pInfo.popLineNum == lineNumber) {
                                    bugReporter.reportBug(new BugInstance(this, BugType.SMII_STATIC_METHOD_INSTANCE_INVOCATION.name(), NORMAL_PRIORITY)
                                            .addClass(this).addMethod(this).addSourceLine(this));
                                }
                                popStack.clear();
                            }
                        }
                    }
                }
            }

            if ((seen == ASTORE) || ((seen >= ASTORE_0) && (seen <= ASTORE_3)) || (seen == PUTFIELD) || (seen == ATHROW) || (seen == GOTO) || (seen == GOTO_W)
                    || (seen >= IFEQ) && (seen <= IF_ACMPNE)) {
                popStack.clear();
            } else if ((seen == INVOKESPECIAL) || (seen == INVOKEINTERFACE) || (seen == INVOKEVIRTUAL) || (seen == INVOKESTATIC)) {
                Type result = Type.getReturnType(getSigConstantOperand());
                if ("V".equals(result.getSignature())) {
                    popStack.clear();
                }
            }

            if (seen == POP) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item itm = stack.getStackItem(0);
                    String popSig = itm.getSignature();
                    if (popSig.charAt(0) == 'L') {
                        int lineNumber = -1;
                        if (lineNumberTable != null) {
                            lineNumber = lineNumberTable.getSourceLine(getPC());
                        }
                        popStack.add(new PopInfo(getPC(), lineNumber, popSig, sDepth - 1));
                    }
                }
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
            popStack.clear();
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    boolean classDefinesStaticMethod(String popSignature) throws ClassNotFoundException {
        popSignature = popSignature.replace('/', '.');
        if ("java.lang.Object".equals(popSignature) || "java.lang.Class".equals(popSignature)) {
            return false;
        }

        JavaClass cls = Repository.lookupClass(popSignature);
        Method[] methods = cls.getMethods();
        for (Method m : methods) {
            if (m.isStatic()) {
                if (m.getName().equals(getNameConstantOperand()) && m.getSignature().equals(getSigConstantOperand())) {
                    return true;
                }
            }
        }

        return classDefinesStaticMethod(cls.getSuperclassName());
    }

    static class PopInfo {
        int popPC;
        int popLineNum;
        String popSignature;
        int popDepth;

        PopInfo(int pc, int lineNum, String signature, int depth) {
            popPC = pc;
            popLineNum = lineNum;
            popSignature = signature;
            popDepth = depth;
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }
}
