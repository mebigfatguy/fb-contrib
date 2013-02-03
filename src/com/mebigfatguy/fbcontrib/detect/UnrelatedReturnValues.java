/*
 * fb-contrib - Auxiliary detectors for Java programs
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.SignatureUtils;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for methods that return Object, and who's code body returns two or more
 * different types of objects that are unrelated (other than by Object).
 */
public class UnrelatedReturnValues extends BytecodeScanningDetector
{
	private final BugReporter bugReporter;
	private OpcodeStack stack;
	private JavaClass currentClass;
	private Map<JavaClass, Integer> returnTypes;

	/**
     * constructs a URV detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
	 */
	public UnrelatedReturnValues(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}

	/**
	 * implements the visitor to create and destroy the stack and return types
	 *
	 * @param classContext the context object of the currently parsed class
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			currentClass = classContext.getJavaClass();
			stack = new OpcodeStack();
			returnTypes = new HashMap<JavaClass, Integer>();
			super.visitClassContext(classContext);
		}
		finally {
			currentClass = null;
			stack = null;
			returnTypes = null;
		}
	}
	/**
	 * implements the visitor to see if the method returns Object, and if the method
	 * is defined in a superclass, or interface.
	 *
	 * @param obj the context object of the currently parsed code block
	 */
	@Override
	public void visitCode(Code obj) {
		try {
			Method m = getMethod();
			String signature = m.getSignature();
			if (signature.endsWith(")Ljava/lang/Object;")) {
				stack.resetForMethodEntry(this);
				returnTypes.clear();
				super.visitCode(obj);
				if (returnTypes.size() > 1) {
		            String methodName = m.getName();
	                boolean isInherited = SignatureUtils.isInheritedMethod(currentClass, methodName, signature);

					int priority = NORMAL_PRIORITY;
					for (JavaClass cls : returnTypes.keySet()) {
						if ((cls != null) && "java.lang.Object".equals(cls.getClassName())) {
							priority = LOW_PRIORITY;
							break;
						}
					}

					JavaClass cls = findCommonType(returnTypes.keySet());
					BugInstance bug;
					if ((cls != null) && !isInherited) {
						bug = new BugInstance(this, "URV_CHANGE_RETURN_TYPE", priority)
								.addClass(this)
								.addMethod(this);
						bug.addString(cls.getClassName());
					} else if (!isInherited) {
						bug = new BugInstance(this, "URV_UNRELATED_RETURN_VALUES", priority)
								.addClass(this)
								.addMethod(this);
					} else {
						bug = new BugInstance(this, "URV_INHERITED_METHOD_WITH_RELATED_TYPES", priority)
						.addClass(this)
						.addMethod(this);
						if (cls != null)
							bug.addString(cls.getClassName());
					}
					if (bug != null) {
						for (Integer pc : returnTypes.values()) {
							bug.addSourceLine(this, pc.intValue());
						}
						bugReporter.reportBug(bug);
					}
				}
			}
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
		}
	}

	/**
	 * implements the visitor to find return values where the types of objects returned from the
	 * method are related only by object.
	 *
	 * @param seen the opcode of the currently parsed instruction
	 */
	@Override
	public void sawOpcode(int seen) {
		try {
			stack.mergeJumps(this);
			if (seen == ARETURN) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item itm = stack.getStackItem(0);
					if (!itm.isNull())
						returnTypes.put(itm.getJavaClass(), Integer.valueOf(getPC()));
				}
			}
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
		} finally {
			stack.sawOpcode(this, seen);
		}
	}

	/**
	 * looks for a common superclass or interface for all the passed in types
	 *
	 * @param classes the set of classes to look for a common super class or interface
	 * @return the type that is the common interface or superclass (not Object, tho).
	 */
	public JavaClass findCommonType(Set<JavaClass> classes) throws ClassNotFoundException {
		Set<JavaClass> possibleCommonTypes = new HashSet<JavaClass>();

		boolean populate = true;
		for (JavaClass cls : classes) {
			if (cls == null) //array
				return null;

			if ("java/lang/Object".equals(cls.getClassName()))
				continue;

			JavaClass[] infs = cls.getAllInterfaces();
			JavaClass[] supers = cls.getSuperClasses();
			if (populate) {
				possibleCommonTypes.addAll(Arrays.asList(infs));
				possibleCommonTypes.addAll(Arrays.asList(supers));
				possibleCommonTypes.remove(Repository.lookupClass("java/lang/Object"));
				populate = false;
			} else {
				Set<JavaClass> retain = new HashSet<JavaClass>();
				retain.addAll(Arrays.asList(infs));
				retain.addAll(Arrays.asList(supers));
				possibleCommonTypes.retainAll(retain);
			}
		}

		if (possibleCommonTypes.isEmpty())
			return null;

		for (JavaClass cls : possibleCommonTypes) {
			if (cls.isInterface())
				return cls;
		}
		return possibleCommonTypes.iterator().next();
	}
}
