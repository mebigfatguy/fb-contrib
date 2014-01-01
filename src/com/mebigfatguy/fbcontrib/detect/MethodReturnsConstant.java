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

import java.util.HashMap;
import java.util.Map;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for private methods that can only return one constant value.
 * either the class should not return a value, or perhaps a branch was missed.
 */
@CustomUserValue
public class MethodReturnsConstant extends BytecodeScanningDetector
{
	private final BugReporter bugReporter;
	private OpcodeStack stack;
	private int returnRegister;
	private Map<Integer, Object> registerConstants;
	private Object returnConstant;
	private boolean methodSuspect;
	private int returnPC;

	/**
     * constructs a MRC detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
	 */
	public MethodReturnsConstant(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}

	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			stack = new OpcodeStack();
			registerConstants = new HashMap<Integer, Object>();
			super.visitClassContext(classContext);
		} finally {
			stack = null;
			registerConstants = null;
		}
	}

	/**
	 * implements the visitor to reset the stack and proceed for private methods
	 *
	 * @param obj the context object of the currently parsed code block
	 */
	@Override
	public void visitCode(Code obj) {
		Method m = getMethod();
		int aFlags = m.getAccessFlags();
		if ((((aFlags & Constants.ACC_PRIVATE) != 0) || ((aFlags & Constants.ACC_STATIC) != 0))
		&&  ((aFlags & Constants.ACC_SYNTHETIC) == 0)
		&&  (!m.getSignature().endsWith(")Z"))) {
			stack.resetForMethodEntry(this);
			returnRegister = -1;
			returnConstant = null;
			registerConstants.clear();
			methodSuspect = true;
			returnPC = -1;
			super.visitCode(obj);
			if (methodSuspect && (returnConstant != null)) {
				BugInstance bi = new BugInstance(this, "MRC_METHOD_RETURNS_CONSTANT", ((aFlags & Constants.ACC_PRIVATE) != 0) ? NORMAL_PRIORITY : LOW_PRIORITY)
									.addClass(this)
									.addMethod(this);
				if (returnPC >= 0) {
					bi.addSourceLine(this, returnPC);
				}

				bi.addString(returnConstant.toString());
				bugReporter.reportBug(bi);
			}
		}
	}

	/**
	 * implements the visitor to look for methods that return a constant
	 *
	 * @param seen the opcode of the currently parsed instruction
	 */
	@Override
	public void sawOpcode(int seen) {
		boolean sawSBToString = false;
		try {
			if (!methodSuspect) {
				return;
			}

	        stack.precomputation(this);
			if ((seen >= IRETURN) && (seen <= ARETURN)) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item item = stack.getStackItem(0);

					int register = item.getRegisterNumber();
					if (registerConstants.containsKey(register) && (registerConstants.get(register) == null)) {
                        methodSuspect = false;
                        return;
                    }

					Object constant = item.getConstant();
					if (constant == null) {
						methodSuspect = false;
						return;
					}
					if (Boolean.TRUE.equals(item.getUserValue()) && ("".equals(constant))) {
						methodSuspect = false;
						return;
					}
					if ((returnConstant != null) && (!returnConstant.equals(constant))) {
						methodSuspect = false;
						return;
					}

					returnRegister = item.getRegisterNumber();
					returnConstant = constant;
				}
			} else if ((seen == GOTO) || (seen == GOTO_W)) {
				if (stack.getStackDepth() > 0) {
					methodSuspect = false; //Trinaries confuse us too much, if the code has a ternary well - oh well
				}
			} else if (seen == INVOKEVIRTUAL) {
				String clsName = getClassConstantOperand();
				if (clsName.startsWith("java/lang/StringB")) {
					sawSBToString = "toString".equals(getNameConstantOperand());
				}
			} else if ((seen >= ISTORE) && (seen <= ASTORE_3) || (seen == IINC)) {
			    int register = getRegisterOperand();
			    if ((returnRegister != -1) && (register == returnRegister)) {
			        methodSuspect = false;
			    }

			    if (stack.getStackDepth() > 0) {
			        OpcodeStack.Item item = stack.getStackItem(0);
			        Object constant = item.getConstant();
			        if (registerConstants.containsKey(register)) {
			            if ((constant == null) || !constant.equals(registerConstants.get(register))) {
			                registerConstants.put(register, null);
			            }
			        } else {
			            registerConstants.put(register, constant);
			        }
			    } else {
			        registerConstants.put(register, null);
			    }

			    if (returnRegister == register) {
			        Object constant = registerConstants.get(returnRegister);
			        if (constant != null) {
			            methodSuspect = false;
			        }
			    }
			}


		} finally {
			TernaryPatcher.pre(stack, seen);
			stack.sawOpcode(this, seen);
			TernaryPatcher.post(stack, seen);
			if (sawSBToString && (stack.getStackDepth() > 0)) {
				OpcodeStack.Item item = stack.getStackItem(0);
				item.setUserValue(Boolean.TRUE);
			}
		}
	}
}
