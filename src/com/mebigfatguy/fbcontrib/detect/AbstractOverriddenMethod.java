/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2015 Dave Brosius
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

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.Detector;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.visitclass.PreorderVisitor;

/**
 * finds methods that are declared abstract but override concrete methods in a superclass.
 */
public class AbstractOverriddenMethod extends PreorderVisitor implements Detector {
	private BugReporter bugReporter;
	private ClassContext clsContext;
	private JavaClass[] superClasses;
	
	/**
     * constructs a AOM detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
	 */
	public AbstractOverriddenMethod(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}
	
	/**
	 * implements the detector to collect the super classes
	 * 
	 * @param classContext the context object for the currently parsed class
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			clsContext = classContext;
			JavaClass cls = classContext.getJavaClass();
			if (cls.isInterface())
				return;
			superClasses = cls.getSuperClasses();
			cls.accept(this);
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
		} finally {
			clsContext = null;
			superClasses = null;
		}
	}
	
	/**
	 * overrides the visitor to find abstract methods that override concrete ones
	 * 
	 * @param obj the context object of the currently parsed method
	 */
	@Override
	public void visitMethod(Method obj) {
		if (!obj.isAbstract())
			return;
		
		String methodName = obj.getName();
		String methodSig = obj.getSignature();
		outer: for (JavaClass cls : superClasses) {
			Method[] methods = cls.getMethods();
			for (Method m : methods) {
				if (m.isPrivate() || m.isAbstract())
					continue;
				if (methodName.equals(m.getName()) && methodSig.equals(m.getSignature())) {
					BugInstance bug = new BugInstance(this, BugType.AOM_ABSTRACT_OVERRIDDEN_METHOD.name(), NORMAL_PRIORITY)
									.addClass(this)
									.addMethod(this);
					
					Code code = obj.getCode();
					if (code != null)
						bug.addSourceLineRange(clsContext, this, 0, code.getLength()-1);
					bugReporter.reportBug(bug);
					
					break outer;
				}
			}
		}		
	}
	
	/**
	 * implements the Detector with a nop
	 */
	@Override
	public void report() {
	}
}
