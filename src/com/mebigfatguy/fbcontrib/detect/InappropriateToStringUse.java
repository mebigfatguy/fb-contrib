/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2012 Dave Brosius
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XMethod;

/**
 * looks for methods that rely on the format of the string fetched from another object's toString
 * method, when that method appears not to be owned by the author of the calling method.
 * As the implementation of toString() is often considered a private implementation detail of a class, 
 * and not something that should be relied on, depending on it's format is dangerous.
 */
public class InappropriateToStringUse extends BytecodeScanningDetector {

	private static final Set<String> validToStringClasses = new HashSet<String>();
	static {
		validToStringClasses.add("java/lang/Object"); // too many fps
		validToStringClasses.add("java/lang/Byte");
		validToStringClasses.add("java/lang/Character");
		validToStringClasses.add("java/lang/Short");
		validToStringClasses.add("java/lang/Integer");
		validToStringClasses.add("java/lang/Boolean");
		validToStringClasses.add("java/lang/Float");
		validToStringClasses.add("java/lang/Double");
		validToStringClasses.add("java/lang/Long");
		validToStringClasses.add("java/lang/String");
		validToStringClasses.add("java/lang/Number");
		validToStringClasses.add("java/lang/StringBuffer");
		validToStringClasses.add("java/lang/StringBuilder");
		validToStringClasses.add("java/io/StringWriter");
	}
	private static final Set<String> stringAlgoMethods = new HashSet<String>();
	static {
		stringAlgoMethods.add("indexOf");
		stringAlgoMethods.add("contains");
		stringAlgoMethods.add("startsWith");
		stringAlgoMethods.add("endsWith");
		stringAlgoMethods.add("substring");
	}

	private final BugReporter bugReporter;
	private OpcodeStack stack;
	private Map<Integer, String> toStringRegisters;
	private String packageName;

	/**
	 * constructs a ITU detector given the reporter to report bugs on
	 * @param bugReporter the sync of bug reports
	 */
	public InappropriateToStringUse(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}

	/**
	 * overrides the visitor to reset the stack
	 * 
	 * @param classContext the context object of the currently parsed class
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			stack = new OpcodeStack();
			toStringRegisters = new HashMap<Integer, String>();
			packageName = classContext.getJavaClass().getPackageName();
			super.visitClassContext(classContext);
		} finally {
			stack = null;
			toStringRegisters = null;
		}
	}

	/**
	 * overrides the visitor to resets the stack for this method.
	 * 
	 * @param obj the context object for the currently parsed code block
	 */
	@Override
	public void visitCode(Code obj) {
		stack.resetForMethodEntry(this);
		toStringRegisters.clear();
		super.visitCode(obj);
	}

	/**
	 * overrides the visitor to look for suspicious operations on toString
	 */
	@Override
	public void sawOpcode(int seen) {
		String methodPackage = null;
		try {
			if (seen == INVOKEVIRTUAL) {
				String methodName = getNameConstantOperand();
				if ("toString".equals(methodName)) {
					String signature = getSigConstantOperand();
					if ("()Ljava/lang/String;".equals(signature)) {
						String className = getClassConstantOperand();
						if (!validToStringClasses.contains(className)) {
							if (stack.getStackDepth() > 0) {
								OpcodeStack.Item item = stack.getStackItem(0);
								JavaClass cls = item.getJavaClass();
								if (cls != null) {
									methodPackage = cls.getPackageName();
								}
							}
						}
					}
				} else if (stringAlgoMethods.contains(methodName)) {
					String className = getClassConstantOperand();
					if ("java/lang/String".equals(className)) {
						String signature = getSigConstantOperand();
						int numParms = Type.getArgumentTypes(signature).length;
						if (stack.getStackDepth() > numParms) {
							OpcodeStack.Item item = stack.getStackItem(numParms);
							if (item.getUserValue() != null) {
								XMethod xm = item.getReturnValueOf();
								String tsPackage = null;
								if (xm != null) {
									tsPackage = xm.getPackageName();
								}
								if ((tsPackage == null) || !SignatureUtils.similarPackages(tsPackage, packageName, 2)) {
									bugReporter.reportBug(new BugInstance(this, "ITU_INAPPROPRIATE_TOSTRING_USE", NORMAL_PRIORITY)
									.addClass(this)
									.addMethod(this)
									.addSourceLine(this));
								}
							}
						}
					}
				}
			} else if ((seen == ASTORE)
					||  ((seen >= ASTORE_0) && (seen <= ASTORE_3))) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item item = stack.getStackItem(0);
					Integer reg = Integer.valueOf(RegisterUtils.getAStoreReg(this, seen));
					if (item.getUserValue() != null) {
						XMethod xm = item.getReturnValueOf();
						if (xm != null) {
							toStringRegisters.put(reg, xm.getPackageName());
						} else {
							toStringRegisters.remove(reg);
						}
					} else {
						toStringRegisters.remove(reg);
					}
				}
			} else if ((seen == ALOAD)
					||  ((seen >= ALOAD_0) && (seen <= ALOAD_3))) {
				Integer reg = Integer.valueOf(RegisterUtils.getAStoreReg(this, seen));
				methodPackage = toStringRegisters.get(reg);
			}
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
		} finally {
			TernaryPatcher.pre(stack, seen);
			stack.sawOpcode(this, seen);
			TernaryPatcher.post(stack, seen);
			if (methodPackage != null) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item item = stack.getStackItem(0);
					item.setUserValue(methodPackage);
				}
			}
		}
	}

}
