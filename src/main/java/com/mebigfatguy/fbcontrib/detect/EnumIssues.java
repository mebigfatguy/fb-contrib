/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2019 Dave Brosius
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
import org.apache.bcel.classfile.JavaClass;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

public class EnumIssues extends BytecodeScanningDetector {
    private final BugReporter bugReporter;
    private OpcodeStack stack;
    private String clsName;
    private boolean isEnum;
    private boolean inEnumInitializer;
    private int numEnumValues;

    /**
     * constructs a ENMI detector given the reporter to report bugs on.
     *
     * @param bugReporter the sync of bug reports
     */
    public EnumIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * overrides the visitor to see if this class is an enum
     *
     * @param classContext the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            JavaClass cls = classContext.getJavaClass();
            isEnum = cls.isEnum();
            clsName = cls.getClassName();
            numEnumValues = 0;
            super.visitClassContext(classContext);
        } finally {
            stack = null;
        }
    }

    /**
     * overrides the visitor to reset the stack
     *
     * @param obj the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);

        inEnumInitializer = isEnum && getMethod().getName().equals(Values.STATIC_INITIALIZER);
        super.visitCode(obj);

        if (inEnumInitializer && numEnumValues <= 1) {
            bugReporter.reportBug(new BugInstance(this, BugType.ENMI_ONE_ENUM_VALUE.name(), NORMAL_PRIORITY)
                    .addClass(this).addMethod(this).addSourceLine(this));
        }
    }

    @Override
    public void sawOpcode(int seen) {
        try {
            if (inEnumInitializer) {
                if (seen == PUTSTATIC) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    String sig = item.getSignature();
                    if (clsName.equals(SignatureUtils.stripSignature(sig))) {
                        numEnumValues++;
                    }

                }
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }
}
