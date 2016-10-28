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

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantString;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
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
 * looks for appending strings inside of calls to StringBuffer or StringBuilder append.
 */
@CustomUserValue
public class InefficientStringBuffering extends BytecodeScanningDetector {
    private enum AppendType {
        CLEAR, NESTED, TOSTRING
    };

    private BugReporter bugReporter;
    private OpcodeStack stack;
    private boolean sawLDCEmpty;

    /**
     * constructs a ISB detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public InefficientStringBuffering(final BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to create an clear the stack
     *
     * @param classContext
     *            the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
        }
    }

    /**
     * implements the visitor to create and clear the stack
     *
     * @param obj
     *            the context object of the currently parsed code block
     */
    @Override
    public void visitCode(final Code obj) {
        if (obj.getCode() != null) {
            stack.resetForMethodEntry(this);
            sawLDCEmpty = false;
            super.visitCode(obj);
        }
    }

    @Override
    public void sawOpcode(final int seen) {
        ISBUserValue userValue = null;

        try {
            stack.precomputation(this);

            if (seen == INVOKESPECIAL) {
                userValue = sawInvokeSpecial();
            } else if (seen == INVOKEVIRTUAL) {
                if (sawLDCEmpty) {
                    dealWithEmptyString();
                }
                userValue = sawInvokeVirtual();

            } else if ((seen == GOTO) || (seen == GOTO_W)) {
                int depth = stack.getStackDepth();
                for (int i = 0; i < depth; i++) {
                    OpcodeStack.Item itm = stack.getStackItem(i);
                    itm.setUserValue(null);
                }
            } else if ((seen == LDC) || (seen == LDC_W)) {
                Constant c = getConstantRefOperand();
                if (c instanceof ConstantString) {
                    String s = ((ConstantString) c).getBytes(getConstantPool());
                    if (s.length() == 0) {
                        sawLDCEmpty = true;
                    }
                }
            } else if (OpcodeUtils.isALoad(seen)) {
                userValue = new ISBUserValue(AppendType.CLEAR, true);
            }
        } finally {
            handleOpcode(seen);
            if ((userValue != null) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item itm = stack.getStackItem(0);
                itm.setUserValue(userValue);
            }
        }
    }

    private void handleOpcode(final int seen) {
        TernaryPatcher.pre(stack, seen);
        stack.sawOpcode(this, seen);
        TernaryPatcher.post(stack, seen);
    }

    private ISBUserValue sawInvokeVirtual() {
        ISBUserValue userValue = null;
        String calledClass = getClassConstantOperand();

        if (Values.isAppendableStringClassName(calledClass)) {
            String methodName = getNameConstantOperand();
            if ("append".equals(methodName)) {
                OpcodeStack.Item itm = getStringBufferItemAt(1);
                if (itm != null) {
                    userValue = (ISBUserValue) itm.getUserValue();
                }

                if (stack.getStackDepth() > 0) {
                    itm = stack.getStackItem(0);
                    ISBUserValue uv = (ISBUserValue) itm.getUserValue();
                    if (uv != null) {
                        switch (uv.getAppendType()) {
                            case NESTED:
                                bugReporter.reportBug(new BugInstance(this, BugType.ISB_INEFFICIENT_STRING_BUFFERING.name(),
                                        "toString".equals(getMethodName()) ? LOW_PRIORITY : NORMAL_PRIORITY).addClass(this).addMethod(this)
                                                .addSourceLine(this));
                            break;
                            case TOSTRING:
                                if (stack.getStackDepth() > 1) {
                                    itm = stack.getStackItem(1);
                                    if (itm != null) {
                                        uv = (ISBUserValue) itm.getUserValue();

                                        if ((uv != null) && uv.hasResolvedString()) {
                                            bugReporter.reportBug(new BugInstance(this, BugType.ISB_TOSTRING_APPENDING.name(), NORMAL_PRIORITY).addClass(this)
                                                    .addMethod(this).addSourceLine(this));
                                        }
                                    }
                                }
                            break;
                            default:
                            break;
                        }
                    }
                }

                if (getSigConstantOperand().startsWith("(Ljava/lang/String;)")) {
                    if (userValue == null) {
                        userValue = new ISBUserValue(AppendType.CLEAR, true);
                    } else {
                        userValue = new ISBUserValue(userValue.getAppendType(), true);
                    }
                }
            } else if ("toString".equals(methodName)) {
                OpcodeStack.Item itm = getStringBufferItemAt(0);
                if (itm != null) {
                    userValue = (ISBUserValue) itm.getUserValue();
                }
            }
        } else if ("toString".equals(getNameConstantOperand()) && "()Ljava/lang/String;".equals(getSigConstantOperand())
        // calls to this.toString() are okay, some people like to be explicit
                && (stack.getStackDepth() > 0) && (stack.getStackItem(0).getRegisterNumber() != 0)) {
            userValue = new ISBUserValue(AppendType.TOSTRING);
        }
        return userValue;
    }

    private void dealWithEmptyString() {
        String calledClass = getClassConstantOperand();
        if (Values.isAppendableStringClassName(calledClass) && "append".equals(getNameConstantOperand())
                && getSigConstantOperand().startsWith("(Ljava/lang/String;)") && (stack.getStackDepth() > 1)) {
            OpcodeStack.Item sbItm = stack.getStackItem(1);
            if ((sbItm != null) && (sbItm.getUserValue() == null)) {
                OpcodeStack.Item itm = stack.getStackItem(0);
                Object cons = itm.getConstant();
                if ((cons instanceof String) && (itm.getRegisterNumber() < 0) && ((String) cons).isEmpty()) {
                    bugReporter.reportBug(new BugInstance(this, BugType.ISB_EMPTY_STRING_APPENDING.name(), LOW_PRIORITY).addClass(this).addMethod(this)
                            .addSourceLine(this));
                }
            }
        }
    }

    private ISBUserValue sawInvokeSpecial() {
        ISBUserValue userValue = null;
        String calledClass = getClassConstantOperand();

        if (Values.isAppendableStringClassName(calledClass)
                && Values.CONSTRUCTOR.equals(getNameConstantOperand())) {
            String signature = getSigConstantOperand();
            if ("()V".equals(signature)) {
                OpcodeStack.Item itm = getStringBufferItemAt(2);
                if (itm != null) {
                    userValue = new ISBUserValue(AppendType.NESTED);
                }
            } else if ("(Ljava/lang/String;)V".equals(signature) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item itm = stack.getStackItem(0);
                userValue = (ISBUserValue) itm.getUserValue();
                if ((userValue != null) && (userValue.getAppendType() == AppendType.NESTED)) {
                    bugReporter.reportBug(new BugInstance(this, BugType.ISB_INEFFICIENT_STRING_BUFFERING.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                            .addSourceLine(this));
                }

                if (userValue == null) {
                    userValue = new ISBUserValue(AppendType.CLEAR, true);
                }
            }
        }
        return userValue;
    }

    private OpcodeStack.Item getStringBufferItemAt(int depth) {
        if (stack.getStackDepth() > depth) {
            OpcodeStack.Item itm = stack.getStackItem(depth);
            String signature = itm.getSignature();
            if ("Ljava/lang/StringBuffer;".equals(signature) || "Ljava/lang/StringBuilder;".equals(signature)) {
                return itm;
            }
        }

        return null;
    }

    static class ISBUserValue {

        private AppendType appendType;
        private boolean hasResolvedString;

        public ISBUserValue(AppendType appType) {
            this(appType, false);
        }

        public ISBUserValue(AppendType appType, boolean resolved) {
            appendType = appType;
            hasResolvedString = resolved;
        }

        public AppendType getAppendType() {
            return appendType;
        }

        public boolean hasResolvedString() {
            return hasResolvedString;
        }

        @Override
        public int hashCode() {
            return appendType.hashCode() ^ (hasResolvedString ? 1 : 0);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ISBUserValue)) {
                return false;
            }

            ISBUserValue that = (ISBUserValue) obj;
            return (appendType == that.appendType) && (hasResolvedString == that.hasResolvedString);
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }

    }
}
