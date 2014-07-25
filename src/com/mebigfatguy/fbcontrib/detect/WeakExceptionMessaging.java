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
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.ConstantString;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

@CustomUserValue
public class WeakExceptionMessaging extends BytecodeScanningDetector {

	private static JavaClass exceptionClass;
	private static final Set<String> ignorableExceptionTypes = new HashSet<String>();

	static {
		try {
			exceptionClass = Repository.lookupClass("java/lang/Exception");
		} catch (ClassNotFoundException cnfe) {
			exceptionClass = null;
		}

		ignorableExceptionTypes.add("java.lang.UnsupportedOperationException");
	}

	private final BugReporter bugReporter;
	private OpcodeStack stack;

	/**
     * constructs a WEM detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
	 */
	public WeakExceptionMessaging(BugReporter bugReporter) {
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
			if (exceptionClass != null) {
				stack = new OpcodeStack();
				super.visitClassContext(classContext);
			}
		} finally {
			stack = null;
		}
	}

	/**
	 * looks for methods that contain a ATHROW opcodes
	 *
	 * @param method the context object of the current method
	 * @return if the class uses throws
	 */
	public boolean prescreen(Method method) {
		BitSet bytecodeSet = getClassContext().getBytecodeSet(method);
		return (bytecodeSet != null) && (bytecodeSet.get(Constants.ATHROW));
	}

	/**
	 * overrides the visitor to prescreen the method to look for throws calls
	 * and only forward onto bytecode scanning if there
	 *
	 * @param obj the context object of the currently parsed code block
	 */
	@Override
	public void visitCode(Code obj) {
		Method method = getMethod();
		if (!method.getSignature().startsWith("()")) {
			if (prescreen(method)) {
				stack.resetForMethodEntry(this);
				super.visitCode(obj);
			}
		}
	}

	/**
	 * overrides the visitor to look for throws instructions using exceptions with
	 * static messages
	 *
	 * @param seen the opcode of the currently visited instruction
	 */
	@Override
	public void sawOpcode(int seen) {
		boolean allConstantStrings = false;
		boolean sawConstant = false;
		try {
	        stack.precomputation(this);
	        
			if (seen == ATHROW) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item item = stack.getStackItem(0);
					if (item.getUserValue() != null) {
						JavaClass exClass = item.getJavaClass();
						if ((exClass == null) || !ignorableExceptionTypes.contains(exClass.getClassName())) {
							bugReporter.reportBug(new BugInstance(this, "WEM_WEAK_EXCEPTION_MESSAGING", LOW_PRIORITY)
									   .addClass(this)
									   .addMethod(this)
									   .addSourceLine(this));
						}
					}
				}
			} else if ((seen == LDC) || (seen == LDC_W)) {
				if (getConstantRefOperand() instanceof ConstantString)
					sawConstant = true;
			} else if (seen == INVOKESPECIAL) {
				if ("<init>".equals(getNameConstantOperand())) {
					String clsName = getClassConstantOperand();
					if (clsName.indexOf("Exception") >= 0) {
						JavaClass exCls = Repository.lookupClass(clsName);
						if (exCls.instanceOf(exceptionClass)) {
							String sig = getSigConstantOperand();
							Type[] argTypes = Type.getArgumentTypes(sig);
							int stringParms = 0;
							for (int t = 0; t < argTypes.length; t++) {
								if ("Ljava/lang/String;".equals(argTypes[t].getSignature())) {
									stringParms++;
									int stackOffset = argTypes.length - t - 1;
									if (stack.getStackDepth() > stackOffset) {
										OpcodeStack.Item item = stack.getStackItem(stackOffset);
										if (item.getUserValue() == null)
											return;
									}
								}
							}
							allConstantStrings = stringParms > 0;
						}
					}
				}
			}
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
		} finally {
			TernaryPatcher.pre(stack, seen);
			stack.sawOpcode(this, seen);
			TernaryPatcher.post(stack, seen);
			if ((sawConstant || allConstantStrings) && (stack.getStackDepth() > 0)) {
				OpcodeStack.Item item = stack.getStackItem(0);
				item.setUserValue(Boolean.TRUE);
			}
		}
	}
}
