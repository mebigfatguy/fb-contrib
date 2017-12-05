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
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.collect.MethodInfo;
import com.mebigfatguy.fbcontrib.collect.Statistics;
import com.mebigfatguy.fbcontrib.utils.AnnotationUtils;
import com.mebigfatguy.fbcontrib.utils.BugType;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for common problems with the application of annotations
 */
@CustomUserValue
public class AnnotationIssues extends BytecodeScanningDetector {

    private BugReporter bugReporter;
    private OpcodeStack stack;
    private boolean methodIsNullable;

    /**
     * constructs a AI detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public AnnotationIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            if (classContext.getJavaClass().getMajor() >= Constants.MAJOR_1_5) {
                if (!classContext.getJavaClass().isAnonymous()) {
                    stack = new OpcodeStack();
                    super.visitClassContext(classContext);
                }
            }
        } finally {
            stack = null;
        }
    }

    @Override
    public void visitCode(Code obj) {

        Method method = getMethod();
        String sig = method.getSignature();
        char returnTypeChar = sig.charAt(sig.indexOf(')') + 1);
        if ((returnTypeChar != 'L') && (returnTypeChar != '[')) {
            return;
        }

        if (AnnotationUtils.methodHasNullableAnnotation(method)) {
            return;
        }

        MethodInfo methodInfo = Statistics.getStatistics().getMethodStatistics(getClassName(), method.getName(), method.getSignature());
        if (methodInfo.getCanReturnNull()) {
            bugReporter.reportBug(new BugInstance(this, BugType.AI_ANNOTATION_ISSUES_NEEDS_NULLABLE.name(), LOW_PRIORITY).addClass(this).addMethod(this));
        } else {
            methodIsNullable = false;
            stack.resetForMethodEntry(this);
            super.visitCode(obj);
            if (methodIsNullable) {
                bugReporter.reportBug(new BugInstance(this, BugType.AI_ANNOTATION_ISSUES_NEEDS_NULLABLE.name(), LOW_PRIORITY).addClass(this).addMethod(this));
            }
        }
    }

    @Override
    public void sawOpcode(int seen) {
        if (methodIsNullable) {
            return;
        }

        boolean resultIsNullable = false;

        try {
            switch (seen) {
                case ARETURN: {
                    if (!methodIsNullable && (stack.getStackDepth() > 0)) {
                        OpcodeStack.Item itm = stack.getStackItem(0);
                        methodIsNullable = AnnotationUtils.isStackElementNullable(getClassName(), getMethod(), itm);
                    }
                    break;
                }

                case INVOKESTATIC:
                case INVOKEINTERFACE:
                case INVOKEVIRTUAL: {
                    resultIsNullable = (AnnotationUtils.isMethodNullable(getClassConstantOperand(), getNameConstantOperand(), getSigConstantOperand()));
                    break;
                }

            }
        } finally {
            stack.sawOpcode(this, seen);
            if ((resultIsNullable) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item itm = stack.getStackItem(0);
                itm.setUserValue(AnnotationUtils.NULLABLE.TRUE);
            }
        }
    }
}
