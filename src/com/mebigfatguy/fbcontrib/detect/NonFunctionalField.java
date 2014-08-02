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

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.Detector;
import edu.umd.cs.findbugs.Priorities;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.visitclass.PreorderVisitor;

/**
 * looks for fields in serializable classes that are defined as both final and
 * transient. As a transient field is not initialized when streamed, and is not 
 * initialized in a constructor, it will remain null because it is defined final.
 */
public class NonFunctionalField extends PreorderVisitor implements Detector {
	
	private static JavaClass serializableClass;
	
	static {
		try {
			serializableClass = Repository.lookupClass("java/io/Serializable");
		} catch (ClassNotFoundException cnfe) {
			serializableClass = null;
		}
	}
	
	private BugReporter bugReporter;
	/**
     * constructs a NFF detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
	 */
	public NonFunctionalField(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}
	
	/**
	 * checks to see if the class is Serializable, then looks for fields
	 * that are both final and transient
	 * @param classContext the context object of the currently parsed class
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			JavaClass cls = classContext.getJavaClass();
			if ((serializableClass != null) && (cls.implementationOf(serializableClass))) {
				Field[] fields = cls.getFields();
				setupVisitorForClass(cls);
				for (Field f : fields) {
					if (!f.isStatic() && f.isFinal() && f.isTransient()) {
						bugReporter.reportBug(new BugInstance(this, "NFF_NON_FUNCTIONAL_FIELD", Priorities.NORMAL_PRIORITY)
						           .addClass(this)
						           .addField(cls.getClassName(), f.getName(), f.getSignature(), f.getAccessFlags()));
					}
				}
			}
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
		}
	}

	@Override
	public void report() {
	}
}
