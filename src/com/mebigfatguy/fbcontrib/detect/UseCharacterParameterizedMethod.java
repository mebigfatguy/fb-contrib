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
import java.util.HashMap;
import java.util.Map;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for methods that pass single character string constants as parameters to 
 * methods that alternatively have an overridden method that accepts a character instead.
 * It is easier for the method to handle a single character than a String.
 */
public class UseCharacterParameterizedMethod extends BytecodeScanningDetector 
{
	private static Map<String, Integer> characterMethods = new HashMap<String, Integer>();
	static {
		//characterMethods.put("java/lang/StringBuffer:append:(Ljava/lang/String;)Ljava/lang/StringBuffer;", Values.ZERO);
		//characterMethods.put("java/lang/StringBuilder:append:(Ljava/lang/String;)Ljava/lang/StringBuilder;", Values.ZERO);
		characterMethods.put("java/lang/String:indexOf:(Ljava/lang/String;)I", Values.ZERO);
		characterMethods.put("java/lang/String:indexOf:(Ljava/lang/String;I)I", Values.ONE);
		characterMethods.put("java/lang/String:lastIndexOf:(Ljava/lang/String;)I", Values.ZERO);
		characterMethods.put("java/lang/String:lastIndexOf:(Ljava/lang/String;I)I", Values.ONE);
		//characterMethods.put("java/lang/String:startsWith:(Ljava/lang/String;)Z", Values.ZERO);
		characterMethods.put("java/io/PrintStream:print:(Ljava/lang/String;)V", Values.ZERO);
		characterMethods.put("java/io/PrintStream:println:(Ljava/lang/String;)V", Values.ZERO);
		characterMethods.put("java/io/StringWriter:write:(Ljava/lang/String;)V", Values.ZERO);
	}
	
	private final BugReporter bugReporter;
	private OpcodeStack stack;
	
    /**
     * constructs a UCPM detector given the reporter to report bugs on

     * @param bugReporter the sync of bug reports
     */	
	public UseCharacterParameterizedMethod(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}
	
	/**
	 * overrides the visitor to create and clear the opcode stack
	 * 
	 * @param context the context object for the currently parsed class
	 */
	@Override
	public void visitClassContext(final ClassContext context) {
		try {
			stack = new OpcodeStack();
			if (context.getJavaClass().getMajor() >= Constants.MAJOR_1_5) {
				characterMethods.put("java/lang/StringBuffer:append:(Ljava/lang/String;)Ljava/lang/StringBuffer;", Values.ZERO);
				characterMethods.put("java/lang/StringBuilder:append:(Ljava/lang/String;)Ljava/lang/StringBuilder;", Values.ZERO);
			}
			super.visitClassContext(context);
		} finally {
			stack = null;
			characterMethods.remove("java/lang/StringBuilder:append:(Ljava/lang/String;)Ljava/lang/StringBuilder;");
			characterMethods.remove("java/lang/StringBuffer:append:(Ljava/lang/String;)Ljava/lang/StringBuffer;");
		}
	}
	
	/**
	 * looks for methods that contain a LDC opcode
	 * 
	 * @param obj the context object of the current method
	 * @return if the class uses LDC instructions
	 */

	private boolean prescreen(Method obj) {
		BitSet bytecodeSet = getClassContext().getBytecodeSet(obj);
		return (bytecodeSet != null) && ((bytecodeSet.get(Constants.LDC) || (bytecodeSet.get(Constants.LDC_W))));
	}
	
	/**
	 * implement the visitor prescreen the method, and reset the stack
	 * 
	 * @param obj the context object for the currently parsed method
	 */
	@Override
	public void visitCode(Code obj) {
		if (prescreen(getMethod())) {
			stack.resetForMethodEntry(this);
			super.visitCode(obj);
		}
	}
	
	/**
	 * implements the visitor to look for method calls that pass a constant string as a parameter when
	 * the string is only one character long, and there is an alternate method passing a character.
	 */
	@Override
	public void sawOpcode(int seen) {
		try {
	        stack.precomputation(this);
	        
			if ((seen == INVOKEVIRTUAL) || (seen == INVOKEINTERFACE)) {
				String key = getClassConstantOperand() + ":" + getNameConstantOperand() + ":" + getSigConstantOperand();
				Integer parmPos =characterMethods.get(key);
				if (parmPos != null) {
					int stackPos = parmPos.intValue();
					if (stack.getStackDepth() > stackPos) {
						OpcodeStack.Item itm = stack.getStackItem(stackPos);
						String con = (String)itm.getConstant();
						if ((con != null) && (con.length() == 1)) {
							bugReporter.reportBug(new BugInstance(this, "UCPM_USE_CHARACTER_PARAMETERIZED_METHOD", NORMAL_PRIORITY)
									.addClass(this)
									.addMethod(this)
									.addSourceLine(this));
						}
					}
				}
			}
		} finally {
			stack.sawOpcode(this, seen);
		}
	}
}
