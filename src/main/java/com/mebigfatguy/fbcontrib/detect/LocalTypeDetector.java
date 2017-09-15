/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2017 Kevin Lubick
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

import static com.mebigfatguy.fbcontrib.utils.OpcodeUtils.isALoad;
import static com.mebigfatguy.fbcontrib.utils.OpcodeUtils.isAStore;
import static com.mebigfatguy.fbcontrib.utils.OpcodeUtils.isStandardInvoke;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.SourceLineAnnotation;
import edu.umd.cs.findbugs.ba.ClassContext;

abstract class LocalTypeDetector extends BytecodeScanningDetector {

	private OpcodeStack stack;
	private Map<Integer, RegisterInfo> suspectLocals;
	private int classVersion;

	/**
	 * Should return a map of constructors that should be watched, as well as
	 * version number of Java that the given constructor becomes a bad idea.
	 *
	 * e.g. StringBuffer was the only way to efficiently concatenate a string until
	 * the faster, non-thread safe StringBuilder was introduced in 1.5. Thus, in
	 * code that targets before 1.5, FindBugs should not report a
	 * LocalSynchronizedCollection bug. Therefore, the entry
	 * &lt;"java/lang/StringBuffer", Const.MAJOR_1_5&gt; is in the returned map.
	 *
	 * @return the map of watched constructors
	 */
	protected abstract Map<String, Integer> getWatchedConstructors();

	/**
	 * Should return a map of a class and a set of "factory" methods that create
	 * types that should be reported buggy (when made as local variables).
	 *
	 * @return map of factory methods
	 */
	protected abstract Map<String, Set<String>> getWatchedClassMethods();

	/**
	 * returns a set of self returning methods, that is, methods that when called on
	 * a a synchronized collection return themselves.
	 *
	 * @return a set of self referential methods
	 */
	protected abstract Set<String> getSelfReturningMethods();

	/**
	 * Given this RegisterInfo, report an appropriate bug.
	 *
	 * @param cri
	 *            the register info
	 */
	protected abstract void reportBug(RegisterInfo cri);

	/**
	 * implements the visitor to create and clear the stack and suspectLocals
	 *
	 * @param classContext
	 *            the context object of the currently parsed class
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			stack = new OpcodeStack();
			suspectLocals = new HashMap<>();
			classVersion = classContext.getJavaClass().getMajor();
			super.visitClassContext(classContext);
		} finally {
			stack = null;
			suspectLocals = null;
		}
	}

	/**
	 * implements the visitor to collect parameter registers
	 *
	 * @param obj
	 *            the context object of the currently parsed method
	 */
	@Override
	public void visitMethod(Method obj) {
		suspectLocals.clear();
		int[] parmRegs = RegisterUtils.getParameterRegisters(obj);
		for (int pr : parmRegs) {
			suspectLocals.put(Integer.valueOf(pr),
					new RegisterInfo(RegisterUtils.getLocalVariableEndRange(obj.getLocalVariableTable(), pr, 0)));
		}
	}

	/**
	 * implements the visitor to reset the stack
	 *
	 * @param obj
	 *            the context object of the currently parsed code block
	 */
	@Override
	public void visitCode(Code obj) {
		stack.resetForMethodEntry(this);
		super.visitCode(obj);

		for (Map.Entry<Integer, RegisterInfo> entry : suspectLocals.entrySet()) {
			RegisterInfo cri = entry.getValue();
			if (!cri.getIgnore()) {
				reportBug(cri);
			}

		}
	}

	/**
	 * implements the visitor to find the constructors defined in
	 * getWatchedConstructors() and the method calls in getWatchedClassMethods()
	 *
	 * @param seen
	 *            the opcode of the currently parsed instruction
	 */
	@Override
	public void sawOpcode(int seen) {
		Integer tosIsSyncColReg = null;
		try {
			stack.precomputation(this);

			if (seen == Const.INVOKESPECIAL) {
				tosIsSyncColReg = checkConstructors();
			} else if (seen == Const.INVOKESTATIC) {
				tosIsSyncColReg = checkStaticCreations();
			} else if ((seen == Const.INVOKEVIRTUAL) || (seen == Const.INVOKEINTERFACE)) {
				tosIsSyncColReg = checkSelfReturningMethods();
			} else if (isAStore(seen)) {
				dealWithStoring(seen);
			} else if (isALoad(seen)) {
				int reg = RegisterUtils.getALoadReg(this, seen);
				RegisterInfo cri = suspectLocals.get(Integer.valueOf(reg));
				if ((cri != null) && !cri.getIgnore()) {
					tosIsSyncColReg = Integer.valueOf(reg);
				}
			} else if (((seen == Const.PUTFIELD) || (seen == Const.ARETURN)) && (stack.getStackDepth() > 0)) {
				OpcodeStack.Item item = stack.getStackItem(0);
				suspectLocals.remove(item.getUserValue());
			}

			if (!suspectLocals.isEmpty()) {
				if (isStandardInvoke(seen)) {
					String sig = getSigConstantOperand();
					int argCount = SignatureUtils.getNumParameters(sig);
					if (stack.getStackDepth() >= argCount) {
						for (int i = 0; i < argCount; i++) {
							OpcodeStack.Item item = stack.getStackItem(i);
							RegisterInfo cri = suspectLocals.get(item.getUserValue());
							if (cri != null) {
								if (SignatureUtils.similarPackages(
										SignatureUtils.getPackageName(
												SignatureUtils.stripSignature(getClassConstantOperand())),
										SignatureUtils.getPackageName(
												SignatureUtils.stripSignature(this.getClassName())),
										2)) {
									cri.setPriority(LOW_PRIORITY);
								} else {
									cri.setIgnore();
								}
							}
						}
					}
				} else if (seen == Const.MONITORENTER) {
					// Assume if synchronized blocks are used then something
					// tricky is going on.
					// There is really no valid reason for this, other than
					// folks who use
					// synchronized blocks tend to know what's going on.
					if (stack.getStackDepth() > 0) {
						OpcodeStack.Item item = stack.getStackItem(0);
						suspectLocals.remove(item.getUserValue());
					}
				} else if ((seen == Const.AASTORE) && (stack.getStackDepth() > 0)) {
					OpcodeStack.Item item = stack.getStackItem(0);
					suspectLocals.remove(item.getUserValue());
				}
			}

			reportTroublesomeLocals();
		} finally {
			TernaryPatcher.pre(stack, seen);
			stack.sawOpcode(this, seen);
			TernaryPatcher.post(stack, seen);
			if ((tosIsSyncColReg != null) && (stack.getStackDepth() > 0)) {
				OpcodeStack.Item item = stack.getStackItem(0);
				item.setUserValue(tosIsSyncColReg);
			}
		}
	}

	protected void dealWithStoring(int seen) {
		if (stack.getStackDepth() > 0) {
			OpcodeStack.Item item = stack.getStackItem(0);
			int reg = RegisterUtils.getAStoreReg(this, seen);
			if (item.getUserValue() != null) {
				if (!suspectLocals.containsKey(Integer.valueOf(reg))) {
					RegisterInfo cri = new RegisterInfo(SourceLineAnnotation.fromVisitedInstruction(this), RegisterUtils
							.getLocalVariableEndRange(getMethod().getLocalVariableTable(), reg, getNextPC()));
					suspectLocals.put(Integer.valueOf(reg), cri);

				}
			} else {
				RegisterInfo cri = suspectLocals.get(Integer.valueOf(reg));
				if (cri == null) {
					cri = new RegisterInfo(RegisterUtils.getLocalVariableEndRange(getMethod().getLocalVariableTable(),
							reg, getNextPC()));
					suspectLocals.put(Integer.valueOf(reg), cri);
				}
				cri.setIgnore();
			}
		}
	}

	protected Integer checkStaticCreations() {
		Integer tosIsSyncColReg = null;
		Map<String, Set<String>> mapOfClassToMethods = getWatchedClassMethods();
		for (Entry<String, Set<String>> entry : mapOfClassToMethods.entrySet()) {
			if (entry.getKey().equals(getClassConstantOperand())
					&& entry.getValue().contains(getNameConstantOperand())) {
				tosIsSyncColReg = Values.NEGATIVE_ONE;
				break;
			}
		}
		return tosIsSyncColReg;
	}

	protected Integer checkSelfReturningMethods() {
		Integer tosIsSyncColReg = null;
		Set<String> selfReturningMethods = getSelfReturningMethods();
		String methodName = getClassConstantOperand() + '.' + getNameConstantOperand();
		for (String selfRefNames : selfReturningMethods) {
			if (methodName.equals(selfRefNames)) {
				int numParameters = SignatureUtils.getNumParameters(getSigConstantOperand());
				if (stack.getStackDepth() > numParameters) {
					OpcodeStack.Item item = stack.getStackItem(numParameters);
					tosIsSyncColReg = (Integer) item.getUserValue();
				}
				break;
			}
		}
		return tosIsSyncColReg;
	}

	protected Integer checkConstructors() {
		Integer tosIsSyncColReg = null;
		if (Values.CONSTRUCTOR.equals(getNameConstantOperand())) {
			Integer minVersion = getWatchedConstructors().get(getClassConstantOperand());
			if ((minVersion != null) && (classVersion >= minVersion.intValue())) {
				tosIsSyncColReg = Values.NEGATIVE_ONE;
			}
		}
		return tosIsSyncColReg;
	}

	protected void reportTroublesomeLocals() {
		int curPC = getPC();
		Iterator<RegisterInfo> it = suspectLocals.values().iterator();
		while (it.hasNext()) {
			RegisterInfo cri = it.next();
			if (cri.getEndPCRange() < curPC) {
				if (!cri.getIgnore()) {
					reportBug(cri);
				}
				it.remove();
			}
		}
	}

	protected static class RegisterInfo {
		private SourceLineAnnotation slAnnotation;
		private int priority = HIGH_PRIORITY;
		private int endPCRange = Integer.MAX_VALUE;

		public RegisterInfo(SourceLineAnnotation sla, int endPC) {
			slAnnotation = sla;
			endPCRange = endPC;
		}

		public RegisterInfo(int endPC) {
			slAnnotation = null;
			endPCRange = endPC;
		}

		public SourceLineAnnotation getSourceLineAnnotation() {
			return slAnnotation;
		}

		public void setEndPCRange(int pc) {
			endPCRange = pc;
		}

		public int getEndPCRange() {
			return endPCRange;
		}

		public void setIgnore() {
			slAnnotation = null;
		}

		public boolean getIgnore() {
			return slAnnotation == null;
		}

		public void setPriority(int newPriority) {
			if (newPriority > priority) {
				priority = newPriority;
			}
		}

		public int getPriority() {
			return priority;
		}

		@Override
		public String toString() {
			return ToString.build(this);
		}
	}

}
