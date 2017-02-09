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

import java.util.ArrayDeque;
import java.util.Deque;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.ToString;

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
    private Deque<ActiveStackOp> activeStackOps;

    public OptionalIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        JavaClass cls = classContext.getJavaClass();

        if (cls.getMajor() >= Constants.MAJOR_1_8) {
            try {
                stack = new OpcodeStack();
                activeStackOps = new ArrayDeque<>();
                super.visitClassContext(classContext);
            } finally {
                activeStackOps = null;
                stack = null;
            }
        }
    }

    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        activeStackOps.clear();
        super.visitCode(obj);
    }

    @Override
    public void sawOpcode(int seen) {
        FQMethod curCalledMethod = null;

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

                case INVOKESTATIC:
                case INVOKEINTERFACE:
                case INVOKESPECIAL:
                    // case INVOKEDYNAMIC:
                    curCalledMethod = new FQMethod(getClassConstantOperand(), getNameConstantOperand(), getSigConstantOperand());
                break;

                case INVOKEVIRTUAL:
                    curCalledMethod = new FQMethod(getClassConstantOperand(), getNameConstantOperand(), getSigConstantOperand());
                    if (OR_ELSE.equals(curCalledMethod)) {
                        if (stack.getStackDepth() > 0) {
                            OpcodeStack.Item itm = stack.getStackItem(0);
                            if ((itm.getReturnValueOf() != null) && !isTrivialStackOps()) {
                                bugReporter.reportBug(new BugInstance(this, BugType.OI_OPTIONAL_ISSUES_USES_IMMEDIATE_EXECUTION.name(), NORMAL_PRIORITY)
                                        .addClass(this).addMethod(this).addSourceLine(this));
                            }
                        }
                    } else if (OR_ELSE_GET.equals(curCalledMethod)) {
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
            if (stack.getStackDepth() == 0) {
                activeStackOps.clear();
            } else {
                activeStackOps.addLast(new ActiveStackOp(seen, curCalledMethod));
            }
        }
    }

    /**
     * returns whether the set of operations that contributed to the current stack form, are trivial or not, specifically boxing a primitive value, or appending
     * to strings or such.
     *
     * @return
     */
    private boolean isTrivialStackOps() {
        return false;
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

    static class ActiveStackOp {
        private int opcode;
        FQMethod method;

        public ActiveStackOp(int op) {
            opcode = op;
        }

        public ActiveStackOp(int op, FQMethod calledMethod) {
            opcode = op;
            method = calledMethod;
        }

        public int getOpcode() {
            return opcode;
        }

        public FQMethod getMethod() {
            return method;
        }

        @Override
        public int hashCode() {
            return opcode ^ ((method != null) ? method.hashCode() : -1);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ActiveStackOp)) {
                return false;
            }

            ActiveStackOp that = (ActiveStackOp) o;

            return (opcode == that.opcode) && ((method == that.method) || ((method != null) && method.equals(that.method)));
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }

    }
}
