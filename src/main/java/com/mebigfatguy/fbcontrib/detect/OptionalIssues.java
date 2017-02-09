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

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

public class OptionalIssues extends BytecodeScanningDetector {

    private static final FQMethod OR_ELSE = new FQMethod("java/util/Optional", "orElse", "(Ljava/lang/Object;)Ljava/lang/Object;");
    private static final FQMethod OR_ELSE_GET = new FQMethod("java/util/Optional", "orElseGet", "(Ljava/util/function/Supplier;)Ljava/lang/Object;");
    private BugReporter bugReporter;
    private OpcodeStack stack;

    public OptionalIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        JavaClass cls = classContext.getJavaClass();

        if (cls.getMajor() >= Constants.MAJOR_1_8) {
            try {
                stack = new OpcodeStack();
                super.visitClassContext(classContext);
            } finally {
                stack = null;
            }
        }
    }

    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        super.visitCode(obj);
    }

    @Override
    public void sawOpcode(int seen) {
        try {
            switch (seen) {
                case IFNULL:
                case IFNONNULL:
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item itm = stack.getStackItem(0);
                        if ("Ljava/util/Optional;".equals(itm.getSignature())) {
                            bugReporter.reportBug(new BugInstance(this, BugType.OI_OPTIONAL_ISSUES_CHECKING_REFERENCE.name(), NORMAL_PRIORITY).addClass(this)
                                    .addMethod(this).addSourceLine(this));
                        }

                    }
                break;

                case INVOKEVIRTUAL:
                    FQMethod curMethod = new FQMethod(getClassConstantOperand(), getNameConstantOperand(), getSigConstantOperand());
                    if (OR_ELSE.equals(curMethod)) {
                        if (stack.getStackDepth() > 0) {
                            OpcodeStack.Item itm = stack.getStackItem(0);
                            if (itm.getReturnValueOf() != null) {
                                bugReporter.reportBug(new BugInstance(this, BugType.OI_OPTIONAL_ISSUES_USES_IMMEDIATE_EXECUTION.name(), NORMAL_PRIORITY)
                                        .addClass(this).addMethod(this).addSourceLine(this));
                            }
                        }
                    } else if (OR_ELSE_GET.equals(curMethod)) {
                        if (stack.getStackDepth() > 0) {
                            OpcodeStack.Item itm = stack.getStackItem(0);
                            JavaClass supplier = itm.getJavaClass();
                            if (supplier.isClass()) {
                                Method getMethod = getSupplierGetMethod(supplier);
                                if (getMethod != null) {
                                    byte[] byteCode = getMethod.getCode().getCode();
                                    if (byteCode.length <= 4) {
                                        // we are looking for ALOAD, GETFIELD, or LDC followed by ARETURN, that should fit in 4 bytes
                                        if (!hasInvoke(byteCode)) {
                                            bugReporter.reportBug(new BugInstance(this, BugType.OI_OPTIONAL_ISSUES_USES_DELAYED_EXECUTION.name(), LOW_PRIORITY)
                                                    .addClass(this).addMethod(this).addSourceLine(this));
                                        }
                                    }
                                }
                            }
                        }
                    }
                break;
            }
        } catch (ClassNotFoundException e) {
            bugReporter.reportMissingClass(e);
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    private Method getSupplierGetMethod(JavaClass supplier) {
        for (Method method : supplier.getMethods()) {
            if ("get".equals(method.getName()) && "()Ljava/lang/Object;".equals(method.getSignature())) {
                return method;
            }
        }

        return null;
    }

    private boolean hasInvoke(byte[] byteCode) {
        for (byte b : byteCode) {
            if ((b == INVOKEINTERFACE) || (b == INVOKEVIRTUAL) || (b == INVOKESTATIC) || (b == INVOKEDYNAMIC)) {
                return true;
            }
        }

        return false;
    }
}
