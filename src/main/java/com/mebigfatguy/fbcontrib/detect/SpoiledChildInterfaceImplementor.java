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

import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.QMethod;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.Detector;
import edu.umd.cs.findbugs.ba.AnalysisContext;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for classes that implement interfaces by relying on methods being implemented in super classes, even though the superclass knows nothing about the
 * interface being implemented by the child.
 */
public class SpoiledChildInterfaceImplementor implements Detector {

    private static final Set<QMethod> OBJECT_METHODS = UnmodifiableSet.create(
    // @formatter:off
            new QMethod("equals", "(Ljava/lang/Object;)Z"),
            new QMethod(Values.HASHCODE, "()I"),
            new QMethod(Values.TOSTRING, "()Ljava/lang/String;"),
            new QMethod("clone", "()Ljava/lang/Object;"),
            new QMethod("notify", "()V"),
            new QMethod("notifyAll", "()V"),
            new QMethod("wait", "(J)V"),
            new QMethod("wait", "(JI)V"),
            new QMethod("wait", "()V")
    // @formatter:on
    );
    private final BugReporter bugReporter;

    /**
     * constructs a SCII detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public SpoiledChildInterfaceImplementor(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * looks for classes that implement interfaces but don't provide those methods
     *
     * @param classContext
     *            the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            JavaClass cls = classContext.getJavaClass();

            if (cls.isAbstract() || cls.isInterface()) {
                return;
            }

            if (Values.DOTTED_JAVA_LANG_OBJECT.equals(cls.getSuperclassName())) {
                return;
            }

            JavaClass[] infs = cls.getInterfaces();
            if (infs.length > 0) {
                Set<QMethod> clsMethods = buildMethodSet(cls);
                for (JavaClass inf : infs) {
                    Set<QMethod> infMethods = buildMethodSet(inf);
                    if (!infMethods.isEmpty()) {
                        infMethods.removeAll(clsMethods);

                        if (!infMethods.isEmpty()) {
                            JavaClass superCls = cls.getSuperClass();
                            filterSuperInterfaceMethods(inf, infMethods, superCls);
                            if (!infMethods.isEmpty() && !superCls.implementationOf(inf)) {
                                int priority = AnalysisContext.currentAnalysisContext().isApplicationClass(superCls) ? NORMAL_PRIORITY : LOW_PRIORITY;
                                BugInstance bi = new BugInstance(this, BugType.SCII_SPOILED_CHILD_INTERFACE_IMPLEMENTOR.name(), priority).addClass(cls)
                                        .addString("Implementing interface: " + inf.getClassName()).addString("Methods:");
                                for (QMethod methodInfo : infMethods) {
                                    bi.addString('\t' + methodInfo.toString());
                                }

                                bugReporter.reportBug(bi);
                                return;
                            }
                        }
                    }
                }
            }

        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        }
    }

    /**
     * required for implementing the interface
     */
    @Override
    public void report() {
        // Unused, requirement of the Detector interface
    }

    /**
     * builds a set of all non constructor or static initializer method/signatures
     *
     * @param cls
     *            the class to build the method set from
     * @return a set of method names/signatures
     */
    private static Set<QMethod> buildMethodSet(JavaClass cls) {
        Set<QMethod> methods = new HashSet<>();

        boolean isInterface = cls.isInterface();

        for (Method m : cls.getMethods()) {
            boolean isDefaultInterfaceMethod = isInterface && !m.isAbstract();

            boolean isSyntheticForParentCall;

            if (m.isSynthetic()) {
                BitSet bytecodeSet = ClassContext.getBytecodeSet(cls, m);
                isSyntheticForParentCall = (bytecodeSet != null) && bytecodeSet.get(Constants.INVOKESPECIAL);
            } else {
                isSyntheticForParentCall = false;
            }

            if (!isSyntheticForParentCall && !isDefaultInterfaceMethod) {
                String methodName = m.getName();
                QMethod methodInfo = new QMethod(methodName, m.getSignature());

                if (!OBJECT_METHODS.contains(methodInfo)) {
                    if (!Values.CONSTRUCTOR.equals(methodName) && !Values.STATIC_INITIALIZER.equals(methodName)) {
                        methods.add(methodInfo);
                    }
                }
            }
        }

        return methods;
    }

    /**
     * removes methods found in an interface when a super interface having the same methods is implemented in a parent. While this is somewhat hinky, we'll
     * allow it.
     *
     * @param inf
     *            the interface to look for super interfaces for
     * @param infMethods
     *            the remaining methods that are needed to be found
     * @param cls
     *            the super class to look for these methods in
     */
    private void filterSuperInterfaceMethods(JavaClass inf, Set<QMethod> infMethods, JavaClass cls) {
        try {
            if (infMethods.isEmpty()) {
                return;
            }

            JavaClass[] superInfs = inf.getInterfaces();
            for (JavaClass superInf : superInfs) {
                if (cls.implementationOf(superInf)) {
                    Set<QMethod> superInfMethods = buildMethodSet(superInf);
                    infMethods.removeAll(superInfMethods);
                    if (infMethods.isEmpty()) {
                        return;
                    }
                }
                filterSuperInterfaceMethods(superInf, infMethods, cls);
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
            infMethods.clear();
        }
    }

    @Override
    public String toString() {
        return ToString.build(this);
    }
}
