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

import java.util.BitSet;

import org.apache.bcel.Constants;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.ExceptionTable;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * finds classes that implement clone() that do not specialize the return value,
 * and do not swallow CloneNotFoundException. Not doing so makes the clone
 * method not as simple to use, and should be harmless to do.
 */
public class CloneUsability extends BytecodeScanningDetector {

    private static JavaClass CLONE_CLASS;

    static {
        try {
            CLONE_CLASS = Repository.lookupClass("java/lang/Cloneable");
        } catch (ClassNotFoundException cnfe) {
            CLONE_CLASS = null;
        }
    }

    private BugReporter bugReporter;
    private JavaClass cls;
    private String clsName;
    private OpcodeStack stack;
    private boolean throwsCNFE;

    /**
     * constructs a CU detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public CloneUsability(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * overrides the visitor to check for classes that implement Cloneable.
     *
     * @param classContext
     *            the context object that holds the JavaClass being parsed
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            cls = classContext.getJavaClass();
            if (cls.implementationOf(CLONE_CLASS)) {
                clsName = cls.getClassName();
                stack = new OpcodeStack();
                super.visitClassContext(classContext);
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        } finally {
            cls = null;
            stack = null;
        }
    }

    /**
     * overrides the visitor to grab the method name and reset the state.
     *
     * @param obj
     *            the method being parsed
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "FCBL_FIELD_COULD_BE_LOCAL", justification = "False positives occur when state is maintained across callbacks")
    @Override
    public void visitCode(Code obj) {
        try {
            Method m = getMethod();
            if (m.isPublic() && !m.isSynthetic() && "clone".equals(m.getName()) && (m.getArgumentTypes().length == 0)) {

                String returnClsName = m.getReturnType().getSignature();
                returnClsName = SignatureUtils.stripSignature(returnClsName);
                if (!clsName.equals(returnClsName)) {
                    if (Values.DOTTED_JAVA_LANG_OBJECT.equals(returnClsName)) {
                        bugReporter.reportBug(
                                new BugInstance(this, BugType.CU_CLONE_USABILITY_OBJECT_RETURN.name(), NORMAL_PRIORITY).addClass(this).addMethod(this));
                    } else {
                        JavaClass cloneClass = Repository.lookupClass(returnClsName);
                        if (!cls.instanceOf(cloneClass)) {
                            bugReporter.reportBug(
                                    new BugInstance(this, BugType.CU_CLONE_USABILITY_MISMATCHED_RETURN.name(), HIGH_PRIORITY).addClass(this).addMethod(this));
                        }
                    }
                }

                ExceptionTable et = m.getExceptionTable();

                if ((et != null) && (et.getLength() > 0)) {

                    throwsCNFE = false;
                    if (prescreen(m)) {
                        stack.resetForMethodEntry(this);
                        super.visitCode(obj);
                    }

                    if (!throwsCNFE) {
                        bugReporter.reportBug(new BugInstance(this, BugType.CU_CLONE_USABILITY_THROWS.name(), NORMAL_PRIORITY).addClass(this).addMethod(this));
                    }
                }
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        }
    }

    /**
     * overrides the visitor to look for a CloneNotSupported being thrown
     *
     * @param seen the currently parsed opcode
     */
    @Override
    public void sawOpcode(int seen) {
        try {
            if ((seen == ATHROW) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item item = stack.getStackItem(0);
                if ("Ljava/lang/CloneNotSupportedException;".equals(item.getSignature())) {
                    throwsCNFE = true;
                }
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    /**
     * looks for methods that contain a THROW opcode
     *
     * @param method
     *            the context object of the current method
     * @return if the class throws exceptions
     */
    private boolean prescreen(Method method) {
        BitSet bytecodeSet = getClassContext().getBytecodeSet(method);
        return (bytecodeSet != null) && bytecodeSet.get(Constants.ATHROW);
    }
}
