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
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for methods that synchronize on variables that are not owned by the
 * current class. Doing this causes confusion when two classes use the same variable
 * for their own synchronization purposes. For cleanest separation of interests, only
 * synchronize on private fields of the class. Note that 'this' is not owned by
 * the current class and synchronization on 'this' should be avoided as well.
 */
@CustomUserValue
public class NonOwnedSynchronization extends BytecodeScanningDetector
{
	private static final Integer OWNED = Integer.valueOf(Integer.MAX_VALUE);
	private static final Integer LOW = Integer.valueOf(LOW_PRIORITY);
	private static final Integer NORMAL = Integer.valueOf(NORMAL_PRIORITY);
	private BugReporter bugReporter;
	private OpcodeStack stack;
	private Map<Integer, Integer> regPriorities;

	/**
     * constructs a NOS detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
	 */
	public NonOwnedSynchronization(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}

    /**
     * implements the visitor to set and clear the stack and priorities
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            regPriorities = new HashMap<Integer, Integer>();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            regPriorities = null;
        }
    }

	/**
	 * looks for methods that contain a MONITORENTER opcode
	 *
	 * @param method the context object of the current method
	 * @return if the class uses synchronization
	 */
	public boolean prescreen(Method method) {
		BitSet bytecodeSet = getClassContext().getBytecodeSet(method);
		return bytecodeSet != null && (bytecodeSet.get(Constants.MONITORENTER));
	}

	/**
	 * implements the visitor to reset the opcode stack
	 *
	 * @param obj the context object of the currently parsed code block
	 */
	@Override
	public void visitCode(Code obj) {
		Method method = getMethod();
		if (prescreen(method)) {
			stack.resetForMethodEntry(this);
			regPriorities.clear();
			int[] parmRegs = RegisterUtils.getParameterRegisters(method);
			for (int reg : parmRegs)
				regPriorities.put(Integer.valueOf(reg), NORMAL);
			if (!method.isStatic())
				regPriorities.put(Integer.valueOf(0), LOW);
			super.visitCode(obj);
		}
	}

	/**
	 * implements the visitor to look for synchronization on non-owned objects
	 *
	 * @param seen the opcode of the currently parsed instruction
	 */
	@Override
	public void sawOpcode(int seen) {
		Integer tosIsPriority = null;
		try {
	        stack.precomputation(this);

			switch (seen) {
				case GETFIELD:
					tosIsPriority = OWNED;
				break;

				case ALOAD:
				case ALOAD_0:
				case ALOAD_1:
				case ALOAD_2:
				case ALOAD_3: {
					int reg = RegisterUtils.getALoadReg(this, seen);
					if (getMethod().isStatic() && (reg == 0))
						tosIsPriority = LOW;
					else {
						tosIsPriority = regPriorities.get(Integer.valueOf(reg));
						if (tosIsPriority == null)
							tosIsPriority = NORMAL;
					}
				}
				break;

				case ASTORE:
				case ASTORE_0:
				case ASTORE_1:
				case ASTORE_2:
				case ASTORE_3: {
					if (stack.getStackDepth() > 0) {
						OpcodeStack.Item item = stack.getStackItem(0);
						Integer priority = (Integer)item.getUserValue();
						regPriorities.put(Integer.valueOf(RegisterUtils.getAStoreReg(this, seen)), priority);
					}
				}
				break;

				case INVOKEVIRTUAL:
				case INVOKEINTERFACE: {
					String sig = getSigConstantOperand();
					Type t = Type.getReturnType(sig);
					if (t.getSignature().startsWith("L")) {
						int parmCnt = Type.getArgumentTypes(sig).length;
						if (stack.getStackDepth() > parmCnt) {
							OpcodeStack.Item itm = stack.getStackItem(parmCnt);
							Integer priority = (Integer)itm.getUserValue();
							if ((priority != null) && OWNED.equals(priority)) {
								tosIsPriority = OWNED;
							} else {
								int reg = itm.getRegisterNumber();
								if (reg > 0)
									tosIsPriority = regPriorities.get(Integer.valueOf(reg));
								else
									tosIsPriority = OWNED;
							}
						}
					}
				}
				break;

				case INVOKESTATIC: {
					String sig = getSigConstantOperand();
					Type t = Type.getReturnType(sig);
					if (t.getSignature().startsWith("L"))
						tosIsPriority = OWNED; // can't be sure
				}
				break;

				case INVOKESPECIAL: {
					String name = getNameConstantOperand();
					if ("<init>".equals(name)) {
						tosIsPriority = OWNED;
					}
				}
				break;

				case MONITORENTER: {
					if (stack.getStackDepth() > 0) {
						OpcodeStack.Item itm = stack.getStackItem(0);
						Integer priority = (Integer)itm.getUserValue();
						if ((priority != null) && (!priority.equals(OWNED))) {
							bugReporter.reportBug(new BugInstance(this, "NOS_NON_OWNED_SYNCHRONIZATION", priority.intValue())
											.addClass(this)
											.addMethod(this)
											.addSourceLine(this));
						}
					}
				}
				break;
			}
		} finally {
			TernaryPatcher.pre(stack, seen);
			stack.sawOpcode(this, seen);
			TernaryPatcher.post(stack, seen);
			if (tosIsPriority != null) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item itm = stack.getStackItem(0);
					itm.setUserValue(tosIsPriority);
				}
			}
		}
	}
}
