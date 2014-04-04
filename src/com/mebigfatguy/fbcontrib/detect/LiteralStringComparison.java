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

import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Method;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for methods that compare strings against literal strings, where the literal string
 * is passed as the parameter. If the .equals or .compareTo is called on the literal itself, passing
 * the variable as the parameter, you avoid the possibility of a NullPointerException.
 * 
 * Updated for 1.7 to not throw false positives for string-based switch statements (which are susceptible to 
 * NPEs).  String-based switch generate String.equals(Constant) bytecodes, and thus, must be accounted for
 */
public class LiteralStringComparison extends BytecodeScanningDetector
{
	//offsets to detect for a string switch
	private static final int HASH_CODE_PC_OFFSET = 3;
	private static final int DUP_PC_OFFSET = 5;
	private static final int STRING_SWITCH_OFFSET = 3;


	private BugReporter bugReporter;
	private OpcodeStack stack;

	private Set<Integer> stringBasedSwitchFalsePositives;

	int lastDupSeen;
	int lastStringHashCodeSeen;


	/**
	 * constructs a LSC detector given the reporter to report bugs on
	 * @param bugReporter the sync of bug reports
	 */
	public LiteralStringComparison(final BugReporter bugReporter) {
		this.bugReporter = bugReporter;		
	}

	/**
	 * implements the visitor to create and clear the stack
	 * 
	 * @param classContext the context object for the currently parsed class
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			stack = new OpcodeStack();

			stringBasedSwitchFalsePositives = new HashSet<Integer>();
			super.visitClassContext(classContext);
		} finally {
			stack = null;
			stringBasedSwitchFalsePositives.clear();
			stringBasedSwitchFalsePositives = null;
		}
	}

	/**
	 * looks for methods that contain a LDC or LDC_W opcodes
	 * 
	 * @param method the context object of the current method
	 * @return if the class loads constants
	 */
	public boolean prescreen(Method method) {
		BitSet bytecodeSet = getClassContext().getBytecodeSet(method);
		return (bytecodeSet != null) && (bytecodeSet.get(Constants.LDC) || bytecodeSet.get(Constants.LDC_W));
	}

	/**
	 * overrides the visitor to reset the opcode stack
	 * 
	 * @param obj the code object for the currently parsed method
	 */
	@Override
	public void visitCode(final Code obj) {
		if (prescreen(getMethod())) {
			stack.resetForMethodEntry(this);
			lastDupSeen=-10;
			lastStringHashCodeSeen=-10;
			stringBasedSwitchFalsePositives.clear();

			super.visitCode(obj);
		}
	}

	/**
	 * looks for strings comparisons where the stack object is a literal
	 * 
	 * @param seen the currently parsed opcode
	 */
	@Override
	public void sawOpcode(final int seen) {
		try {
			stack.precomputation(this);

			if ((seen == INVOKEVIRTUAL) && "java/lang/String".equals(getClassConstantOperand())) {
				handleMethodOnString();						
			} 
			else if (seen == DUP) {
				lastDupSeen = getPC();
			}
			else if (seen == LOOKUPSWITCH) {
				handleLookupSwitch();
			} 
		} finally {
			stack.sawOpcode(this, seen);
		}
	}


	private void handleLookupSwitch() {
		int pc = getPC();
		//This setup, with a dup 5 bytes before and a hashcode call 3 bytes before is a near-sure-fire
		//way to detect a string-based switch
		if (pc-lastStringHashCodeSeen == HASH_CODE_PC_OFFSET && pc - lastDupSeen == DUP_PC_OFFSET) {
			addFalsePositivesForStringSwitch(getSwitchOffsets(),pc);
		}
	}

	private void addFalsePositivesForStringSwitch(int[] switchOffsets, int pc) {
		for (Integer i:switchOffsets) {
			//string-based switches
			stringBasedSwitchFalsePositives.add(pc + i.intValue() + STRING_SWITCH_OFFSET);
		}
	}

	private void handleMethodOnString() {
		String calledMethodName = getNameConstantOperand();
		String calledMethodSig = getSigConstantOperand();

		if (("equals".equals(calledMethodName) && "(Ljava/lang/Object;)Z".equals(calledMethodSig))
				||  ("compareTo".equals(calledMethodName) && "(Ljava/lang/String;)I".equals(calledMethodSig))
				||  ("equalsIgnoreCase".equals(calledMethodName) && "(Ljava/lang/String;)Z".equals(calledMethodSig))) {

			if (stack.getStackDepth() > 0) {
				OpcodeStack.Item itm = stack.getStackItem(0);
				Object constant = itm.getConstant();
				if ((constant != null) && constant.getClass().equals(String.class)) {
					if (stringBasedSwitchFalsePositives.contains(getPC())) {
						System.out.println("Ignoring false positive LSC");
					}
					else {
						bugReporter.reportBug( new BugInstance( this, "LSC_LITERAL_STRING_COMPARISON", HIGH_PRIORITY)  //very confident
						.addClass(this)
						.addMethod(this)
						.addSourceLine(this));
					}

				}
			}
		}
		else if ("hashCode".equals(calledMethodName)) {
			lastStringHashCodeSeen = getPC();
		}
	}

}
