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

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for methods that make a recursive call to itself as the last statement in the
 * method. This tail recursion could be converted into a simple loop which would improve
 * the performance and stack requirements.
 */
public class TailRecursion extends BytecodeScanningDetector 
{
	public static final int TAILRECURSIONFUDGE = 6;
	
	private BugReporter bugReporter;
	private OpcodeStack stack;
	private int trPCPos;
	private boolean possibleTailRecursion;
	private boolean isStatic;
	
    /**
     * constructs a TR detector given the reporter to report bugs on

     * @param bugReporter the sync of bug reports
     */	
	public TailRecursion(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}
	
	/**
	 * implements the visitor to create and clear the stack
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
	 * implements the visitor to figure the pc where the method call must occur
	 * depending on whether the method returns a value, or not.
	 * 
	 * @param obj the context object of the currently parsed method
	 */
	@Override
	public void visitMethod(Method obj) {
		Code c = obj.getCode();
		if (c != null) {
			byte[] opcodes = c.getCode();
			if (opcodes != null) {
				trPCPos = c.getCode().length - 1;
				if (!obj.getSignature().endsWith("V")) {
					trPCPos -= 1;
				}
				trPCPos -= TAILRECURSIONFUDGE;
				possibleTailRecursion = true;
				isStatic = obj.isStatic();
				stack.resetForMethodEntry(this);
				super.visitMethod(obj);
			}
		}
	}
	
	/**
	 * implements the visitor to find methods that employ tail recursion
	 * 
	 * @param seen the opcode of the currently parsed instruction
	 */
	@Override
	public void sawOpcode(int seen) {
		try {
	        stack.precomputation(this);

			if (seen == INVOKEVIRTUAL) {
				boolean isRecursion = (getMethodName().equals(getNameConstantOperand()))
								   && (getMethodSig().equals(getSigConstantOperand()))
								   && (getClassName().equals(getClassConstantOperand()));
				
				if (isRecursion && !isStatic) {
					int numParms = Type.getArgumentTypes(getMethodSig()).length;
					if (stack.getStackDepth() > numParms) {
						OpcodeStack.Item itm = stack.getStackItem(numParms);
						isRecursion = (itm.getRegisterNumber() == 0);
					}
				}
	
				if (isRecursion && possibleTailRecursion && (getPC() >= trPCPos)) {
					bugReporter.reportBug(new BugInstance(this, "TR_TAIL_RECURSION", NORMAL_PRIORITY)
								.addClass(this)
								.addMethod(this)
								.addSourceLine(this));
				}
				else
					possibleTailRecursion = false;
			}
		} finally {
			stack.sawOpcode(this, seen);
		}
	}
}
