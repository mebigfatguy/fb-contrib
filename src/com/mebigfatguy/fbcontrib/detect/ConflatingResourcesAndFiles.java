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

import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for methods that conflate the use of resources and files. Converting URLs 
 * retrieved from potentially non file resources, into files objects.
 */
public class ConflatingResourcesAndFiles extends BytecodeScanningDetector {

	private BugReporter bugReporter;
	private OpcodeStack stack;
	
	/**
	 * constructs a CRF detector given the reporter to report bugs on
	 * @param bugReporter the sync of bug reports
	 */
	public ConflatingResourcesAndFiles(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}
	
	/**
	 * overrides the visitor to reset the stack
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
	 * overrides the visitor to resets the stack for this method.
	 * 
	 * @param obj the context object for the currently parsed code block
	 */
	@Override
	public void visitCode(Code obj) {
		stack.resetForMethodEntry(this);
		super.visitCode(obj);
	}
	
	/**
	 * overrides the visitor to look conflated use of resources and files
	 */
	@Override
	public void sawOpcode(int seen) {
		try {
			stack.precomputation(this);
			
		} finally {
			stack.sawOpcode(this, seen);
		}
	}
}
