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

import java.util.HashMap;
import java.util.Map;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XField;

public abstract class MissingMethodsDetector extends BytecodeScanningDetector {

	private final BugReporter bugReporter;
	private OpcodeStack stack;
	private String clsSignature;
	/** register to first allocation PC */
	private Map<Integer, Integer> localSpecialObjects;
	/** fieldname to field sig */
	private Map<String, String> fieldSpecialObjects;
	private boolean sawTernary;
	private boolean isInnerClass;

	public MissingMethodsDetector(BugReporter bugReporter) {
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
			JavaClass clz = classContext.getJavaClass();
			isInnerClass = clz.getClassName().contains("$");

			clsSignature = "L" + clz.getClassName().replaceAll("\\.", "/") + ";";
			stack = new OpcodeStack();
			localSpecialObjects = new HashMap<Integer, Integer>();
			fieldSpecialObjects = new HashMap<String, String>();
			super.visitClassContext(classContext);

			if (!isInnerClass && (fieldSpecialObjects.size() > 0)) {
				String clsName = classContext.getJavaClass().getClassName();
				for (Map.Entry<String, String> entry : fieldSpecialObjects.entrySet()) {
					String fieldName = entry.getKey();
					String signature = entry.getValue();
					bugReporter.reportBug(makeFieldBugInstance()
							.addClass(this)
							.addField(clsName, fieldName, signature, false));
				}
			}
		} finally {
			stack = null;
			localSpecialObjects = null;
			fieldSpecialObjects = null;
		}
	}


	@Override
	public void visitField(Field obj) {
		if (!isInnerClass && obj.isPrivate() && !obj.isSynthetic()) {
			String sig = obj.getSignature();
			if (sig.startsWith("L")) {
				String type = sig.substring(1, sig.length() - 1).replace('/', '.');
				if (doesObjectNeedToBeWatched(type)) {
					fieldSpecialObjects.put(obj.getName(), obj.getSignature());
				}
			}
		}
	}

	/**
	 * overrides the visitor reset the stack
	 *
	 * @param obj the context object of the currently parsed code block
	 */
	@Override
	public void visitCode(Code obj) {
		
		stack.resetForMethodEntry(this);
		localSpecialObjects.clear();
		sawTernary = false;
		super.visitCode(obj);

		for (Integer pc : localSpecialObjects.values()) {
			bugReporter.reportBug(makeLocalBugInstance()
					.addClass(this)
					.addMethod(this)
					.addSourceLine(this, pc.intValue()));
		}
	}

	/**
	 * overrides the visitor to look for uses of collections where the only
	access to
	 * to the collection is to write to it
	 *
	 * @param seen the opcode of the currently visited instruction
	 */
	@Override
	public void sawOpcode(int seen) {
		Object userObject = null;

		// saving and restoring the userobject of the top item, works around a bug in Findbugs proper
		if (stack.getStackDepth() > 0) {
			userObject = stack.getStackItem(0).getUserValue();
		}
		stack.precomputation(this);
		if (stack.getStackDepth() > 0) {
			stack.getStackItem(0).setUserValue(userObject);
			userObject = null;
		}

		try {
			switch (seen) {
			case INVOKESPECIAL:
				userObject = sawInvokeSpecial(userObject);
				break;
			case INVOKEINTERFACE:case INVOKEVIRTUAL:
				sawInvokeInterfaceVirtual();
				break;
			case INVOKESTATIC:
				processMethodParms();
				break;
			case ARETURN:
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item item = stack.getStackItem(0);
					clearUserValue(item);
				}
				break;

			case ASTORE_0:case ASTORE_1:case ASTORE_2:case ASTORE_3:case ASTORE:
				sawAStore(seen);
				break;

			case ALOAD_0:case ALOAD_1:case ALOAD_2:case ALOAD_3:case ALOAD:
				userObject = sawLoad(seen, userObject);
				break;

			case AASTORE:
				if (stack.getStackDepth() >= 3) {
					OpcodeStack.Item item = stack.getStackItem(0);
					clearUserValue(item);
				}
				break;

			case PUTFIELD:
				sawPutField();
				break;

			case GETFIELD:
				userObject = sawGetField(userObject);
				break;

			case PUTSTATIC:
				sawPutStatic();
				break;

			case GETSTATIC:
				userObject = sawGetStatic(userObject);
				break;

			case GOTO: case IFNULL: case IFNONNULL:
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item item = stack.getStackItem(0);
					Object uo = item.getUserValue();
					if ((uo != null) && !(uo instanceof Boolean)) {
						clearUserValue(item);
					}
					sawTernary = true;
				}
				break;
			default:
				break;
			}
		} finally {
			TernaryPatcher.pre(stack, seen);
			stack.sawOpcode(this, seen);
			TernaryPatcher.post(stack, seen);
			if (userObject != null) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item item = stack.getStackItem(0);
					item.setUserValue(userObject);
				}
			}
			if (sawTernary) {
				handleTernary(seen);
			}
		}
	}

	private void handleTernary(int seen) {
		if ((seen == GETFIELD) || (seen == ALOAD) || ((seen >= ALOAD_0) && (seen <= ALOAD_3))) {
			if (stack.getStackDepth() > 0) {
				OpcodeStack.Item item = stack.getStackItem(0);
				clearUserValue(item);
			}
		}
		/* check ALOAD_0, as if it's a field the statement after a GOTO will be loading 'this' */
		if ((seen != GOTO) && (seen != IFNULL) && (seen != IFNONNULL) && (seen != ALOAD_0))
			sawTernary = false;
	}

	private void sawPutStatic() {
		if (stack.getStackDepth() > 0) {
			OpcodeStack.Item item = stack.getStackItem(0);
			Object uo = item.getUserValue();
			if ((uo != null) && !(uo instanceof Boolean)) {
				clearUserValue(item);
			}
		}
	}

	private Object sawGetStatic(Object userObject) {
		XField field = getXFieldOperand();
		if (field != null) {
			String fieldName = field.getName();
			if (fieldSpecialObjects.containsKey(fieldName)) {
				userObject = fieldName;
			}
		}
		return userObject;
	}

	private void sawPutField() {
		if (stack.getStackDepth() > 1) {
			OpcodeStack.Item item = stack.getStackItem(0);
			Object uo = item.getUserValue();
			if ((uo != null) && !(uo instanceof Boolean)) {
				clearUserValue(item);
			}
		}
	}

	private Object sawGetField(Object userObject) {
		if (stack.getStackDepth() > 0) {
			OpcodeStack.Item item = stack.getStackItem(0);
			String sig = item.getSignature();
			if ((item.getRegisterNumber() == 0) || ((sig != null) && sig.equals(clsSignature))) {
				userObject = sawGetStatic(userObject);
			}
		}
		return userObject;
	}

	private Object sawLoad(int seen, Object userObject) {
		int reg = RegisterUtils.getALoadReg(this, seen);
		if (localSpecialObjects.containsKey(Integer.valueOf(reg))) {
			userObject = Integer.valueOf(reg);
		}
		return userObject;
	}

	private void sawInvokeInterfaceVirtual() {
		String sig = getSigConstantOperand();
		int numParms = Type.getArgumentTypes(sig).length;
		if (stack.getStackDepth() > numParms) {
			OpcodeStack.Item item = stack.getStackItem(numParms);
			Object uo = item.getUserValue();
			if (uo != null) {
				String name = getNameConstantOperand();
				if (isMethodThatShouldBeCalled(name)) {
					clearUserValue(item);
				} else if (!"clone".equals(name)) {
					Type t = Type.getReturnType(sig);
					if ((t != Type.VOID) && !nextOpIsPop()) {
						clearUserValue(item);
					}
				}
			}
		}
		processMethodParms();
	}

	private Object sawInvokeSpecial(Object userObject) {
		String methodName = getNameConstantOperand();
		if ("<init>".equals(methodName)) {
			String clsName = getClassConstantOperand().replace('/', '.');
			if (doesObjectNeedToBeWatched(clsName))
			{
				userObject = Boolean.TRUE;
			}
		}
		processMethodParms();
		return userObject;
	}

	private void sawAStore(int seen) {
		if (stack.getStackDepth() > 0) {
			OpcodeStack.Item item = stack.getStackItem(0);
			Object uo = item.getUserValue();
			if (uo != null) {
				if (uo instanceof Boolean) {
					int reg = RegisterUtils.getAStoreReg(this, seen);
					localSpecialObjects.put(Integer.valueOf(reg), Integer.valueOf(getPC()));
					if (stack.getStackDepth() > 1) {
						//the astore was preceded by a dup
						item = stack.getStackItem(1);
						item.setUserValue(Integer.valueOf(reg));
					}
				} else {
					clearUserValue(item);
				}
			}
		}
	}

	private boolean nextOpIsPop() {
		int nextPC = getNextPC();
		return getCode().getCode()[nextPC] == POP;
	}

	private void clearUserValue(OpcodeStack.Item item) {
		Object uo = item.getUserValue();
		if (uo instanceof Integer) {
			localSpecialObjects.remove(uo);
		} else if (uo instanceof String) {
			fieldSpecialObjects.remove(uo);
		} else if (uo instanceof Boolean) {
			localSpecialObjects.remove(Integer.valueOf(item.getRegisterNumber()));
		}
		item.setUserValue(null);
	}

	/**
	 * Checks to see if any of the locals or fields that we are tracking are passed into
	 * another method.  If they are, we clear out our tracking of them, because we can't
	 * easily track their progress into the method
	 */
	protected void processMethodParms() {
		String sig = getSigConstantOperand();
		int numParms = Type.getArgumentTypes(sig).length;
		if ((numParms > 0) && (stack.getStackDepth() >= numParms)) {
			for (int i = 0; i < numParms; i++) {
				clearUserValue(stack.getStackItem(i));
			}
		}
		
	}

	protected abstract BugInstance makeFieldBugInstance();
	protected abstract BugInstance makeLocalBugInstance();

	protected abstract boolean doesObjectNeedToBeWatched(String type);
	protected abstract boolean isMethodThatShouldBeCalled(String methodName);

}
