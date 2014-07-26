/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2014 Kevin Lubick
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

import static com.mebigfatguy.fbcontrib.utils.OpcodeUtils.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;

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
	 * the faster, non-thread safe StringBuilder was introduced in 1.5.  Thus, in code
	 * that targets before 1.5, FindBugs should not report a LocalSynchronizedCollection bug.
	 * Therefore, the entry <"java/lang/StringBuffer", Constants.MAJOR_1_5> is in the returned map.
	 * 
	 * 
	 * 
	 */
	protected abstract Map<String, Integer> getWatchedConstructors();

	/**
	 * Should return a map of a class and a set of "factory" methods that create types
	 * that should be reported buggy (when made as local variables).
	 * @return map of factory methods
	 */
	protected abstract Map<String, Set<String>> getWatchedClassMethods();

	/**
	 * Given this RegisterInfo, report an appropriate bug.
	 * @param cri
	 */
	protected abstract void reportBug(RegisterInfo cri);

	/**
	 * implements the visitor to create and clear the stack and suspectLocals
	 * 
	 * @param classContext the context object of the currently parsed class 
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			stack = new OpcodeStack();
			suspectLocals = new HashMap<Integer, RegisterInfo>();
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
	 * @param obj the context object of the currently parsed method
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
	 * @param obj the context object of the currently parsed code block
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
	 * implements the visitor to find the constructors defined in getWatchedConstructors()
	 * and the method calls in getWatchedClassMethods()
	 * 
	 * @param seen the opcode of the currently parsed instruction
	 */
	@Override
	public void sawOpcode(int seen) {
		Integer tosIsSyncColReg = null;
		try {
			stack.precomputation(this);

			if (seen == INVOKESPECIAL) {
				tosIsSyncColReg = checkConstructors();
			} else if (seen == INVOKESTATIC) {
				tosIsSyncColReg = checkStaticCreations();
			} else if (isAStore(seen)) {
				dealWithStoring(seen);
			} else if (isALoad(seen)) {
				int reg = RegisterUtils.getALoadReg(this, seen);
				RegisterInfo cri = suspectLocals.get(Integer.valueOf(reg));
				if ((cri != null) && !cri.getIgnore()) {
					tosIsSyncColReg = Integer.valueOf(reg);
				}
			} else if ((seen == PUTFIELD) || (seen == ARETURN)) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item item = stack.getStackItem(0);
					suspectLocals.remove(item.getUserValue());
				}
			}

			if (suspectLocals.size() > 0) {
				if (isInvokeInterfaceSpecialStaticOrVirtual(seen)) {
					String sig = getSigConstantOperand();
					int argCount = Type.getArgumentTypes(sig).length;
					if (stack.getStackDepth() >= argCount) {
						for (int i = 0; i < argCount; i++) {
							OpcodeStack.Item item = stack.getStackItem(i);
							RegisterInfo cri = suspectLocals.get(item.getUserValue());
							if (cri != null)
								cri.setPriority(LOW_PRIORITY);
						}
					}
				} else if (seen == MONITORENTER) {
					// Assume if synchronized blocks are used then something tricky is going on.
					// There is really no valid reason for this, other than folks who use
					// synchronized blocks tend to know what's going on.
					if (stack.getStackDepth() > 0) {
						OpcodeStack.Item item = stack.getStackItem(0);
						suspectLocals.remove(item.getUserValue());
					}
				} else if (seen == AASTORE) {
					if (stack.getStackDepth() > 0) {
						OpcodeStack.Item item = stack.getStackItem(0);
						suspectLocals.remove(item.getUserValue());
					}
				}
			}

			reportTroublesomeLocals();
		} finally {
			TernaryPatcher.pre(stack, seen);
			stack.sawOpcode(this, seen);
			TernaryPatcher.post(stack, seen);
			if (tosIsSyncColReg != null) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item item = stack.getStackItem(0);
					item.setUserValue(tosIsSyncColReg);
				}
			}
		}
	}

	

	protected void dealWithStoring(int seen) {
		if (stack.getStackDepth() > 0) {
			OpcodeStack.Item item = stack.getStackItem(0);
			int reg = RegisterUtils.getAStoreReg(this, seen);
			if (item.getUserValue() != null) {
				if (!suspectLocals.containsKey(Integer.valueOf(reg))) {
					RegisterInfo cri = new RegisterInfo(SourceLineAnnotation.fromVisitedInstruction(this),
							RegisterUtils.getLocalVariableEndRange(getMethod().getLocalVariableTable(), reg,
									getNextPC()));
					suspectLocals.put(Integer.valueOf(reg), cri);

				}
			} else {
				RegisterInfo cri = suspectLocals.get(Integer.valueOf(reg));
				if (cri == null) {
					cri = new RegisterInfo(RegisterUtils.getLocalVariableEndRange(getMethod()
							.getLocalVariableTable(), reg, getNextPC()));
					suspectLocals.put(Integer.valueOf(reg), cri);
				}
				cri.setIgnore();
			}
		}
	}

	protected Integer checkStaticCreations() {
		Integer tosIsSyncColReg = null;
		Map<String, Set<String>> mapOfClassToMethods = getWatchedClassMethods();
		for (Entry<String, Set<String>> entry: mapOfClassToMethods.entrySet())
			if (entry.getKey().equals(getClassConstantOperand())) {
				if (entry.getValue().contains(getNameConstantOperand())) {
					tosIsSyncColReg = Integer.valueOf(-1);
				}
			}
		return tosIsSyncColReg;
	}

	protected Integer checkConstructors() {
		Integer tosIsSyncColReg = null;
		if ("<init>".equals(getNameConstantOperand())) {
			Integer minVersion = getWatchedConstructors().get(getClassConstantOperand());
			if ((minVersion != null) && (classVersion >= minVersion.intValue())) {
				tosIsSyncColReg = Integer.valueOf(-1);
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
			if (newPriority > priority)
				priority = newPriority;
		}

		public int getPriority() {
			return priority;
		}

		@Override
		public String toString() {
			return "RegisterInfo [slAnnotation=" + slAnnotation + ", priority=" + priority + ", endPCRange="
					+ endPCRange + "]";
		}
	}

}
