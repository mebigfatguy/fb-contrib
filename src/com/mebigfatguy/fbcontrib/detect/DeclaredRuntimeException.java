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

import java.util.HashSet;
import java.util.Set;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.ExceptionTable;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.Detector;
import edu.umd.cs.findbugs.StringAnnotation;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.visitclass.PreorderVisitor;

/**
 * looks for methods that declare Runtime exceptions in their throws clause. While doing
 * so is not illegal, it may represent a misunderstanding as to the exception in question.
 * If a RuntimeException is declared, it implies that this exception type is expected to happen,
 * which if true, should be handled in code, and not propogated.
 */
public class DeclaredRuntimeException extends PreorderVisitor implements Detector
{
	private final BugReporter bugReporter;
	private static final Set<String> runtimeExceptions = new HashSet<String>();
	private static JavaClass runtimeExceptionClass;
	
	static {
		try {
			runtimeExceptionClass = Repository.lookupClass("java.lang.RuntimeException");
			runtimeExceptions.add("java.lang.RuntimeException");
		} catch (ClassNotFoundException cnfe) {
			runtimeExceptionClass = null;
		}
	}
	
	/**
     * constructs a DRE detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
	 */
	public DeclaredRuntimeException(final BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}
	
	/**
	 * overrides the visitor and accepts if the Exception class was loaded
	 * 
	 * @param classContext the context object for the currently parsed class
	 */
	public void visitClassContext(final ClassContext classContext) {
		if (runtimeExceptionClass != null)
			classContext.getJavaClass().accept(this);
	}
	
	/**
	 * overrides the visitor to find declared runtime exceptions
	 * 
	 * @param obj the method object of the currently parsed method
	 */
	@Override
	public void visitMethod(final Method obj) {
		ExceptionTable et = obj.getExceptionTable();
		if (et != null) {
			String[] exNames = et.getExceptionNames();
			Set<String> methodRTExceptions = new HashSet<String>();
			int priority = LOW_PRIORITY;
			boolean foundRuntime = false;
			for (String ex : exNames) {
				boolean isRuntime = false;
				if (runtimeExceptions.contains(ex))
					isRuntime = true;	
				else {
					try {
						JavaClass exClass = Repository.lookupClass(ex);
						if (exClass.instanceOf(runtimeExceptionClass)) {
							runtimeExceptions.add(ex);
							if (ex.startsWith("java.lang."))
								priority = NORMAL_PRIORITY;
							isRuntime = true;
						}
					} catch (ClassNotFoundException cnfe) {
							bugReporter.reportMissingClass(cnfe);
					}
				}
				if (isRuntime) {
					foundRuntime = true;
					methodRTExceptions.add(ex);
				}
			}
		
			if (foundRuntime) {	
				BugInstance bug = new BugInstance(this, "DRE_DECLARED_RUNTIME_EXCEPTION", priority)
									.addClass(this)
									.addMethod(this);
				
				for (String ex : methodRTExceptions) {
					bug.add(new StringAnnotation(ex));
				}
							
				bugReporter.reportBug(bug);
			}
		}
	}

	/**
	 * implementation of the detector, with null implementation
	 */
	public void report() {
	}

}
