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
import java.util.Iterator;
import java.util.Map;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;

import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XField;

/**
 * looks for loops that transfers the contents of one collection to another. These collection sources
 * might be local variables or member fields, including sets, maps key/values, lists, or arrays. 
 * It is simpler to just use the addAll method of the collection class. In the case where the 
 * source is an array, you can use Arrays.asList(array), and use that as the source to addAll.
 */
@CustomUserValue
public class UseAddAll extends BytecodeScanningDetector {
	private JavaClass collectionClass;
	private ClassNotFoundException ex;	
	private final BugReporter bugReporter;
	private OpcodeStack stack;
	/** register/field to alias register/field */
	private Map<Comparable<?>, Comparable<?>> userValues;
	/** alias register to loop info  */
	private Map<Comparable<?>, LoopInfo> loops;
	private boolean isInstanceMethod;
	
	/**
     * constructs a UTA detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
	 */
	public UseAddAll(BugReporter bugReporter) {
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
	 * implements the visitor to reset the stack and userValues and loops
	 * 
	 * @param obj the context object of the currently parsed code block
	 */
	@Override
	public void visitCode(Code obj) {
		try {
			stack.resetForMethodEntry(this);
			userValues = new HashMap<Comparable<?>, Comparable<?>>();
			loops = new HashMap<Comparable<?>, LoopInfo>();
			isInstanceMethod = !getMethod().isStatic();
			super.visitCode(obj);
		} finally {
			userValues = null;
			loops = null;
		}
	}
	
	/**
	 * implements the visitor to look for manually copying of collections to collections
	 * 
	 * @param seen the opcode of the currently parsed instruction
	 */
	@Override
	public void sawOpcode(int seen) {
		Comparable<?> regOrField = null;
		Comparable<?> uValue;
		boolean sawAlias = false;
		boolean sawLoad = false;
		
		try {
			int pc = getPC();
			Iterator<LoopInfo> it = loops.values().iterator();
			while (it.hasNext()) {
				LoopInfo loop = it.next();
				if ((loop.getEndPC()-3) <= pc) {
					int loopPC = loop.getAddPC();
					if (loopPC > 0) {
						bugReporter.reportBug(new BugInstance(this, "UAA_USE_ADD_ALL", NORMAL_PRIORITY)
								   .addClass(this)
								   .addMethod(this)
								   .addSourceLine(this, loopPC));
					}
					it.remove();
				} else if ((loop.getEndPC() > pc) && (loop.addPC < (pc - 5)) &&  (loop.addPC > 0)) {
					it.remove();
				}
			}
			
			if (seen == INVOKEINTERFACE) {
				String methodName = getNameConstantOperand();
				String signature = getSigConstantOperand();
				if ("get".equals(methodName) && "(I)Ljava/lang/Object;".equals(signature)) {
					if (stack.getStackDepth() > 1) {
						OpcodeStack.Item itm = stack.getStackItem(1);
						int reg = isLocalCollection(itm);
						if (reg >= 0) {
							regOrField = Integer.valueOf(reg);
							sawAlias = true;
						} else {
							String field = isFieldCollection(itm);
							if (field != null) {
								regOrField = field;
								sawAlias = true;
							}
						}
					}
				} else if ("keySet".equals(methodName) || "values".equals(methodName) || "iterator".equals(methodName) || "next".equals(methodName) || "hasNext".equals(methodName)) {
					if (stack.getStackDepth() > 0) {
						OpcodeStack.Item itm = stack.getStackItem(0);
						int reg = isLocalCollection(itm);
						if (reg >= 0) {
							regOrField = Integer.valueOf(reg);
							sawAlias = true;
						} else {
							String field = isFieldCollection(itm);
							if (field != null) {
								regOrField = field;
								sawAlias = true;
							}
						}
					}
				} else if ("add".equals(methodName) && "(Ljava/lang/Object;)Z".equals(signature)) {
					if (stack.getStackDepth() > 1) {
						OpcodeStack.Item colItem = stack.getStackItem(1);
						OpcodeStack.Item valueItem = stack.getStackItem(0);
						int reg = isLocalCollection(colItem);
						if (reg >= 0) {
							regOrField = Integer.valueOf(reg);
							uValue = (Comparable<?>)valueItem.getUserValue();
							if (uValue != null) {
								LoopInfo loop = loops.get(uValue);
								if (loop != null) {
									if (loop.isInLoop(pc)) {
										if (this.getCodeByte(getNextPC()) == POP) {
											loop.foundAdd(pc);
										}
									}
								}
							}
						} else {
							String field = isFieldCollection(colItem);
							if (field != null) {
								regOrField = field;
								uValue = (Comparable<?>)valueItem.getUserValue();
								if (uValue != null) {
									LoopInfo loop = loops.get(uValue);
									if (loop != null) {
										if (loop.isInLoop(pc)) {
											if (this.getCodeByte(getNextPC()) == POP) {
												loop.foundAdd(pc);
											}
										}
									}
								}
							}
						}
					}
				}
			} else if (((seen == ISTORE) || ((seen >= ISTORE_0) && (seen <= ISTORE_3)))
				   ||  ((seen == ASTORE) || ((seen >= ASTORE_0) && (seen <= ASTORE_3)))) {
				if (stack.getStackDepth() > 0) {
					uValue = (Comparable<?>)stack.getStackItem(0).getUserValue();
					userValues.put(Integer.valueOf(RegisterUtils.getStoreReg(this, seen)), uValue); 
				}
			} else if (((seen == ILOAD) || ((seen >= ILOAD_0) && (seen <= ILOAD_3)))
				   ||  ((seen == ALOAD) || ((seen >= ALOAD_0) && (seen <= ALOAD_3)))) {
				sawLoad = true;
			} else if (seen == IFEQ) {
				boolean loopFound = false;
				if (stack.getStackDepth() > 0) {
					if (getBranchOffset() > 0) {
						int gotoPos = getBranchTarget() - 3;
						byte[] code = getCode().getCode();
						if ((0x00FF & code[gotoPos]) == GOTO) {
							short brOffset = (short)(0x0FF & code[gotoPos+1]);
							brOffset <<= 8;
							brOffset |= (0x0FF & code[gotoPos+2]);
							gotoPos += brOffset;
							if (gotoPos < pc) {
								OpcodeStack.Item itm = stack.getStackItem(0);
								uValue = (Comparable<?>)itm.getUserValue();
								if (uValue != null) {
									loops.put(uValue, new LoopInfo(pc, getBranchTarget()));
								}
								loopFound = true;
							}
						}
						
						if (!loopFound) {
							removeLoop(pc);
						}
					}
				}
			} else if (isInstanceMethod && (seen == PUTFIELD)) {
				if (stack.getStackDepth() > 1) {
					OpcodeStack.Item item = stack.getStackItem(1);
					if (item.getRegisterNumber() == 0) {
						uValue = (Comparable<?>)stack.getStackItem(0).getUserValue();
						userValues.put(getNameConstantOperand(), uValue);
					}
				}
			} else if (isInstanceMethod && (seen == GETFIELD)) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item item = stack.getStackItem(0);
					if (item.getRegisterNumber() == 0) {
						sawLoad = true;
					}
				}
			} else if (((seen > IFEQ) && (seen <= GOTO)) || (seen == IFNULL) || (seen == IFNONNULL)) {
				removeLoop(pc);
			} else if (seen == CHECKCAST) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item itm = stack.getStackItem(0);
					uValue = (Comparable<?>)itm.getUserValue();
					if (uValue != null) {
						regOrField = uValue;
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
					itm.setUserValue(regOrField);
				}
			} else if (sawLoad) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item itm = stack.getStackItem(0);
					int reg = itm.getRegisterNumber();
					if (reg >= 0) {
						uValue = userValues.get(Integer.valueOf(reg));
						itm.setUserValue(uValue);
					} else {
						XField xField = itm.getXField();
						if (xField != null) {
							uValue = userValues.get(xField.getName());
							itm.setUserValue(uValue);
						}
					}
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
		Comparable<?> aliasReg = (Comparable<?>)item.getUserValue();
		if (aliasReg instanceof Integer)
			return ((Integer)aliasReg).intValue();
		
		int reg = item.getRegisterNumber();
		if (reg < 0)
			return -1;
		
		JavaClass cls = item.getJavaClass();
		if ((cls != null) && cls.implementationOf(collectionClass))
			return reg;
		
		return -1;
	}
	
	/**
	 * determines if the stack item refers to a collection that is stored in a field
	 * 
	 * param item the stack item to check
	 * 
	 * @return the field name of the collection, or null
	 * @throws ClassNotFoundException if the items class cannot be found
	 */	
	private String isFieldCollection(OpcodeStack.Item item) throws ClassNotFoundException {
			Comparable<?> aliasReg = (Comparable<?>)item.getUserValue();
			if (aliasReg instanceof String)
				return (String)aliasReg;
			
			XField field = item.getXField();
			if (field == null)
				return null;
			
			JavaClass cls = item.getJavaClass();
			if ((cls != null) && cls.implementationOf(collectionClass))
				return field.getName();
			
			return null;
	}
	
	private void removeLoop(int pc) {
		Iterator<LoopInfo> it = loops.values().iterator();
		while (it.hasNext()) {
			if (it.next().isInLoop(pc)) {
				it.remove();
			}
		}
	}
	
	static class LoopInfo
	{
		private final int start;
		private final int end;
		private int addPC;
		
		public LoopInfo(int loopStart, int loopEnd)
		{
			start = loopStart;
			end = loopEnd;
			addPC = 0;
		}
				
		public boolean isInLoop(int pc)
		{
			return ((pc >= start) && (pc <= end));
		}
		
		public void foundAdd(int pc) {
			if (addPC == 0)
				addPC = pc;
			else
				addPC = -1;
		}
		
		public int getStartPC() {
			return start;
		}
		
		public int getEndPC() {
			return end;
		}
		
		public int getAddPC() {
			return addPC;
		}
	}
}
