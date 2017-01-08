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

import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * Looks for methods that call wait, notify or notifyAll on an instance of a java.lang.Thread. Since the internal workings of the threads is to synchronize on
 * the thread itself, introducing client calls will confuse the thread state of the object in question, and will cause spurious thread state changes, either
 * waking threads up when not intended, or removing the the thread from the runnable state.
 */
public class SpuriousThreadStates extends BytecodeScanningDetector {
    private BugReporter bugReporter;
    private OpcodeStack stack;

    /**
     * constructs a STS detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public SpuriousThreadStates(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
        }
    }

    @Override
    public void visitMethod(Method obj) {
        stack.resetForMethodEntry(this);
        super.visitMethod(obj);
    }

    @Override
    public void sawOpcode(int seen) {
        OpcodeStack.Item itm = null;

        try {
            stack.precomputation(this);

            if (seen == INVOKEVIRTUAL) {
                String className = getClassConstantOperand();
                if (Values.SLASHED_JAVA_LANG_OBJECT.equals(className)) {
                    if (stack.getStackDepth() > 0) {
                        String methodName = getNameConstantOperand();
                        String signature = getSigConstantOperand();
                        if (("wait".equals(methodName) || "notify".equals(methodName) || "notifyAll".equals(methodName)) && SignatureBuilder.SIG_VOID_TO_VOID.equals(signature)) {
                            itm = stack.getStackItem(0);
                        } else if ("wait".equals(methodName)) {
                            if (SignatureBuilder.SIG_LONG_TO_VOID.equals(signature) && (stack.getStackDepth() > 1)) {
                                itm = stack.getStackItem(1);
                            } else if (SignatureBuilder.SIG_LONG_AND_INT_TO_VOID.equals(signature) && (stack.getStackDepth() > 2)) {
                                itm = stack.getStackItem(2);
                            }
                        }
                    }

                    if (itm != null) {
                        JavaClass cls = itm.getJavaClass();
                        boolean found = false;
                        if (cls != null) {
                            if ("java.lang.Thread".equals(cls.getClassName())) {
                                found = true;
                            } else {
                                JavaClass[] supers = cls.getSuperClasses();
                                for (JavaClass jc : supers) {
                                    if ("java.lang.Thread".equals(jc.getClassName())) {
                                        found = true;
                                        break;
                                    }
                                }
                            }
                        }

                        if (found) {
                            bugReporter.reportBug(
                                    new BugInstance(this, "STS_SPURIOUS_THREAD_STATES", NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
                        }
                    }
                }
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        } finally {
            stack.sawOpcode(this, seen);
        }
    }
}
