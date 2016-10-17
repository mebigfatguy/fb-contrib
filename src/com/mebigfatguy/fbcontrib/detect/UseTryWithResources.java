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
import org.apache.bcel.classfile.CodeException;

import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for try/finally blocks that manage resources, without using try-with-resources
 */
public class UseTryWithResources extends BytecodeScanningDetector {

    private BugReporter bugReporter;
    private OpcodeStack stack;

    public UseTryWithResources(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {

        try {
            int majorVersion = classContext.getJavaClass().getMajor();

            if (majorVersion >= MAJOR_1_7) {
                stack = new OpcodeStack();
                super.visitClassContext(classContext);
            }
        } finally {
            stack = null;
        }
    }

    @Override
    public void visitCode(Code obj) {
        if (prescreen(obj)) {
            stack.resetForMethodEntry(this);
            super.visitCode(obj);
        }
    }

    @Override
    public void sawOpcode(int seen) {
        try {

        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    private boolean prescreen(Code obj) {
        CodeException[] ces = obj.getExceptionTable();
        if ((ces == null) || (ces.length == 0)) {
            return false;
        }

        for (CodeException ce : ces) {
            if (ce.getCatchType() == 0) {
                return true;
            }
        }

        return false;
    }
}
