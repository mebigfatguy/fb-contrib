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

import java.util.Map;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.collect.MethodInfo;
import com.mebigfatguy.fbcontrib.collect.Statistics;
import com.mebigfatguy.fbcontrib.collect.StatisticsKey;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.classfile.ClassDescriptor;

public class OverlyPermissiveMethod extends BytecodeScanningDetector {

	private BugReporter bugReporter;
	private OpcodeStack stack;
	private String callingPackage;
	private String callingClass;
	
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
		stack.resetForMethodEntry(this);
		super.visitCode(obj);
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
	
	public boolean isCallingOnThis(String sig) {
		Type[] argTypes = Type.getArgumentTypes(sig);
		if (stack.getStackDepth() < argTypes.length) {
			return false;
		}
		
		OpcodeStack.Item item = stack.getStackItem(argTypes.length);
		return item.getRegisterNumber() == 0;
	}
	
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
				if (!isDerived(getClassContext().getJavaClass(), getMethod())) {
				
					bugReporter.reportBug(new BugInstance(this, "OPM_OVERLY_PERMISSIVE_METHOD", NORMAL_PRIORITY)
									.addClass(key.getClassName())
									.addMethod(key.getClassName(), key.getMethodName(), key.getSignature(), (declaredAccess & Constants.ACC_STATIC) != 0));
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
	
	private boolean isDerived(JavaClass cls, Method m) {
		try {
			for (JavaClass infCls : cls.getInterfaces()) {
				for (Method infMethod : infCls.getMethods()) {
					if (m.getName().equals(infMethod.getName()) && m.getSignature().equals(infMethod.getSignature())) {
						return true;
					}
				}
			}
			
			JavaClass superClass = cls.getSuperClass();
			if ("java/lang/Object".equals(superClass.getClassName())) {
				return false;
			}
			
			for (Method superMethod : superClass.getMethods()) {
				if (m.getName().equals(superMethod.getName()) && m.getSignature().equals(superMethod.getSignature())) {
					return true;
				}
			}
			
			return isDerived(superClass, m);
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
			return true;
		}
	}
}
