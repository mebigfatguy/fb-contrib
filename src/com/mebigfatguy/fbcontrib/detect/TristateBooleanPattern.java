/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2011 Dave Brosius
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
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for methods that are defined to return Boolean, but return null. This thus
 * allows three return values, Boolean.FALSE, Boolean.TRUE and null. If three values
 * intended, it would be more clear to just create an enumeration with three values
 * and return that type.
 */
public class TristateBooleanPattern extends BytecodeScanningDetector 
{
	private BugReporter bugReporter;
	private OpcodeStack stack;
	private boolean methodReported;
	
	/**
     * constructs a TBP detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
	 */
	public TristateBooleanPattern(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}
	
	/**
	 * implements the visitor to allocate the opcode stack
	 * 
	 * @param classContext the context object of the currently parsed class
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			stack = new OpcodeStack();
			super.visitClassContext(classContext);
		} finally {
			stack = null;
		}
	}
	
	/**
	 * implements the visitor to filter out methods that don't return Boolean,
	 * and to reset the methodReported flag
	 *
	 * @param obj the context object for the currently parsed code block
	 */
	@Override
	public void visitCode(Code obj) {
		Method m = getMethod();
		Type retType = m.getReturnType();
		if ("Ljava/lang/Boolean;".equals(retType.getSignature())) {
			stack.resetForMethodEntry(this);
			methodReported = false;
			super.visitCode(obj);
		}
	}
	
	/**
	 * implements the visitor to look for null returns
	 */
	@Override
	public void sawOpcode(int seen) {
		try {
			if (methodReported)
				return;
			
			if (seen == ARETURN) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item item = stack.getStackItem(0);
					if (item.isNull()) {
						bugReporter.reportBug(new BugInstance(this, "TBP_TRISTATE_BOOLEAN_PATTERN", NORMAL_PRIORITY)
						           .addClass(this)
						           .addMethod(this)
						           .addSourceLine(this));
						methodReported = true;
					}
				}
			}
		} finally {
			stack.sawOpcode(this, seen);
		}
	}

}
