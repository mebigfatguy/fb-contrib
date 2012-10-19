/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2012 Dave Brosius
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

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.Detector;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.visitclass.PreorderVisitor;

/**
 * finds classes that implement clone() that do not specialize the return value, and do
 * not swallow CloneNotFoundException. Not doing so makes the clone method not as simple
 * to use, and should be harmless to do.
 */
public class CloneUsability extends PreorderVisitor implements Detector {

    private static JavaClass CLONE_CLASS;

    static {
        try {
            CLONE_CLASS = Repository.lookupClass("java.lang.Cloneable");
        } catch (ClassNotFoundException cnfe) {
            CLONE_CLASS = null;
        }
    }

    private BugReporter bugReporter;
    private String clsName;

    /**
     * constructs a CU detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
     */
    public CloneUsability(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * overrides the visitor to check for classes that implement Cloneable.
     *
     * @param classContext the context object that holds the JavaClass being parsed
     */
    public void visitClassContext(ClassContext classContext) {
        try {
            JavaClass cls = classContext.getJavaClass();
            if (cls.implementationOf(CLONE_CLASS)) {
                clsName = cls.getClassName();
                cls.accept(this);
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        }
    }

    /**
     * overrides the visitor to grab the method name and reset the state.
     *
     * @param obj the method being parsed
     */
    @Override
    public void visitMethod(Method obj) {
        if (obj.isPublic() && obj.getName().equals("clone") && (obj.getArgumentTypes().length == 0)) {

            String returnClsName = obj.getReturnType().getSignature();
            returnClsName = returnClsName.substring(1, returnClsName.length() - 1).replaceAll("/", ".");
            if (!clsName.equals(returnClsName))
            {
                bugReporter.reportBug(new BugInstance(this, "CU_CLONE_USABILITY_OBJECT_RETURN", NORMAL_PRIORITY)
                            .addClass(this)
                            .addMethod(this));
            }
            if (obj.getExceptionTable().getLength() > 0) {
                bugReporter.reportBug(new BugInstance(this, "CU_CLONE_USABILITY_THROWS", NORMAL_PRIORITY)
                .addClass(this)
                .addMethod(this));
            }
        }
    }

    /**
     * implements the Detector with a nop
     */
    public void report() {
    }
}
