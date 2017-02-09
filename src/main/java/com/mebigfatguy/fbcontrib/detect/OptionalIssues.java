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
import java.util.BitSet;
import java.util.Deque;
import java.util.Set;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.ConstantInvokeDynamic;
import org.apache.bcel.classfile.ConstantNameAndType;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.ConstantUtf8;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

public class OptionalIssues extends BytecodeScanningDetector {

    private Set<String> BOXED_OPTIONAL_TYPES = UnmodifiableSet.create("java/lang/Integer", "java/lang/Long", "java/lang/Double");

    private static final FQMethod OPTIONAL_OR_ELSE_METHOD = new FQMethod("java/util/Optional", "orElse", "(Ljava/lang/Object;)Ljava/lang/Object;");
    private static final FQMethod OPTIONAL_OR_ELSE_GET_METHOD = new FQMethod("java/util/Optional", "orElseGet",
            "(Ljava/util/function/Supplier;)Ljava/lang/Object;");
    private static final FQMethod OPTIONAL_GET_METHOD = new FQMethod("java/util/Optional", "get", "()Ljava/lang/Object;");

    private static final Set<FQMethod> OR_ELSE_METHODS = UnmodifiableSet.create(
    // @formatter:off
        OPTIONAL_OR_ELSE_METHOD,
        new FQMethod("java/util/OptionalDouble", "orElse", "(D)D"),
        new FQMethod("java/util/OptionalInt", "orElse", "(I)I"),
        new FQMethod("java/util/OptionalLong", "orElse", "(J)J")
    // @formatter:on
    );

    private static final Set<FQMethod> OR_ELSE_GET_METHODS = UnmodifiableSet.create(
    // @formatter:off
        OPTIONAL_OR_ELSE_GET_METHOD,
        new FQMethod("java/util/OptionalDouble", "orElseGet", "(Ljava/util/function/DoubleSupplier;)D"),
        new FQMethod("java/util/OptionalInt", "orElseGet", "(Ljava/util/function/IntSupplier;)I"),
        new FQMethod("java/util/OptionalLong", "orElseGet", "(Ljava/util/function/LongSupplier;)J")
    // @formatter:on
    );

    private static final BitSet INVOKE_OPS = new BitSet();
    private BugReporter bugReporter;
    private OpcodeStack stack;
    private JavaClass currentClass;
    private Deque<ActiveStackOp> activeStackOps;

    static {
        INVOKE_OPS.set(INVOKEINTERFACE);
        INVOKE_OPS.set(INVOKEVIRTUAL);
        INVOKE_OPS.set(INVOKESTATIC);
        INVOKE_OPS.set(INVOKESPECIAL);
        INVOKE_OPS.set(INVOKEDYNAMIC);
    }

    public OptionalIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        currentClass = classContext.getJavaClass();

        if (currentClass.getMajor() >= Constants.MAJOR_1_8) {
            try {
                stack = new OpcodeStack();
                activeStackOps = new ArrayDeque<>();
                super.visitClassContext(classContext);
            } finally {
                activeStackOps = null;
                stack = null;
            }
        }
        currentClass = null;
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
        Boolean sawPlainOptional = null;

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

                case INVOKEDYNAMIC:
                    // smells like a hack. Not sure how to do this better
                    ConstantInvokeDynamic id = (ConstantInvokeDynamic) getConstantRefOperand();
                    ConstantPool cp = getConstantPool();
                    ConstantNameAndType nameAndType = (ConstantNameAndType) cp.getConstant(id.getNameAndTypeIndex());
                    ConstantUtf8 typeConstant = (ConstantUtf8) cp.getConstant(nameAndType.getSignatureIndex());
                    curCalledMethod = new FQMethod(getClassName(), "lambda$" + id.getBootstrapMethodAttrIndex(), typeConstant.getBytes());
                break;

                case INVOKESTATIC:
                case INVOKEINTERFACE:
                case INVOKESPECIAL:
                    curCalledMethod = new FQMethod(getClassConstantOperand(), getNameConstantOperand(), getSigConstantOperand());
                break;

                case INVOKEVIRTUAL:
                    curCalledMethod = new FQMethod(getClassConstantOperand(), getNameConstantOperand(), getSigConstantOperand());
                    if (OR_ELSE_METHODS.contains(curCalledMethod)) {
                        if (stack.getStackDepth() > 0) {
                            OpcodeStack.Item itm = stack.getStackItem(0);
                            if ((itm.getReturnValueOf() != null) && !isTrivialStackOps()) {
                                bugReporter.reportBug(new BugInstance(this, BugType.OI_OPTIONAL_ISSUES_USES_IMMEDIATE_EXECUTION.name(), NORMAL_PRIORITY)
                                        .addClass(this).addMethod(this).addSourceLine(this));
                            }
                        }
                        if (OPTIONAL_OR_ELSE_METHOD.equals(curCalledMethod)) {
                            sawPlainOptional = true;
                        }
                    } else if (OR_ELSE_GET_METHODS.contains(curCalledMethod)) {
                        if (!activeStackOps.isEmpty()) {
                            ActiveStackOp op = activeStackOps.getLast();

                            Method getMethod = getLambdaMethod(op.getMethod().getMethodName());
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
                        if (OPTIONAL_OR_ELSE_GET_METHOD.equals(curCalledMethod)) {
                            sawPlainOptional = true;
                        }
                    } else if (OPTIONAL_GET_METHOD.equals(curCalledMethod)) {
                        sawPlainOptional = true;
                    }
                break;

                case CHECKCAST:
                    if (BOXED_OPTIONAL_TYPES.contains(getClassConstantOperand())) {
                        if (stack.getStackDepth() > 0) {
                            OpcodeStack.Item itm = stack.getStackItem(0);
                            if (itm.getUserValue() != null) {
                                bugReporter.reportBug(new BugInstance(this, BugType.OI_OPTIONAL_ISSUES_PRIMITIVE_VARIANT_PREFERRED.name(), LOW_PRIORITY)
                                        .addClass(this).addMethod(this).addSourceLine(this));
                            }
                        }
                    }
                break;
            }
        } finally {
            stack.sawOpcode(this, seen);
            if (stack.getStackDepth() == 0) {
                activeStackOps.clear();
            } else {
                activeStackOps.addLast(new ActiveStackOp(seen, curCalledMethod));
                if (sawPlainOptional != null) {
                    OpcodeStack.Item itm = stack.getStackItem(0);
                    itm.setUserValue(sawPlainOptional);
                }
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

        int invokeCount = 0;
        for (ActiveStackOp op : activeStackOps) {
            if (INVOKE_OPS.get(op.getOpcode())) {
                invokeCount++;
            }
        }

        if (invokeCount == 1) {
            FQMethod method = activeStackOps.getLast().getMethod();
            if (method.getMethodName().equals("valueOf") && (method.getClassName().startsWith("java/lang/"))) {
                return true;
            }
        }

        // do simple string appending?

        return false;
    }

    private Method getLambdaMethod(String methodName) {
        for (Method method : currentClass.getMethods()) {
            if (methodName.equals(method.getName())) {
                return method;
            }
        }

        return null;
    }

    private boolean hasInvoke(byte[] byteCode) {
        for (byte b : byteCode) {
            if (INVOKE_OPS.get(b & 0x00FF)) {
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
