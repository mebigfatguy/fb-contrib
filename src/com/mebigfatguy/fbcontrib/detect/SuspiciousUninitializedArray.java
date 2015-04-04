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

import java.util.BitSet;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for creation of arrays, that are not populated before being returned
 * for a method. While it is possible that the method that called this method
 * will do the work of populated the array, it seems odd that this would be the case.
 */
@CustomUserValue
public class SuspiciousUninitializedArray extends BytecodeScanningDetector
{
	private static final String UNINIT_ARRAY = "Unitialized Array";
	private static JavaClass THREAD_LOCAL_CLASS;
	private static final String INITIAL_VALUE = "initialValue";
	
	static {
		try {
			THREAD_LOCAL_CLASS = Repository.lookupClass(ThreadLocal.class);
		} catch (ClassNotFoundException e) {
			THREAD_LOCAL_CLASS = null;
		}
	}
	
	private final BugReporter bugReporter;
	private OpcodeStack stack;
	private String returnArraySig;
	private BitSet uninitializedRegs;

	/**
	 * constructs a SUA detector given the reporter to report bugs on
	 * @param bugReporter the sync of bug reports
	 */
	public SuspiciousUninitializedArray(BugReporter bugReporter) {
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
			uninitializedRegs = new BitSet();
			super.visitClassContext(classContext);
		} finally {
			stack = null;
			uninitializedRegs = null;
		}
	}

	/**
	 * overrides the visitor to check to see if the method returns an array,
	 * and if so resets the stack for this method.
	 * 
	 * @param obj the context object for the currently parsed code block
	 */
	@Override
	public void visitCode(Code obj) {
		String sig = getMethod().getSignature();
		int sigPos = sig.indexOf(")[");
		if (sigPos >= 0) {
			Method m = getMethod();
			if (m.getName().equals(INITIAL_VALUE)) {
				try {
					if ((THREAD_LOCAL_CLASS == null) || getClassContext().getJavaClass().instanceOf(THREAD_LOCAL_CLASS)) {
						return;
					}
				} catch (ClassNotFoundException e) {
					return;
				}
			}
			
			stack.resetForMethodEntry(this);
			returnArraySig = sig.substring(sigPos + 1);
			uninitializedRegs.clear();
			super.visitCode(obj);
		}
	}

	/**
	 * overrides the visitor to annotate new array creation with a user value
	 * that denotes it as being uninitialized, and then if the array is populated
	 * to remove that user value. It then finds return values that have uninitialized
	 * arrays
	 * 
	 * @param seen the context parameter of the currently parsed op code
	 */
	@Override
	public void sawOpcode(int seen) {
		Object userValue = null;
		try {
	        stack.precomputation(this);
	        
			switch (seen) {
				case NEWARRAY: {
					if (!isTOS0()) {
						int typeCode = getIntConstant();
						String sig = "[" + SignatureUtils.getTypeCodeSignature(typeCode);
						if (returnArraySig.equals(sig)) {
							userValue = UNINIT_ARRAY;
						}
					}
				}
				break;
				
				case ANEWARRAY: {
					if (!isTOS0()) {
						String sig = "[L" + getClassConstantOperand() + ";";
						if (returnArraySig.equals(sig)) {
							userValue = UNINIT_ARRAY;
						}
					}
				}
				break;
				
				case MULTIANEWARRAY: {
					if (returnArraySig.equals(getClassConstantOperand())) {
						userValue = UNINIT_ARRAY;
					}
				}
				break;
				
				case INVOKEVIRTUAL:
				case INVOKEINTERFACE:
				case INVOKESPECIAL:
				case INVOKESTATIC: {
					String methodSig = getSigConstantOperand();
					Type[] types = Type.getArgumentTypes(methodSig);
					for (int t = 0; t < types.length; t++) {
						Type type = types[t];
						String parmSig = type.getSignature();
						if (returnArraySig.equals(parmSig) || "Ljava/lang/Object;".equals(parmSig) || "[Ljava/lang/Object;".equals(parmSig)) {
							int parmIndex = types.length - t - 1;
							if (stack.getStackDepth() > parmIndex) {
								OpcodeStack.Item item = stack.getStackItem(parmIndex);
								if (item.getUserValue() != null) {
									userValue = item.getUserValue();
									int reg;
									if (userValue instanceof Integer) {
										reg = ((Integer)userValue).intValue();
									} else {
										reg = item.getRegisterNumber();
									}
									item.setUserValue(null);
									if (reg >= 0) {
										uninitializedRegs.clear(reg);
									}
									userValue = null;
								}
							}
						}
					}
				}
				break;
				
				case AALOAD: {
					if (stack.getStackDepth() >= 2) {
						OpcodeStack.Item item = stack.getStackItem(1);
						if (UNINIT_ARRAY.equals(item.getUserValue())) {
							userValue = Integer.valueOf(item.getRegisterNumber());
						}
					}
				}
				break;
				
				
				case IASTORE:
				case LASTORE:
				case FASTORE:
				case DASTORE:
				case AASTORE:
				case BASTORE:
				case CASTORE:
				case SASTORE: {
					if (stack.getStackDepth() >= 3) {
						OpcodeStack.Item item = stack.getStackItem(2);
						userValue = item.getUserValue();
						int reg;
						if (userValue instanceof Integer) {
							reg = ((Integer)userValue).intValue();
						} else {
							reg = item.getRegisterNumber();
						}
						item.setUserValue(null);
						if (reg >= 0) {
							uninitializedRegs.clear(reg);
						}
						userValue = null;
					} else {
					    //error condition - stack isn't right
					    uninitializedRegs.clear();
					}
				}
				break;
				
				case ASTORE:
				case ASTORE_0:
				case ASTORE_1:
				case ASTORE_2:
				case ASTORE_3: {
					int reg = RegisterUtils.getAStoreReg(this, seen);
					if (stack.getStackDepth() > 0) {
						OpcodeStack.Item item = stack.getStackItem(0);
						uninitializedRegs.set(reg, (UNINIT_ARRAY.equals(item.getUserValue())));
					} else {
						uninitializedRegs.clear(reg);
					}
				}
				break;
				
				case ALOAD:
				case ALOAD_0:
				case ALOAD_1:
				case ALOAD_2:
				case ALOAD_3: {
					int reg = RegisterUtils.getALoadReg(this, seen);
					if (uninitializedRegs.get(reg)) {
						userValue = UNINIT_ARRAY;
					}
				}
				break;
				
				case PUTFIELD: {
					if (stack.getStackDepth() > 0) {
						OpcodeStack.Item item = stack.getStackItem(0);
						item.setUserValue(null);
						int reg = item.getRegisterNumber();
						if (reg >= 0) {
							uninitializedRegs.clear(reg);
						}
					}
				}
				break;
				
				case ARETURN: {
					if (stack.getStackDepth() > 0) {
						OpcodeStack.Item item = stack.getStackItem(0);
						if (UNINIT_ARRAY.equals(item.getUserValue()))
							bugReporter.reportBug(new BugInstance(this, BugType.SUA_SUSPICIOUS_UNINITIALIZED_ARRAY.name(), NORMAL_PRIORITY)
							           .addClass(this)
							           .addMethod(this)
							           .addSourceLine(this));
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
			if (stack.getStackDepth() > 0) {
				OpcodeStack.Item item = stack.getStackItem(0);
				item.setUserValue(userValue);
			}
		}
	}
	
	private boolean isTOS0() {
		if (stack.getStackDepth() > 0) {
			OpcodeStack.Item item = stack.getStackItem(0);
			return item.mustBeZero();
		}
		
		return false;
	}
}
