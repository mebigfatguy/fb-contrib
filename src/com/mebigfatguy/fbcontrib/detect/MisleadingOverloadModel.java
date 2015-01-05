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

import java.util.HashMap;
import java.util.Map;

import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.Detector;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XFactory;
import edu.umd.cs.findbugs.visitclass.PreorderVisitor;

/** looks for classes that define both static and instance methods with the same name.
 * This 'overloading' is confusing as one method is instance based the other class based,
 * and points to a confusion in implementation.
 */
public class MisleadingOverloadModel  extends PreorderVisitor implements Detector
{
	enum MethodFoundType {Instance, Static, Both}
	
	private final BugReporter bugReporter;
	
	/**
     * constructs a MOM detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
	 */
	public MisleadingOverloadModel(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}
	
	@Override
	public void visitClassContext(ClassContext classContext) {
		JavaClass cls = classContext.getJavaClass();
		String clsName = cls.getClassName();
		Method[] methods = cls.getMethods();
        Map<String, MethodFoundType> declMethods = new HashMap<String, MethodFoundType>(methods.length);
		for (Method m : methods) {
			String methodName = m.getName();
			boolean report;
			MethodFoundType newType;
			if (m.isStatic()) {
				report = declMethods.get(methodName) == MethodFoundType.Instance;
				if (report)
					newType = MethodFoundType.Both;
				else
					newType = MethodFoundType.Static;
			} else {
				report = declMethods.get(m.getName()) == MethodFoundType.Static;
				if (report)
					newType = MethodFoundType.Both;
				else
					newType = MethodFoundType.Instance;
			}
			
			declMethods.put(methodName, newType);
			if (report) {
				bugReporter.reportBug(new BugInstance(this, BugType.MOM_MISLEADING_OVERLOAD_MODEL.name(), NORMAL_PRIORITY)
							.addClass(cls)
							.addMethod(XFactory.createXMethod(clsName, m))
							.addString(methodName));
			}
		}
	}

	/** implements the visitor to do nothing */
	@Override
	public void report() {
	}
}
