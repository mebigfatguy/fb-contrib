/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2013 Bhaskar Maddala
 * Copyright (C) 2005-2013 Dave Brosius
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
import org.apache.bcel.classfile.JavaClass;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.bcel.OpcodeStackDetector;

/**
 * Detects use of Backport concurrent classes. Updated/Efficient version of
 * these classes are available in versions of the JDK 5.0 and higher, and these
 * classes should only be used if you are targeting JDK 1.4 and lower.
 * 
 * Finds usage of classes from backport utils package.
 */
public class BackportReusePublicIdentifiers extends OpcodeStackDetector {

    private final BugReporter bugReporter;

    public BackportReusePublicIdentifiers(final BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }
    
    @Override
    public void visitClassContext(ClassContext classContext) {
        JavaClass cls = classContext.getJavaClass();
        if (cls.getMajor() >= Constants.MAJOR_1_5) {
            super.visitClassContext(classContext);
        }
    }

    @Override
    public void sawOpcode(int seen) {
        switch (seen) {
        case INVOKESTATIC: {
            String className = getClassConstantOperand();
            if (className.startsWith("edu/emory/mathcs/backport/")) {
                reportBug();
            }
        }
            break;
        case INVOKESPECIAL: {
            String className = getClassConstantOperand();
            String methodName = getNameConstantOperand();
            if (className.startsWith("edu/emory/mathcs/backport/")
                    && methodName.equals("<init>")) {
                reportBug();
            }
        }
            break;
        }
    }

    private void reportBug() {
        bugReporter.reportBug(new BugInstance(this,
                "BRPI_BACKPORT_REUSE_PUBLIC_IDENTIFIERS", NORMAL_PRIORITY)
                .addClass(this).addMethod(this).addSourceLine(this));
    }
}
