/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2013 Dave Brosius
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

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for implementation of clone() where a store is made to a member
 * of the source object.
 */
@CustomUserValue
public class SuspiciousCloneAlgorithm extends BytecodeScanningDetector {
	
	private static JavaClass cloneableClass;
	private static Map<String, Integer> changingMethods;
	static {
		try {
			cloneableClass = Repository.lookupClass("java/lang/Cloneable");
			changingMethods = new HashMap<String, Integer>();
			changingMethods.put("add", Integer.valueOf(NORMAL_PRIORITY));
			changingMethods.put("addAll", Integer.valueOf(NORMAL_PRIORITY));
			changingMethods.put("put", Integer.valueOf(NORMAL_PRIORITY));
			changingMethods.put("putAll", Integer.valueOf(NORMAL_PRIORITY));
			changingMethods.put("insert", Integer.valueOf(LOW_PRIORITY));
			changingMethods.put("set", Integer.valueOf(LOW_PRIORITY));
			
		} catch (ClassNotFoundException cnfe) {
			cloneableClass = null;
		}
	}
	
	private BugReporter bugReporter;
	private OpcodeStack stack;
	
	/**
     * constructs a SCA detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
	 */
	public SuspiciousCloneAlgorithm(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}
	
	/**
	 * override the visitor to look for classes that implement Cloneable
	 * 
	 * @param classContext the context object of the class to be checked
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		if (cloneableClass == null)
			return;
		
		try {
			JavaClass cls = classContext.getJavaClass();
			if (cls.implementationOf(cloneableClass)) {
				stack = new OpcodeStack();
				super.visitClassContext(classContext);
			}
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
		} finally {
			stack = null;
		}
	}
	
	/**
	 * override the visitor to only continue for the clone method
	 * 
	 * @param obj the context object of the currently parsed method
	 */
	@Override
	public void visitCode(Code obj) {
		Method m = getMethod();
		if (!m.isStatic() && "clone".equals(m.getName()) && "()Ljava/lang/Object;".equals(m.getSignature()))
			super.visitCode(obj);
	}
	
	/**
	 * override the visitor to look for stores to member fields of the source object on a clone
	 * 
	 * @param seen the opcode of the currently parsed instruction
	 */
	@Override
	public void sawOpcode(int seen) {
		boolean srcField = false;
		try {
			stack.mergeJumps(this);
			switch (seen) {
				case ALOAD_0:
					srcField = true;
				break;
				
				case DUP:
					if (stack.getStackDepth() > 0) {
						OpcodeStack.Item item = stack.getStackItem(0);
						if (item.getUserValue() != null)
							srcField = true;
					}
				break;
				
				case GETFIELD:
					if (stack.getStackDepth() > 0) {
						OpcodeStack.Item item = stack.getStackItem(0);
						if (item.getRegisterNumber() == 0) {
							srcField = true;
						}
					}
				break;
				
				case PUTFIELD:
					if (stack.getStackDepth() >= 2) {
						OpcodeStack.Item item = stack.getStackItem(1);
						if ((item.getRegisterNumber() == 0) || (item.getUserValue() != null)) {
							bugReporter.reportBug(new BugInstance(this, "SCA_SUSPICIOUS_CLONE_ALGORITHM", NORMAL_PRIORITY)
									   .addClass(this)
									   .addMethod(this)
									   .addSourceLine(this));
						}
					}
					
				break;
				
				case INVOKEINTERFACE:
				case INVOKEVIRTUAL:
					String sig = getSigConstantOperand();
					int numArgs = Type.getArgumentTypes(sig).length;
					if (stack.getStackDepth() > numArgs) {
						OpcodeStack.Item item = stack.getStackItem(numArgs);
						if ((item.getRegisterNumber() == 0) || (item.getUserValue() != null)) {
							String name = getNameConstantOperand();
							Integer priority = changingMethods.get(name);
							if (priority != null)
								bugReporter.reportBug(new BugInstance(this, "SCA_SUSPICIOUS_CLONE_ALGORITHM", priority.intValue())
								   .addClass(this)
								   .addMethod(this)
								   .addSourceLine(this));
						}
					}
				break;
			}
		} finally {
			TernaryPatcher.pre(stack, seen);
			stack.sawOpcode(this, seen);
			TernaryPatcher.post(stack, seen);
			if (srcField && stack.getStackDepth() > 0) {
				OpcodeStack.Item item = stack.getStackItem(0);
				item.setUserValue(Boolean.TRUE);
			}
		}
	}
}
