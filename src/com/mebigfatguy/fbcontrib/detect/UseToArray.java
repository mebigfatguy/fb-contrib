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

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for code that builds an array of values from a collection, by manually looping
 * over the elements of the collection, and adding them to the array. It is simpler and
 * cleaner to use mycollection.toArray(new type[mycollection.size()].
 */
@CustomUserValue
public class UseToArray extends BytecodeScanningDetector 
{
	private JavaClass collectionClass;
	private ClassNotFoundException ex;	
	private BugReporter bugReporter;
	private OpcodeStack stack;
	private Map<Integer, Object> userValues;
	
	/**
     * constructs a UTA detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
	 */
	public UseToArray(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
		try {
			collectionClass = Repository.lookupClass("java/util/Collection");
		} catch (ClassNotFoundException cnfe) {
			collectionClass = null;
			ex = cnfe;
		}
	}
	
	/**
	 * implements the visitor to create and clear the stack, and report missing class errors
	 * 
	 * @param classContext the context object of the currently parsed class
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		if (collectionClass == null) {
			if (ex != null) {
				bugReporter.reportMissingClass(ex);
				ex = null;
			}
			return;
		}
		
		try {	
			stack = new OpcodeStack();
			super.visitClassContext(classContext);
		} finally {
			stack = null;
		}
	}
	
	/**
	 * implements the visitor to reset the stack and uservalues
	 * 
	 * @param obj the context object of the currently parsed code block
	 */
	@Override
	public void visitCode(Code obj) {
		try {
			stack.resetForMethodEntry(this);
			userValues = new HashMap<Integer, Object>();
			super.visitCode(obj);
		} finally {
			userValues = null;
		}
	}
	
	/**
	 * implements the visitor to look for manually copying of collections to arrays
	 * 
	 * @param seen the opcode of the currently parsed instruction
	 */
	@Override
	public void sawOpcode(int seen) {
		int reg = -1;
		Object uValue = null;
		boolean sawAlias = false;
		boolean sawLoad = false;
		boolean sawNewArray = false;
		
		try {
	        stack.precomputation(this);
	        
			if (seen == INVOKEINTERFACE) {
				String methodName = getNameConstantOperand();
				String signature = getSigConstantOperand();
				if ("size".equals(methodName) && "()I".equals(signature)) {
					if (stack.getStackDepth() > 0) {
						OpcodeStack.Item itm = stack.getStackItem(0);
						reg = isLocalCollection(itm);
						if (reg >= 0) {
							sawAlias = true;
						}
					}
				} else if ("get".equals(methodName) && "(I)Ljava/lang/Object;".equals(signature)) {
					if (stack.getStackDepth() > 1) {
						OpcodeStack.Item itm = stack.getStackItem(1);
						reg = isLocalCollection(itm);
						if (reg >= 0) {
							sawAlias = true;
						}
					}
				} else if ("keySet".equals(methodName) || "values".equals(methodName) || "iterator".equals(methodName) || "next".equals(methodName)) {
					if (stack.getStackDepth() > 0) {
						OpcodeStack.Item itm = stack.getStackItem(0);
						reg = isLocalCollection(itm);
						if (reg >= 0) {
							sawAlias = true;
						}
					}
				}
			} else if (((seen == ISTORE) || ((seen >= ISTORE_0) && (seen <= ISTORE_3)))
				   ||  ((seen == ASTORE) || ((seen >= ASTORE_0) && (seen <= ASTORE_3)))) {
				if (stack.getStackDepth() > 0) {
					uValue = stack.getStackItem(0).getUserValue();
					userValues.put(Integer.valueOf(RegisterUtils.getStoreReg(this, seen)), uValue); 
				}
			} else if (((seen == ILOAD) || ((seen >= ILOAD_0) && (seen <= ILOAD_3)))
				   ||  ((seen == ALOAD) || ((seen >= ALOAD_0) && (seen <= ALOAD_3)))) {
				sawLoad = true;
			} else if (seen == ANEWARRAY) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item itm = stack.getStackItem(0);
					uValue = itm.getUserValue();
					sawNewArray = true;
				}
			} else if (seen == IF_ICMPGE) {
				if (stack.getStackDepth() > 1) {
					OpcodeStack.Item itm1 = stack.getStackItem(1);
					OpcodeStack.Item itm2 = stack.getStackItem(0);
					reg = itm1.getRegisterNumber();
					if ((reg >= 0) && (itm1.couldBeZero())) {
						uValue = itm2.getUserValue();
						if (uValue != null) {
							userValues.put(Integer.valueOf(reg), uValue);
						}
					}
				}
			} else if ((seen >= IASTORE) && (seen <= SASTORE)) {
				if (stack.getStackDepth() > 2) {
					OpcodeStack.Item arItem = stack.getStackItem(2);
					OpcodeStack.Item idxItem = stack.getStackItem(1);
					OpcodeStack.Item valueItem = stack.getStackItem(0);
					reg = isLocalCollection(arItem);
					if ((reg >= 0)
					&&  (idxItem.getUserValue() != null)
					&&  (valueItem.getUserValue() != null)) {
						bugReporter.reportBug(new BugInstance(this, BugType.UTA_USE_TO_ARRAY.name(), NORMAL_PRIORITY)
									.addClass(this)
									.addMethod(this)
									.addSourceLine(this));
					}
				}
			} else if (seen == CHECKCAST) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item itm = stack.getStackItem(0);
					uValue = itm.getUserValue();
					if (uValue instanceof Integer) {
						reg = ((Integer)uValue).intValue();
						sawAlias = true;
					}
				}
			}
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
		} finally {
			TernaryPatcher.pre(stack, seen);
			stack.sawOpcode(this, seen);
			TernaryPatcher.post(stack, seen);
			if (sawAlias) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item itm = stack.getStackItem(0);
					itm.setUserValue(Integer.valueOf(reg));
				}
			} else if (sawLoad) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item itm = stack.getStackItem(0);
					reg = itm.getRegisterNumber();
					if (reg >= 0) {
						uValue = userValues.get(Integer.valueOf(reg));
						itm.setUserValue(uValue);
					}
				}
			} else if (sawNewArray) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item itm = stack.getStackItem(0);
					itm.setUserValue(uValue);
				}
			}
		}
	}
	
	/**
	 * determines if the stack item refers to a collection that is stored in a local variable
	 * 
	 * param item the stack item to check
	 * 
	 * @return the register number of the local variable that this collection refers to, or -1
	 * @throws ClassNotFoundException if the items class cannot be found
	 */
	private int isLocalCollection(OpcodeStack.Item item) throws ClassNotFoundException {
		Integer aliasReg = (Integer)item.getUserValue();
		if (aliasReg != null)
			return aliasReg.intValue();
		
		int reg = item.getRegisterNumber();
		if (reg < 0)
			return -1;
		
		JavaClass cls = item.getJavaClass();
		if ((cls != null) && cls.implementationOf(collectionClass))
			return reg;
		
		return -1;
	}
	
}
