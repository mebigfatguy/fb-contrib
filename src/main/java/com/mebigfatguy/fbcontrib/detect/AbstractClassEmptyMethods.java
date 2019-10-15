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

import java.util.HashSet;
import java.util.Set;

import org.apache.bcel.Const;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.ConstantString;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.QMethod;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * finds methods of abstract classes that do nothing, or just throw exceptions.
 * Since this is an abstract class, it may be more correct to just leave the
 * method abstract.
 */
public class AbstractClassEmptyMethods extends BytecodeScanningDetector {
    enum State {
        SAW_NOTHING, SAW_NEW, SAW_DUP, SAW_LDC, SAW_INVOKESPECIAL, SAW_DONE
    }

    private final BugReporter bugReporter;
    private JavaClass exceptionClass;
    private Set<QMethod> interfaceMethods;
    private String methodName;
    private State state;

    /**
     * constructs a ACEM detector given the reporter to report bugs on
     *
     * @param bugReporter the sync of bug reports
     */
    public AbstractClassEmptyMethods(BugReporter bugReporter) {
        this.bugReporter = bugReporter;

        try {
            exceptionClass = Repository.lookupClass(Values.SLASHED_JAVA_LANG_EXCEPTION);
        } catch (ClassNotFoundException e) {
            bugReporter.reportMissingClass(e);
        }
    }

    /**
     * overrides the visitor to check for abstract classes.
     *
     * @param classContext the context object that holds the JavaClass being parsed
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            JavaClass cls = classContext.getJavaClass();
            if (cls.isAbstract()) {
                interfaceMethods = collectInterfaceMethods(cls);
                super.visitClassContext(classContext);
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        } finally {
            interfaceMethods = null;
        }
    }

    /**
     * overrides the visitor to grab the method name and reset the state.
     *
     * @param obj the method being parsed
     */
    @Override
    public void visitMethod(Method obj) {
        methodName = obj.getName();
        state = State.SAW_NOTHING;
    }

    /**
     * overrides the visitor to filter out constructors.
     *
     * @param obj the code to parse
     */
    @Override
    public void visitCode(Code obj) {
        if (Values.CONSTRUCTOR.equals(methodName) || Values.STATIC_INITIALIZER.equals(methodName)) {
            return;
        }

        Method m = getMethod();
        if (m.isSynthetic()) {
            return;
        }

        if (!interfaceMethods.contains(new QMethod(methodName, m.getSignature()))) {
            super.visitCode(obj);
        }
    }

    /**
     * overrides the visitor to look for empty methods or simple exception throwers.
     *
     * @param seen the opcode currently being parsed
     */
    @Override
    public void sawOpcode(int seen) {
        try {
            switch (state) {
            case SAW_NOTHING:
                if (seen == Const.RETURN) {
                    bugReporter.reportBug(
                            new BugInstance(this, BugType.ACEM_ABSTRACT_CLASS_EMPTY_METHODS.name(), NORMAL_PRIORITY)
                                    .addClass(this).addMethod(this).addSourceLine(this));
                    state = State.SAW_DONE;
                } else if (seen == Const.NEW) {
                    String newClass = getClassConstantOperand();
                    JavaClass exCls = Repository.lookupClass(newClass);
                    if ((exceptionClass != null) && exCls.instanceOf(exceptionClass)) {
                        state = State.SAW_NEW;
                    } else {
                        state = State.SAW_DONE;
                    }
                } else {
                    state = State.SAW_DONE;
                }
                break;

            case SAW_NEW:
                if (seen == Const.DUP) {
                    state = State.SAW_DUP;
                } else {
                    state = State.SAW_DONE;
                }
                break;

            case SAW_DUP:
                if (((seen == Const.LDC) || (seen == Const.LDC_W))
                        && (getConstantRefOperand() instanceof ConstantString)) {
                    state = State.SAW_LDC;
                } else {
                    state = State.SAW_DONE;
                }
                break;

            case SAW_LDC:
                if ((seen == Const.INVOKESPECIAL) && Values.CONSTRUCTOR.equals(getNameConstantOperand())) {
                    state = State.SAW_INVOKESPECIAL;
                } else {
                    state = State.SAW_DONE;
                }
                break;

            case SAW_INVOKESPECIAL:
                if (seen == Const.ATHROW) {
                    bugReporter.reportBug(
                            new BugInstance(this, BugType.ACEM_ABSTRACT_CLASS_EMPTY_METHODS.name(), NORMAL_PRIORITY)
                                    .addClass(this).addMethod(this).addSourceLine(this));
                }
                state = State.SAW_DONE;
                break;

            case SAW_DONE:
                break;
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
            state = State.SAW_DONE;
        }
    }

    private Set<QMethod> collectInterfaceMethods(JavaClass cls) throws ClassNotFoundException {
        Set<QMethod> methods = new HashSet<>();
        for (JavaClass inf : cls.getAllInterfaces()) {
            for (Method m : inf.getMethods()) {
                methods.add(new QMethod(m.getName(), m.getSignature()));
            }
        }

        return methods;
    }
}
