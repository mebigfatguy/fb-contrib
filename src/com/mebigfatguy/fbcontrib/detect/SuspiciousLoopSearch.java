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
import java.util.BitSet;
import java.util.HashMap;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.detect.SuspiciousNullGuard.NullGuard;

import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;


public class SuspiciousLoopSearch extends BytecodeScanningDetector {
	
	private BugReporter bugReporter;
	private OpcodeStack stack;

	
    /**
     * constructs an SLS detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
     */
    public SuspiciousLoopSearch(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }
    
	/**
	 * overrides the visitor to initialize and tear down the opcode stack
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
	 * overrides the visitor to reset the stack
	 *
	 * @param obj the context object of the currently parsed code block
	 */
	@Override
	public void visitCode(Code obj) {
		if (prescreen(getMethod())) {
			stack.resetForMethodEntry(this);
			super.visitCode(obj);
		}
	}
	
	/**
	 * implements the visitor to find continuations after finding a 
	 * search result in a loop.
	 * 
	 * @param seen the currently visitor opcode
	 */
	@Override
	public void sawOpcode(int seen) {
		try {
			
		} finally {
			stack.sawOpcode(this, seen);
		}
		
	}
	
    /**
	 * looks for methods that contain a GOTO opcodes
	 * 
	 * @param method the context object of the current method
	 * @return if the class uses synchronization
	 */
	private boolean prescreen(Method method) {
		BitSet bytecodeSet = getClassContext().getBytecodeSet(method);
		return (bytecodeSet != null) && bytecodeSet.get(Constants.GOTO);
	}
    
}
