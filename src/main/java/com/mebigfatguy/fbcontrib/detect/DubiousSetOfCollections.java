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

import org.apache.bcel.Const;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.internalAnnotations.SlashedClassName;

/**
 * looks for uses of sets or keySets of maps that contain other collections. As
 * collection typically implement hashCode, equals and compareTo by iterating
 * the contents of the collection this can be costly from a performance point of
 * view.
 */
public class DubiousSetOfCollections extends BytecodeScanningDetector {

    private static JavaClass collectionCls;
    private static JavaClass setCls;
    private static JavaClass mapCls;

    static {
        try {
            collectionCls = Repository.lookupClass(Values.SLASHED_JAVA_UTIL_COLLECTION);
            setCls = Repository.lookupClass(Values.SLASHED_JAVA_UTIL_SET);
            mapCls = Repository.lookupClass(Values.SLASHED_JAVA_UTIL_MAP);
        } catch (ClassNotFoundException cnfe) {
            // np bugReporter yet, so ignore
        }
    }

    private final BugReporter bugReporter;
    private OpcodeStack stack;

    /**
     * constructs a DSOC detector given the reporter to report bugs on
     *
     * @param bugReporter the sync of bug reports
     */
    public DubiousSetOfCollections(final BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implement the visitor to set up the opcode stack, and make sure that
     * collection, set and map classes could be loaded.
     *
     * @param clsContext the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext clsContext) {
        try {
            if ((collectionCls == null) || (setCls == null) || (mapCls == null)) {
                return;
            }

            stack = new OpcodeStack();
            super.visitClassContext(clsContext);
        } finally {
            stack = null;
        }
    }

    /**
     * implements the visitor to reset the opcode stack
     *
     * @param code the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code code) {
        stack.resetForMethodEntry(this);
        super.visitCode(code);
    }

    /**
     * implements the visitor look for adds to sets or puts to maps where the
     * element to be added is a collection.
     *
     * @param seen the opcode of the currently parsed instruction
     */
    @Override
    public void sawOpcode(int seen) {
        try {
            stack.precomputation(this);

            if ((seen == Const.INVOKEVIRTUAL) || (seen == Const.INVOKEINTERFACE)) {
                String clsName = getClassConstantOperand();
                String methodName = getNameConstantOperand();
                String signature = getSigConstantOperand();

                if ("add".equals(methodName) && SignatureBuilder.SIG_OBJECT_TO_BOOLEAN.equals(signature)
                        && isImplementationOf(clsName, setCls)) {
                    if (stack.getStackDepth() > 1) {
                        OpcodeStack.Item item = stack.getStackItem(0);
                        JavaClass entryCls = item.getJavaClass();
                        if (isImplementationOf(entryCls, collectionCls)) {
                            bugReporter.reportBug(new BugInstance(this, BugType.DSOC_DUBIOUS_SET_OF_COLLECTIONS.name(),
                                    NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
                        }
                    }
                } else if ("put".equals(methodName) && SignatureBuilder.SIG_TWO_OBJECTS_TO_OBJECT.equals(signature)
                        && isImplementationOf(clsName, setCls) && (stack.getStackDepth() > 2)) {
                    OpcodeStack.Item item = stack.getStackItem(1);
                    JavaClass entryCls = item.getJavaClass();
                    if (isImplementationOf(entryCls, collectionCls)) {
                        bugReporter.reportBug(
                                new BugInstance(this, BugType.DSOC_DUBIOUS_SET_OF_COLLECTIONS.name(), NORMAL_PRIORITY)
                                        .addClass(this).addMethod(this).addSourceLine(this));
                    }
                }
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    /**
     * returns whether the class implements the interface
     *
     * @param clsName the name of the class
     * @param inf     the interface to check
     * @return if the class implements the interface
     */
    private boolean isImplementationOf(@SlashedClassName String clsName, JavaClass inf) {

        try {
            if (clsName.startsWith("java/lang/")) {
                return false;
            }

            JavaClass cls = Repository.lookupClass(clsName);
            return isImplementationOf(cls, inf);
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        }
        return false;
    }

    /**
     * returns whether the class implements the interface
     *
     * @param cls the class
     * @param inf the interface to check
     * @return if the class implements the interface
     */
    private boolean isImplementationOf(JavaClass cls, JavaClass inf) {
        try {
            if (cls == null) {
                return false;
            }
            if (cls.implementationOf(inf)) {
                return true;
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        }
        return false;
    }

}
