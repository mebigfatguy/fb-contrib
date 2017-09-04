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

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for methods that use an array of length one to pass a variable to
 * achieve call by pointer ala C++. It is better to define a proper return class
 * type that holds all the relevant information retrieved from the called
 * method.
 */
@CustomUserValue
public class ArrayWrappedCallByReference extends BytecodeScanningDetector {

	private final BugReporter bugReporter;
	private OpcodeStack stack;
	private Map<Integer, WrapperInfo> wrappers;

	/**
	 * constructs a AWCBR detector given the reporter to report bugs on
	 *
	 * @param bugReporter
	 *            the sync of bug reports
	 */
	public ArrayWrappedCallByReference(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}

	/**
	 * implement the visitor to create and clear the stack and wrappers
	 *
	 * @param classContext
	 *            the context object of the currently parsed class
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			stack = new OpcodeStack();
			wrappers = new HashMap<>(10);
			super.visitClassContext(classContext);
		} finally {
			stack = null;
			wrappers = null;
		}
	}

	/**
	 * looks for methods that contain a NEWARRAY or ANEWARRAY opcodes
	 *
	 * @param method
	 *            the context object of the current method
	 * @return if the class uses synchronization
	 */
	public boolean prescreen(Method method) {
		BitSet bytecodeSet = getClassContext().getBytecodeSet(method);
		return (bytecodeSet != null) && (bytecodeSet.get(Const.NEWARRAY) || bytecodeSet.get(Const.ANEWARRAY));
	}

	/**
	 * implements the visitor to reset the stack of opcodes
	 *
	 * @param obj
	 *            the context object for the currently parsed code block
	 */
	@Override
	public void visitCode(Code obj) {
		if (prescreen(getMethod())) {
			stack.resetForMethodEntry(this);
			wrappers.clear();
			super.visitCode(obj);
		}
	}

	/**
	 * implements the visitor to wrapped array parameter calls
	 *
	 * @param seen
	 *            the currently visitor opcode
	 */
	@Override
	public void sawOpcode(int seen) {
		Integer userValue = null;
		try {
			stack.precomputation(this);

			switch (seen) {
			case Const.NEWARRAY:
			case Const.ANEWARRAY: {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item itm = stack.getStackItem(0);
					Integer size = (Integer) itm.getConstant();
					if ((size != null) && (size.intValue() == 1)) {
						userValue = Values.NEGATIVE_ONE;
					}
				}
			}
				break;

			case Const.IASTORE:
			case Const.LASTORE:
			case Const.FASTORE:
			case Const.DASTORE:
			case Const.AASTORE:
			case Const.BASTORE:
			case Const.CASTORE:
			case Const.SASTORE: {
				userValue = processArrayElementStore();
			}
				break;

			case Const.ASTORE:
			case Const.ASTORE_0:
			case Const.ASTORE_1:
			case Const.ASTORE_2:
			case Const.ASTORE_3: {
				processLocalStore(seen);
			}
				break;

			case Const.INVOKEVIRTUAL:
			case Const.INVOKEINTERFACE:
			case Const.INVOKESPECIAL:
			case Const.INVOKESTATIC: {
				processMethodCall();
			}
				break;

			case Const.IALOAD:
			case Const.LALOAD:
			case Const.FALOAD:
			case Const.DALOAD:
			case Const.AALOAD:
			case Const.BALOAD:
			case Const.CALOAD:
			case Const.SALOAD: {
				if (stack.getStackDepth() >= 2) {
					OpcodeStack.Item arItm = stack.getStackItem(1);
					int arReg = arItm.getRegisterNumber();
					WrapperInfo wi = wrappers.get(Integer.valueOf(arReg));
					if ((wi != null) && wi.wasArg) {
						userValue = Integer.valueOf(wi.wrappedReg);
					}
				}
			}
				break;

			case Const.ALOAD:
			case Const.ALOAD_0:
			case Const.ALOAD_1:
			case Const.ALOAD_2:
			case Const.ALOAD_3: {
				int reg = RegisterUtils.getALoadReg(this, seen);
				WrapperInfo wi = wrappers.get(Integer.valueOf(reg));
				if (wi != null) {
					userValue = Integer.valueOf(wi.wrappedReg);
				}
			}
				break;

			case Const.ISTORE:
			case Const.ISTORE_0:
			case Const.ISTORE_1:
			case Const.ISTORE_2:
			case Const.ISTORE_3:
			case Const.LSTORE:
			case Const.LSTORE_0:
			case Const.LSTORE_1:
			case Const.LSTORE_2:
			case Const.LSTORE_3:
			case Const.DSTORE:
			case Const.DSTORE_0:
			case Const.DSTORE_1:
			case Const.DSTORE_2:
			case Const.DSTORE_3:
			case Const.FSTORE:
			case Const.FSTORE_0:
			case Const.FSTORE_1:
			case Const.FSTORE_2:
			case Const.FSTORE_3: {
				if (stack.getStackDepth() == 0) {
					break;
				}
				OpcodeStack.Item itm = stack.getStackItem(0);
				Integer elReg = (Integer) itm.getUserValue();
				if (elReg == null) {
					break;
				}
				int reg = RegisterUtils.getStoreReg(this, seen);
				if (elReg.intValue() == reg) {
					bugReporter.reportBug(
							new BugInstance(this, BugType.AWCBR_ARRAY_WRAPPED_CALL_BY_REFERENCE.name(), NORMAL_PRIORITY)
									.addClass(this).addMethod(this).addSourceLine(this));
				}
			}
				break;

			default:
				break;
			}
		} finally {
			TernaryPatcher.pre(stack, seen);
			stack.sawOpcode(this, seen);
			TernaryPatcher.post(stack, seen);
			if ((userValue != null) && (stack.getStackDepth() > 0)) {
				OpcodeStack.Item itm = stack.getStackItem(0);
				itm.setUserValue(userValue);
			}
		}
	}

	/**
	 * looks for stores to registers, if that store is an array, builds a wrapper
	 * info for it and stores it in the wrappers collection. If it is a regular
	 * store, sees if this value, came from a wrapper array passed into a method,
	 * and if so reports it.
	 *
	 * @param seen
	 *            the currently parsed opcode
	 */
	private void processLocalStore(int seen) {
		if (stack.getStackDepth() == 0) {
			return;
		}
		OpcodeStack.Item itm = stack.getStackItem(0);
		String sig = itm.getSignature();
		if (sig.startsWith(Values.SIG_ARRAY_PREFIX)) {
			int reg = RegisterUtils.getAStoreReg(this, seen);
			Integer elReg = (Integer) itm.getUserValue();
			if (elReg != null) {
				wrappers.put(Integer.valueOf(reg), new WrapperInfo(elReg.intValue()));
			}
		} else {
			Integer elReg = (Integer) itm.getUserValue();
			if ((elReg != null) && (elReg.intValue() == RegisterUtils.getAStoreReg(this, seen))) {
				bugReporter.reportBug(
						new BugInstance(this, BugType.AWCBR_ARRAY_WRAPPED_CALL_BY_REFERENCE.name(), NORMAL_PRIORITY)
								.addClass(this).addMethod(this).addSourceLine(this));
			}
		}
	}

	/**
	 * processes a store to an array element to see if this array is being used as a
	 * wrapper array, and if so records the register that is stored within it.
	 *
	 * @return the user value representing the stored register value
	 */
	private Integer processArrayElementStore() {
		if (stack.getStackDepth() >= 2) {
			OpcodeStack.Item itm = stack.getStackItem(2);
			int reg = itm.getRegisterNumber();
			if (reg != -1) {
				WrapperInfo wi = wrappers.get(Integer.valueOf(reg));
				if (wi != null) {
					OpcodeStack.Item elItm = stack.getStackItem(0);
					wi.wrappedReg = elItm.getRegisterNumber();
				}
			} else {
				OpcodeStack.Item elItm = stack.getStackItem(0);
				reg = elItm.getRegisterNumber();
				if (reg != -1) {
					return Integer.valueOf(reg);
				}
			}
		}

		return null;
	}

	/**
	 * processes a method call looking for parameters that are arrays. If this array
	 * was seen earlier as a simple wrapping array, then it marks it as being having
	 * been used as a parameter.
	 *
	 */
	private void processMethodCall() {
		if ("invoke".equals(getNameConstantOperand()) && "java/lang/reflect/Method".equals(getClassConstantOperand())) {
			return;
		}
		String sig = getSigConstantOperand();
		List<String> args = SignatureUtils.getParameterSignatures(sig);
		if (stack.getStackDepth() >= args.size()) {
			for (int i = 0; i < args.size(); i++) {
				String argSig = args.get(i);
				if (argSig.startsWith(Values.SIG_ARRAY_PREFIX)) {
					OpcodeStack.Item itm = stack.getStackItem(args.size() - i - 1);
					int arrayReg = itm.getRegisterNumber();
					WrapperInfo wi = wrappers.get(Integer.valueOf(arrayReg));
					if (wi != null) {
						wi.wasArg = true;
					}
				}
			}
		}
	}

	/**
	 * represents a local array that is stored, for wrapping a value
	 */
	static class WrapperInfo {
		int wrappedReg;
		boolean wasArg;

		WrapperInfo(int reg) {
			wrappedReg = reg;
			wasArg = false;
		}

		@Override
		public String toString() {
			return ToString.build(this);
		}
	}

}
