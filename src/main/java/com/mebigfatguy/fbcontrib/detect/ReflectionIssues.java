/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2018 Dave Brosius
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

import org.apache.bcel.Const;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.JavaClass;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.QMethod;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.ba.ClassContext;

public class ReflectionIssues extends BytecodeScanningDetector {

    private static final QMethod SETACCESSIBLE = new QMethod("setAccessible", "(Z)V");
    private static final FQMethod SETACCESSIBLE_ARRAY = new FQMethod("java/lang/reflect/AccessibleObject", "setAccessible",
            "([Ljava/lang/reflect/AccessibleObject;Z)V");
    private static final JavaClass ACCESSIBLE_OBJECT_CLASS;

    static {
        JavaClass cls = null;
        try {
            cls = Repository.lookupClass("java/lang/reflect/AccessibleObject");
        } catch (ClassNotFoundException e) {
            cls = null;
        }
        ACCESSIBLE_OBJECT_CLASS = cls;

    }
    private BugReporter bugReporter;

    public ReflectionIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext clsContext) {
        if (ACCESSIBLE_OBJECT_CLASS != null) {
            super.visitClassContext(clsContext);
        }
    }

    @Override
    public void sawOpcode(int seen) {

        try {
            if (seen == Const.INVOKEVIRTUAL) {
                QMethod m = new QMethod(getNameConstantOperand(), getSigConstantOperand());
                if (SETACCESSIBLE.equals(m)) {

                    JavaClass clz = Repository.lookupClass(getClassConstantOperand());
                    if (clz.instanceOf(ACCESSIBLE_OBJECT_CLASS)) {
                        bugReporter.reportBug(
                                new BugInstance(this, BugType.RFI_SET_ACCESSIBLE.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
                    }
                }

            } else if (seen == Const.INVOKESTATIC) {
                FQMethod m = new FQMethod(getClassConstantOperand(), getNameConstantOperand(), getSigConstantOperand());
                if (SETACCESSIBLE_ARRAY.equals(m)) {
                    bugReporter.reportBug(
                            new BugInstance(this, BugType.RFI_SET_ACCESSIBLE.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
                }
            }
        } catch (ClassNotFoundException e) {
            bugReporter.reportMissingClass(e);
        }
    }
}
