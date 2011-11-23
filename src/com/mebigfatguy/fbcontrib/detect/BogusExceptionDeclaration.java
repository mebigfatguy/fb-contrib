/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2011 Dave Brosius
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

import java.util.HashSet;
import java.util.Set;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.ExceptionTable;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for constructors, private methods or static methods that declare that they
 * throw specific checked exceptions, but that do not. This just causes callers of
 * these methods to do extra work to handle an exception that will never be thrown.
 */
public class BogusExceptionDeclaration extends BytecodeScanningDetector {
	private static JavaClass runtimeExceptionClass;
	private static final Set<String> safeClasses = new HashSet<String>();
	static {
		try {
			safeClasses.add("java/lang/Object");
			safeClasses.add("java/lang/String");
			safeClasses.add("java/lang/Integer");
			safeClasses.add("java/lang/Long");
			safeClasses.add("java/lang/Float");
			safeClasses.add("java/lang/Double");
			safeClasses.add("java/lang/Short");
			safeClasses.add("java/lang/Boolean");

			runtimeExceptionClass = Repository.lookupClass("java/lang/RuntimeException");
		} catch (ClassNotFoundException cnfe) {
			runtimeExceptionClass = null;
		}
	}
	private final BugReporter bugReporter;
	private OpcodeStack stack;
	private Set<String> declaredCheckedExceptions;

	public BogusExceptionDeclaration(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}


	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			if (runtimeExceptionClass != null) {
				stack = new OpcodeStack();
				declaredCheckedExceptions = new HashSet<String>();
				super.visitClassContext(classContext);
			}
		} finally {
			declaredCheckedExceptions = null;
			stack = null;
		}
	}

	/**
	 * implements the visitor to see if the method declares that it throws any
	 * checked exceptions.
	 *
	 * @param obj the context object of the currently parsed code block
	 */
	@Override
	public void visitCode(Code obj) {
		declaredCheckedExceptions.clear();
		stack.resetForMethodEntry(this);
		Method method = getMethod();
		if (method.isStatic() || method.isPrivate() || (("<init>".equals(method.getName()) && !isAnonymousInnerCtor(method, getThisClass())))) {
			ExceptionTable et = method.getExceptionTable();
			if (et != null) {
				String[] exNames = et.getExceptionNames();
				for (String exName : exNames) {
					try {
						JavaClass exCls = Repository.lookupClass(exName);
						if (!exCls.instanceOf(runtimeExceptionClass)) {
							declaredCheckedExceptions.add(exName);
						}
					} catch (ClassNotFoundException cnfe) {
						bugReporter.reportMissingClass(cnfe);
					}
				}
				if (!declaredCheckedExceptions.isEmpty()) {
					super.visitCode(obj);
					if (!declaredCheckedExceptions.isEmpty()) {
						BugInstance bi = new BugInstance(this, "BED_BOGUS_EXCEPTION_DECLARATION", NORMAL_PRIORITY)
									.addClass(this)
									.addMethod(this)
									.addSourceLine(this, 0);
						for (String ex : declaredCheckedExceptions) {
							bi.addString(ex.replaceAll("/", "."));
						}
						bugReporter.reportBug(bi);
					}
				}
			}
		}
	}

	/**
	 * checks to see if this method is constructor of an instance based inner class, as jdk1.5 compiler
	 * has a bug where it attaches bogus exception declarations to this constructors in some cases.
	 * @param m the method to check
	 * @param cls the cls that owns the method
	 * @return whether this method is a ctor of an instance based anonymous inner class
	 */
	private boolean isAnonymousInnerCtor(Method m, JavaClass cls) {
	    if (!"<init>".equals(m.getName())) {
			return false;
		}

	    String clsName = cls.getClassName();
	    int dollarPos = clsName.lastIndexOf('$');
	    if (dollarPos <0) {
			return false;
		}

	    String signature = m.getSignature();
	    return ("(L" + clsName.substring(0, dollarPos).replace('.', '/') + ";)V").equals(signature);
	}

	/**
	 * implements the visitor to look for method calls that could throw the exceptions
	 * that are listed in the declaration.
	 */
	@Override
	public void sawOpcode(int seen) {
		if (declaredCheckedExceptions.isEmpty()) {
			return;
		}

		try {

			if ((seen == INVOKEVIRTUAL)
			||  (seen == INVOKEINTERFACE)
			||  (seen == INVOKESPECIAL)
			||  (seen == INVOKESTATIC)) {
				String clsName = getClassConstantOperand();
				if (!safeClasses.contains(clsName)) {
					try {
						JavaClass cls = Repository.lookupClass(clsName);
						Method[] methods = cls.getMethods();
						String methodName = getNameConstantOperand();
						String signature = getSigConstantOperand();
						boolean found = false;
						for (Method m : methods) {
							if (m.getName().equals(methodName) && m.getSignature().equals(signature)) {
								ExceptionTable et = m.getExceptionTable();
								if (et != null) {
									String[] thrownExceptions = et.getExceptionNames();
									for (String thrownException : thrownExceptions) {
										declaredCheckedExceptions.remove(thrownException);
										JavaClass exCls = Repository.lookupClass(thrownException);
										JavaClass superCls = exCls.getSuperClass();
										do {
											exCls = superCls;
											if (exCls != null) {
	    										declaredCheckedExceptions.remove(exCls.getClassName());
	    										superCls = exCls.getSuperClass();
											} else {
											    break;
											}
										} while (!declaredCheckedExceptions.isEmpty() && !"java.lang.Exception".equals(exCls.getClassName()) && !"java.lang.Error".equals(exCls.getClassName()));

									}
								} else {
									declaredCheckedExceptions.clear();
								}
								found = true;
								break;
							}
						}

						if (!found) {
							declaredCheckedExceptions.clear();
						}
					}
					catch (ClassNotFoundException cnfe) {
						bugReporter.reportMissingClass(cnfe);
						declaredCheckedExceptions.clear();
					}
				}
			} else if (seen == ATHROW) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item item = stack.getStackItem(0);
					String exSig = item.getSignature();
					String exClass = exSig.substring(1, exSig.length() - 1).replaceAll("/", ".");
					declaredCheckedExceptions.remove(exClass);
				}
			}
		} finally {
			stack.sawOpcode(this, seen);
		}
	}
}
