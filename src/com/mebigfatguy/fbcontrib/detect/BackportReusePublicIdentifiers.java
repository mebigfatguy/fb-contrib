/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2016 Bhaskar Maddala
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

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.JavaClass;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.bcel.OpcodeStackDetector;

/**
 * Detects use of backport libraries, when the code in question is compiled in a jdk that has the functionality available. Libraries include:
 * <ul>
 * <li>Use of Backport concurrent classes. Updated/Efficient version of these classes are available in versions of the JDK 5.0 and higher, and these classes
 * should only be used if you are targeting JDK 1.4 and lower.</li>
 * <li>Use of ThreeTen time backport classes, instead of java.time packages which are now available in JDK 8.0 and higher, and these classes should only be used
 * if you are targeting JDK1.7 and lower
 *
 */
public class BackportReusePublicIdentifiers extends OpcodeStackDetector {

    enum Library {
        EMORY, THREETEN
    };

    private static final String EMORY_BACKPORT_PACKAGE = "edu/emory/mathcs/backport/";
    private static final String THREETEN_BACKPORT_PACKAGE = "org/threeten/bp/";
    private final BugReporter bugReporter;
    private int clsVersion;

    /**
     * constructs a BRPI detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public BackportReusePublicIdentifiers(final BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * overrides the visitor to make sure this is a 'modern' class better than 1.4
     *
     * @param classContext
     *            the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        JavaClass cls = classContext.getJavaClass();
        clsVersion = cls.getMajor();
        super.visitClassContext(classContext);
    }

    /**
     * overrides the visitor to look for method calls to the emory backport concurrent library, or threeten bp library when the now built-in versions are
     * available
     *
     * @param seen
     *            the currently parsed opcode
     */
    @Override
    public void sawOpcode(int seen) {
        stack.precomputation(this);

        switch (seen) {
            case INVOKESTATIC:
            case INVOKEVIRTUAL:
            case INVOKEINTERFACE: {
                String className = getClassConstantOperand();
                if (className.startsWith(EMORY_BACKPORT_PACKAGE)) {
                    if (clsVersion >= Constants.MAJOR_1_5) {
                        reportBug(Library.EMORY);
                    }
                } else if (className.startsWith(THREETEN_BACKPORT_PACKAGE)) {
                    if (clsVersion >= Constants.MAJOR_1_8) {
                        reportBug(Library.THREETEN);
                    }
                }
            }
            break;

            case INVOKESPECIAL: {
                String className = getClassConstantOperand();
                String methodName = getNameConstantOperand();
                if (Values.CONSTRUCTOR.equals(methodName)) {
                    if (className.startsWith(EMORY_BACKPORT_PACKAGE)) {
                        if (clsVersion >= Constants.MAJOR_1_5) {
                            reportBug(Library.EMORY);
                        }
                    } else if (className.startsWith(THREETEN_BACKPORT_PACKAGE)) {
                        if (clsVersion >= Constants.MAJOR_1_8) {
                            reportBug(Library.THREETEN);
                        }
                    }
                }
            }
            break;
            default:
            break;
        }
    }

    /**
     * issues a new bug at this location
     */
    private void reportBug(Library library) {
        bugReporter.reportBug(new BugInstance(this, BugType.BRPI_BACKPORT_REUSE_PUBLIC_IDENTIFIERS.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                .addSourceLine(this).addString(library.name()));
    }
}
