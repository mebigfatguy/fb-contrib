/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2014 Dave Brosius
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

import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.Detector;
import edu.umd.cs.findbugs.ba.AnalysisContext;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for classes that implement interfaces by relying on methods being
 * implemented in super classes, even though the superclass knows nothing about
 * the interface being implemented by the child.
 */
public class SpoiledChildInterfaceImplementor implements Detector {

	private final BugReporter bugReporter;

	/**
     * constructs a SCII detector given the reporter to report bugs on

     * @param bugReporter the sync of bug reports
     */
	public SpoiledChildInterfaceImplementor(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}

	/** looks for classes that implement interfaces but don't provide those methods
	 *
	 * @param classContext the context object of the currently parsed class
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			JavaClass cls = classContext.getJavaClass();

			if (cls.isAbstract() || cls.isInterface())
				return;

			if ("java.lang.Object".equals(cls.getSuperclassName()))
				return;

			JavaClass[] infs = cls.getInterfaces();
			if (infs.length > 0) {
				Set<String> clsMethods = buildMethodSet(cls);
				for (JavaClass inf : infs) {
					Set<String> infMethods = buildMethodSet(inf);
					if (infMethods.size() > 0) {
						infMethods.removeAll(clsMethods);
						if (infMethods.size() > 0) {
                            JavaClass superCls = cls.getSuperClass();
                            filterSuperInterfaceMethods(inf, infMethods, superCls);
                            if (infMethods.size() > 0) {
								if (!superCls.implementationOf(inf)) {
	                                int priority = AnalysisContext.currentAnalysisContext().isApplicationClass(superCls) ? NORMAL_PRIORITY : LOW_PRIORITY;
									BugInstance bi = new BugInstance(this, BugType.SCII_SPOILED_CHILD_INTERFACE_IMPLEMENTOR.name(), priority)
											   .addClass(cls)
											   .addString("Implementing interface: " + inf.getClassName())
											   .addString("Methods:");
									for (String nameSig : infMethods)
										bi.addString("\t" + nameSig);

									bugReporter.reportBug(bi);
									return;
								}
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
	}

	/**
	 * builds a set of all non constructor or static initializer method/signatures
	 *
	 * @param cls the class to build the method set from
	 * @return a set of method names/signatures
	 */
	private static Set<String> buildMethodSet(JavaClass cls) {
		Set<String> methods = new HashSet<String>();

		for (Method m : cls.getMethods()) {
			String methodName = m.getName();
			if (!"<init>".equals(methodName) && !"<clinit>".equals(methodName) && (!"clone".equals(methodName))) {
				methods.add(methodName + ":" + m.getSignature());
			}
		}

		return methods;
	}

	/**
	 * removes methods found in an interface when a super interface having the same methods
	 * is implemented in a parent. While this is somewhat hinky, we'll allow it.
	 *
	 * @param inf the interface to look for super interfaces for
	 * @param infMethods the remaining methods that are needed to be found
	 * @param cls the super class to look for these methods in
	 */
	private void filterSuperInterfaceMethods(JavaClass inf, Set<String> infMethods, JavaClass cls) {
		try {
			if (infMethods.isEmpty())
				return;

			JavaClass[] superInfs = inf.getInterfaces();
			for (JavaClass superInf : superInfs) {
				if (cls.implementationOf(superInf)) {
					Set<String> superInfMethods = buildMethodSet(superInf);
					infMethods.removeAll(superInfMethods);
					if (infMethods.isEmpty())
						return;
				}
				filterSuperInterfaceMethods(superInf, infMethods, cls);
			}
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
			infMethods.clear();
		}
	}



}
