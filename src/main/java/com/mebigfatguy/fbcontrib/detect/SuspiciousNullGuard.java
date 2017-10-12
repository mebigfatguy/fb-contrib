/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2017 Dave Brosius
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
import java.util.Iterator;
import java.util.Map;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.ToString;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XField;

/**
 * looks for code that checks to see if a field or local variable is not null,
 * before entering a code block either an if, or while statement, and reassigns
 * that field or variable. It seems that perhaps the guard should check if the
 * field or variable is null.
 */
@CustomUserValue
public class SuspiciousNullGuard extends BytecodeScanningDetector {

	private final BugReporter bugReporter;
	private OpcodeStack stack;
	private Map<Integer, NullGuard> nullGuards;

	/**
	 * constructs a SNG detector given the reporter to report bugs on
	 *
	 * @param bugReporter
	 *            the sync of bug reports
	 */
	public SuspiciousNullGuard(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}

	/**
	 * overrides the visitor to initialize and tear down the opcode stack
	 *
	 * @param classContext
	 *            the context object of the currently parsed class
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			stack = new OpcodeStack();
			nullGuards = new HashMap<>();
			super.visitClassContext(classContext);
		} finally {
			stack = null;
		}
	}

	/**
	 * overrides the visitor to reset the stack
	 *
	 * @param obj
	 *            the context object of the currently parsed code block
	 */
	@Override
	public void visitCode(Code obj) {
		stack.resetForMethodEntry(this);
		nullGuards.clear();
		super.visitCode(obj);
	}

	/**
	 * overrides the visitor to look for bad null guards
	 *
	 * @param seen
	 *            the opcode of the currently visited instruction
	 */
	@Override
	public void sawOpcode(int seen) {
		Integer sawALOADReg = null;
		try {
			stack.precomputation(this);

			Integer pc = Integer.valueOf(getPC());
			nullGuards.remove(pc);

			switch (seen) {
			case Const.IFNULL: {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item itm = stack.getStackItem(0);
					int reg = itm.getRegisterNumber();
					Integer target = Integer.valueOf(getBranchTarget());
					if (reg >= 0) {
						nullGuards.put(target, new NullGuard(reg, pc.intValue(), itm.getSignature()));
					} else {
						XField xf = itm.getXField();
						Integer sourceFieldReg = (Integer) itm.getUserValue();
						if ((xf != null) && (sourceFieldReg != null)) {
							nullGuards.put(target,
									new NullGuard(xf, sourceFieldReg.intValue(), pc.intValue(), itm.getSignature()));
						}
					}
				}
			}
				break;

			case Const.ASTORE:
			case Const.ASTORE_0:
			case Const.ASTORE_1:
			case Const.ASTORE_2:
			case Const.ASTORE_3: {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item itm = stack.getStackItem(0);
					if (!itm.isNull()) {
						NullGuard guard = findNullGuardWithRegister(RegisterUtils.getAStoreReg(this, seen));
						if (guard != null) {
							bugReporter.reportBug(new BugInstance(this, BugType.SNG_SUSPICIOUS_NULL_LOCAL_GUARD.name(),
									NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
							removeNullGuard(guard);
						}
					}
				}
			}
				break;

			case Const.ALOAD:
			case Const.ALOAD_0:
			case Const.ALOAD_1:
			case Const.ALOAD_2:
			case Const.ALOAD_3: {
				NullGuard guard = findNullGuardWithRegister(RegisterUtils.getALoadReg(this, seen));
				if (guard != null) {
					removeNullGuard(guard);
				}
				sawALOADReg = Integer.valueOf(RegisterUtils.getALoadReg(this, seen));
			}
				break;

			case Const.PUTFIELD: {
				if (stack.getStackDepth() <= 1) {
					break;
				}
				OpcodeStack.Item itm = stack.getStackItem(0);
				if (itm.isNull()) {
					break;
				}
				XField xf = getXFieldOperand();
				itm = stack.getStackItem(1);
				Integer fieldSourceReg = (Integer) itm.getUserValue();
				if ((xf != null) && (fieldSourceReg != null)) {
					NullGuard guard = findNullGuardWithField(xf, fieldSourceReg.intValue());
					if (guard != null) {
						bugReporter.reportBug(
								new BugInstance(this, BugType.SNG_SUSPICIOUS_NULL_FIELD_GUARD.name(), NORMAL_PRIORITY)
										.addClass(this).addMethod(this).addSourceLine(this));
						removeNullGuard(guard);
					}
				}
			}
				break;

			case Const.GETFIELD: {
				if (stack.getStackDepth() > 0) {
					XField xf = getXFieldOperand();
					OpcodeStack.Item itm = stack.getStackItem(0);
					Integer fieldSourceReg = (Integer) itm.getUserValue();
					if ((xf != null) && (fieldSourceReg != null)) {
						NullGuard guard = findNullGuardWithField(xf, fieldSourceReg.intValue());
						if (guard != null) {
							removeNullGuard(guard);
						} else {
							sawALOADReg = (Integer) itm.getUserValue();
						}
					}
				}
			}
				break;

			case Const.IFEQ:
			case Const.IFNE:
			case Const.IFLT:
			case Const.IFGE:
			case Const.IFGT:
			case Const.IFLE:
			case Const.IF_ICMPEQ:
			case Const.IF_ICMPNE:
			case Const.IF_ICMPLT:
			case Const.IF_ICMPGE:
			case Const.IF_ICMPGT:
			case Const.IF_ICMPLE:
			case Const.IF_ACMPEQ:
			case Const.IF_ACMPNE:
			case Const.GOTO:
			case Const.GOTO_W:
			case Const.IFNONNULL:
				nullGuards.clear();
				break;
			default:
				break;
			}
		} finally {
			stack.sawOpcode(this, seen);
			if ((sawALOADReg != null) && (stack.getStackDepth() > 0)) {
				OpcodeStack.Item itm = stack.getStackItem(0);
				itm.setUserValue(sawALOADReg);
			}
		}
	}

	private NullGuard findNullGuardWithRegister(int reg) {
		for (NullGuard guard : nullGuards.values()) {
			if (guard.getRegister() == reg) {
				return guard;
			}
		}

		return null;
	}

	private NullGuard findNullGuardWithField(XField field, int fieldSourceReg) {
		for (NullGuard guard : nullGuards.values()) {
			if (field.equals(guard.getField()) && (fieldSourceReg == guard.getFieldSourceReg())) {
				return guard;
			}
		}

		return null;
	}

	private void removeNullGuard(NullGuard guard) {
		Iterator<NullGuard> it = nullGuards.values().iterator();
		while (it.hasNext()) {
			NullGuard potentialNG = it.next();
			if (potentialNG.equals(guard)) {
				it.remove();
				break;
			}
		}
	}

	static class NullGuard {
		int register;
		XField field;
		int fieldSourceReg;
		int location;
		String signature;

		NullGuard(int reg, int start, String guardSignature) {
			register = reg;
			field = null;
			location = start;
			signature = guardSignature;
		}

		NullGuard(XField xf, int fieldSource, int start, String guardSignature) {
			register = -1;
			field = xf;
			fieldSourceReg = fieldSource;
			location = start;
			signature = guardSignature;
		}

		int getRegister() {
			return register;
		}

		XField getField() {
			return field;
		}

		int getFieldSourceReg() {
			return fieldSourceReg;
		}

		int getLocation() {
			return location;
		}

		String getSignature() {
			return signature;
		}

		@Override
		public String toString() {
			return ToString.build(this);
		}
	}
}
