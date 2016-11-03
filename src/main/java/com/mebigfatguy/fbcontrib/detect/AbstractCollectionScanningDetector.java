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

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;

import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * a base detector class for when you need to precess collections, provides methods for checking collection attributes
 */
public class AbstractCollectionScanningDetector extends BytecodeScanningDetector {
    protected final JavaClass collectionClass;
    protected final BugReporter bugReporter;
    protected OpcodeStack stack;

    AbstractCollectionScanningDetector(BugReporter bugReporter, String collectionClassName) {
        this.bugReporter = bugReporter;
        JavaClass clazz;
        try {
            clazz = Repository.lookupClass(collectionClassName);
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
            clazz = null;
        }
        collectionClass = clazz;
    }

    /**
     * implements the visitor to create and clear the stack, and report missing class errors
     *
     * @param classContext
     *            the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        if (collectionClass == null) {
            return;
        }

        try {
            stack = new OpcodeStack();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
        }
    }

    /**
     * implements the visitor to reset the stack
     *
     * @param obj
     *            the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        super.visitCode(obj);
    }

    /**
     * determines if the stack item refers to a collection that is stored in a local variable
     *
     * @param item
     *            the stack item to check
     *
     * @return the register number of the local variable that this collection refers to, or -1
     * @throws ClassNotFoundException
     *             if the items class cannot be found
     */
    protected final int isLocalCollection(OpcodeStack.Item item) throws ClassNotFoundException {
        Comparable<?> aliasReg = (Comparable<?>) item.getUserValue();
        if (aliasReg instanceof Integer) {
            return ((Integer) aliasReg).intValue();
        }

        int reg = item.getRegisterNumber();
        if (reg < 0) {
            return -1;
        }

        JavaClass cls = item.getJavaClass();
        if ((cls != null) && cls.implementationOf(collectionClass)) {
            return reg;
        }

        return -1;
    }

}
