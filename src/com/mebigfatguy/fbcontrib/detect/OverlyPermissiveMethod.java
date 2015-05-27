/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2015 Dave Brosius
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
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.collect.MethodInfo;
import com.mebigfatguy.fbcontrib.collect.Statistics;
import com.mebigfatguy.fbcontrib.collect.StatisticsKey;
import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.classfile.ClassDescriptor;

/**
 * looks for methods that are declared more permissively than the code is using. 
 * For instance, declaring a method public, when it could just be declared private.
 */
public class OverlyPermissiveMethod extends BytecodeScanningDetector {

	private static Map<Integer, String> DECLARED_ACCESS = new HashMap<Integer, String>();
	static {
		DECLARED_ACCESS.put(Integer.valueOf(Constants.ACC_PRIVATE), "private");
		DECLARED_ACCESS.put(Integer.valueOf(Constants.ACC_PROTECTED), "protected");
		DECLARED_ACCESS.put(Integer.valueOf(Constants.ACC_PUBLIC), "public");
		DECLARED_ACCESS.put(Integer.valueOf(0), "package private");
	}
	
	private BugReporter bugReporter;
	private OpcodeStack stack;
	private String callingPackage;
	private String callingClass;

	/**
     * constructs a OPM detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
	 */
	public OverlyPermissiveMethod(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}

	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			ClassDescriptor cd = classContext.getClassDescriptor();
			callingClass = cd.getClassName();
			callingPackage = cd.getPackageName();
			stack = new OpcodeStack();
			super.visitClassContext(classContext);
		} finally {
			callingPackage = null;
			stack = null;
		}
	}

	@Override
	public void visitCode(Code obj) {
		if (!hasRuntimeAnnotations(getMethod())) {
			stack.resetForMethodEntry(this);
			super.visitCode(obj);
		}
	}

	@Override
	public void sawOpcode(int seen) {
		try {
			stack.precomputation(this);

			switch (seen) {
				case INVOKEVIRTUAL:
				case INVOKEINTERFACE:
				case INVOKESTATIC:
				case INVOKESPECIAL:
					String calledClass = getClassConstantOperand();
					String sig = getSigConstantOperand();
					MethodInfo mi = Statistics.getStatistics().getMethodStatistics(calledClass, getNameConstantOperand(), sig);
					if (mi != null) {
						if (seen == INVOKEINTERFACE) {
							mi.addCallingAccess(Constants.ACC_PUBLIC);
						} else {
							String calledPackage;
							int slashPos = calledClass.lastIndexOf('/');
							if (slashPos >= 0) {
								calledPackage = calledClass.substring(0, slashPos);
							} else {
								calledPackage = "";
							}
							boolean sameClass = calledClass.equals(callingClass);
							boolean samePackage = calledPackage.equals(callingPackage);

							if (sameClass) {
								mi.addCallingAccess(Constants.ACC_PRIVATE);
							} else if (samePackage) {
								mi.addCallingAccess(0);
							} else {
								if (seen == INVOKESTATIC) {
									mi.addCallingAccess(Constants.ACC_PUBLIC);
								} else if (isCallingOnThis(sig)) {
									mi.addCallingAccess(Constants.ACC_PROTECTED);
								} else {
									mi.addCallingAccess(Constants.ACC_PUBLIC);
								}
							}
						}
					}
					break;

				default:
					break;
			}
		} finally {
			stack.sawOpcode(this, seen);
		}
	}

	private boolean hasRuntimeAnnotations(Method obj) {
		AnnotationEntry[] annotations = obj.getAnnotationEntries();
		if (annotations != null) {
			for (AnnotationEntry entry : annotations) {
				if (entry.isRuntimeVisible()) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	/**
	 * checks to see if an instance method is called on the 'this' object
	 * @param sig the signature of the method called to find the called-on object
	 * @return when it is called on this or not
	 */
	private boolean isCallingOnThis(String sig) {
		Type[] argTypes = Type.getArgumentTypes(sig);
		if (stack.getStackDepth() < argTypes.length) {
			return false;
		}

		OpcodeStack.Item item = stack.getStackItem(argTypes.length);
		return item.getRegisterNumber() == 0;
	}

	/**
	 * after collecting all method calls, build a report of all methods that have been called,
	 * but in a way that is less permissive then is defined.
	 */
	@Override
	public void report() {
		for (Map.Entry<StatisticsKey, MethodInfo> entry : Statistics.getStatistics()) {
			MethodInfo mi = entry.getValue();

			int declaredAccess = mi.getDeclaredAccess();
			if ((declaredAccess & Constants.ACC_PRIVATE) != 0) {
				continue;
			}

			if (mi.wasCalledPublicly() || !mi.wasCalled()) {
				continue;
			}

			StatisticsKey key = entry.getKey();

			if (isOverlyPermissive(declaredAccess, mi)) {
				try {
					if (!isDerived(Repository.lookupClass(key.getClassName()), key)) {
	
						BugInstance bi = new BugInstance(this, BugType.OPM_OVERLY_PERMISSIVE_METHOD.name(), NORMAL_PRIORITY)
										.addClass(key.getClassName())
										.addMethod(key.getClassName(), key.getMethodName(), key.getSignature(), (declaredAccess & Constants.ACC_STATIC) != 0);

						String descr = String.format("- Method declared %s but could be declared %s", getDeclaredAccessValue(declaredAccess), getRequiredAccessValue(mi));
						bi.addString(descr);
						
						bugReporter.reportBug(bi);
					}
				} catch (ClassNotFoundException cnfe) {
					bugReporter.reportMissingClass(cnfe);
				}
			}
		}
	}

	private static boolean isOverlyPermissive(int declaredAccess, MethodInfo mi) {
		if ((declaredAccess & Constants.ACC_PUBLIC) != 0) {
			return true;
		}

		//TODO: add more permission checks
		return false;
	}

	/**
	 * looks to see if this method described by key is derived from a superclass or interface
	 * 
	 * @param cls the class that the method is defined in
	 * @param key the information about the method
	 * @return whether this method derives from something or not
	 */
	private boolean isDerived(JavaClass cls, StatisticsKey key) {
		try {
			for (JavaClass infCls : cls.getInterfaces()) {
				for (Method infMethod : infCls.getMethods()) {
					if (key.getMethodName().equals(infMethod.getName())) {
						if (infMethod.getGenericSignature() != null) { 
							if (SignatureUtils.compareGenericSignature(infMethod.getGenericSignature(), key.getSignature())) {
								return true;
							}
						}
						else if (infMethod.getSignature().equals(key.getSignature())) {
							return true;
						}
					}
				}
			}

			JavaClass superClass = cls.getSuperClass();
			if ((superClass == null) || "java.lang.Object".equals(superClass.getClassName())) {
				return false;
			}

			for (Method superMethod : superClass.getMethods()) {
				if (key.getMethodName().equals(superMethod.getName())) {
					if (superMethod.getGenericSignature() != null) { 
						if (SignatureUtils.compareGenericSignature(superMethod.getGenericSignature(), key.getSignature())) {
							return true;
						}
					}
					else if (superMethod.getSignature().equals(key.getSignature())) {
						return true;
					}
				}
			}

			return isDerived(superClass, key);
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
			return true;
		}
	}
	
	private static String getDeclaredAccessValue(int declaredAccess) {
		return DECLARED_ACCESS.get(Integer.valueOf(declaredAccess & (Constants.ACC_PRIVATE|Constants.ACC_PROTECTED|Constants.ACC_PUBLIC)));
	}
	
	private static Object getRequiredAccessValue(MethodInfo mi) {
		if (mi.wasCalledProtectedly())
			return "protected";
		if (mi.wasCalledPackagely())
			return "package private";
		return "private";
	}
}
