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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.FieldAnnotation;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.SourceLineAnnotation;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XField;

/**
 * looks for private collection members, either static or instance, that are only initialized in
 * the clinit or init, but are synchronized. This is not necessary as the constructor or static 
 * initializer are guaranteed to be thread safe.
 */
@CustomUserValue
public class NeedlessMemberCollectionSynchronization extends BytecodeScanningDetector {
	private static JavaClass collectionClass;
	static {
		try {
			collectionClass = Repository.lookupClass("java/util/Collection");
		} catch (ClassNotFoundException cnfe) {
			collectionClass = null;
		}
	}
	private static JavaClass mapClass;
	static {
		try {
			mapClass = Repository.lookupClass("java/util/Map");
		} catch (ClassNotFoundException cnfe) {
			mapClass = null;
		}
	}
	private static Set<String> syncCollections = new HashSet<String>();
	static {
		syncCollections.add("java/util/Vector");
		syncCollections.add("java/util/Hashtable");
	}
	private static Set<String> modifyingMethods = new HashSet<String>();
	static {
		modifyingMethods.add("add");
		modifyingMethods.add("addAll");
		modifyingMethods.add("addFirst");
		modifyingMethods.add("addElement");
		modifyingMethods.add("addLast");
		modifyingMethods.add("clear");
		modifyingMethods.add("insertElementAt");
		modifyingMethods.add("put");
		modifyingMethods.add("remove");
		modifyingMethods.add("removeAll");
		modifyingMethods.add("removeAllElements");
		modifyingMethods.add("removeElement");
		modifyingMethods.add("removeElementAt");
		modifyingMethods.add("removeFirst");
		modifyingMethods.add("removeLast");
		modifyingMethods.add("removeRange");
		modifyingMethods.add("retainAll");
		modifyingMethods.add("set");
		modifyingMethods.add("setElementAt");
		modifyingMethods.add("setSize");
	}
	private static final int IN_METHOD = 0;
	private static final int IN_CLINIT = 1;
	private static final int IN_INIT = 2;
	
	private BugReporter bugReporter;
	private Map<String, FieldInfo> collectionFields;
	private Map<Integer, String> aliases;
	private OpcodeStack stack;
	private int state;
	private String className;
	
	/**
     * constructs a NMCS detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
	 */	
	public NeedlessMemberCollectionSynchronization(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}
	
	/**
	 * implements the visitor to clear the collectionFields and stack
	 * and to report collections that remain unmodified out of clinit or init
	 * 
	 * @param classContext the context object of the currently parsed class
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			if ((collectionClass != null) && (mapClass != null)) {
				collectionFields = new HashMap<String, FieldInfo>();
				aliases = new HashMap<Integer, String>();
				stack = new OpcodeStack();
				JavaClass cls = classContext.getJavaClass();
				className = cls.getClassName();
				super.visitClassContext(classContext);
				for (FieldInfo fi : collectionFields.values()) {
					if (fi.isSynchronized()) {
						bugReporter.reportBug(new BugInstance(this, "NMCS_NEEDLESS_MEMBER_COLLECTION_SYNCHRONIZATION", NORMAL_PRIORITY)
									.addClass(this)
									.addField(fi.getFieldAnnotation()));
					}
				}
			}
		} finally {
			collectionFields = null;
			aliases = null;
			stack = null;
		}
	}
	
	/**
	 * implements the visitor to find collection fields
	 * 
	 * @param obj the context object of the currently parse field
	 */
	@Override
	public void visitField(Field obj) {
		if (obj.isPrivate()) {
			String signature = obj.getSignature();
			if (signature.charAt(0) == 'L') {
				try {
					JavaClass cls = Repository.lookupClass(signature.substring(1, signature.length() - 1));
					if (cls.implementationOf(collectionClass) || cls.implementationOf(mapClass)) {
						FieldAnnotation fa = FieldAnnotation.fromVisitedField(this);
						collectionFields.put(fa.getFieldName(), new FieldInfo(fa));
					}
				} catch (ClassNotFoundException cnfe) {
					bugReporter.reportMissingClass(cnfe);
				}
			}
		}
	}
	
	/**
	 * implements the visitor to set the state based on the type of method being parsed
	 * 
	 * @param obj the context object of the currently parsed code block
	 */
	@Override
	public void visitCode(Code obj) {
		if (collectionFields.size() > 0) {
			aliases.clear();
			String methodName = getMethodName();
			if ("<clinit>".equals(methodName))
				state = IN_CLINIT;
			else if ("<init>".equals(methodName))
				state = IN_INIT;
			else
				state = IN_METHOD;
			stack.resetForMethodEntry(this);
			super.visitCode(obj);
		}
	}
	
	/**
	 * implements the visitor to call the approriate visitor based on state
	 * 
	 * @param seen the opcode of the currently parsed instruction
	 */
	@Override
	public void sawOpcode(int seen) {
		switch (state) {
			case IN_CLINIT:
				sawCLInitOpcode(seen);
			break;
			
			case IN_INIT:
				sawInitOpcode(seen);
			break;
			
			case IN_METHOD:
				sawMethodOpcode(seen);
			break;
		}
	}
	
	/**
	 * handle <clinit> blocks by looking for putstatic calls referencing
	 * synchronized collections
	 * 
	 * @param seen the opcode of the currently parsed instruction
	 */
	private void sawCLInitOpcode(int seen) {
		boolean isSyncCollection = false;
		try {
	        stack.precomputation(this);
	        
			isSyncCollection = isSyncCollectionCreation(seen);
			if (seen == PUTSTATIC)
				processCollectionStore();
		} finally {
			stack.sawOpcode(this, seen);
			if (isSyncCollection) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item item = stack.getStackItem(0);
					item.setUserValue(Boolean.TRUE);
				}
			}
		}
	}
	
	/**
	 * handle <init> blocks by looking for putfield calls referencing
	 * synchronized collections
	 * 
	 * @param seen the opcode of the currently parsed instruction
	 */
	private void sawInitOpcode(int seen) {
		boolean isSyncCollection = false;
		try {
			stack.mergeJumps(this);
			isSyncCollection = isSyncCollectionCreation(seen);
			if (seen == PUTFIELD)
				processCollectionStore();
		} finally {
			stack.sawOpcode(this, seen);
			if (isSyncCollection) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item item = stack.getStackItem(0);
					item.setUserValue(Boolean.TRUE);
				}
			}
		}
	}
	
	/**
	 * handles regular methods by looking for methods on collections that
	 * are modifying and removes those collections from the ones under review
	 * 
	 * @param seen the opcode of the currently parsed instruction
	 */
	private void sawMethodOpcode(int seen) {
		boolean isSyncCollection = false;
		try {
			stack.mergeJumps(this);
			isSyncCollection = isSyncCollectionCreation(seen);
			
			switch (seen) {
				case INVOKEVIRTUAL:
				case INVOKEINTERFACE:
					String methodName = getNameConstantOperand();
					if (modifyingMethods.contains(methodName)) {
						String signature = getSigConstantOperand();
						int parmCount = Type.getArgumentTypes(signature).length;
						if (stack.getStackDepth() > parmCount) {
							OpcodeStack.Item item = stack.getStackItem(parmCount);
							XField field = item.getXField();
							if (field != null) {
								collectionFields.remove(field.getName());
							} else {
								int reg = item.getRegisterNumber();
								if (reg >= 0) {
									Integer register = Integer.valueOf(reg);
									String fName = aliases.get(register);
									if (fName != null) {
										collectionFields.remove(fName);
										aliases.remove(register);
									}
								}
							}
						}
					}
                    removeCollectionParameters();
				break;
                
                case INVOKESTATIC:
                    removeCollectionParameters();
                break;
				
				case ARETURN:
					if (stack.getStackDepth() > 0) {
						OpcodeStack.Item item = stack.getStackItem(0);
						XField field = item.getXField();
						if (field != null) {
							collectionFields.remove(field.getName());
						}
					}
				break;
				
				case PUTFIELD:
				case PUTSTATIC:
					String fieldName = getNameConstantOperand();
					collectionFields.remove(fieldName);
				break;
				
				case GOTO:
				case GOTO_W:
					if (stack.getStackDepth() > 0) {
						OpcodeStack.Item item = stack.getStackItem(0);
						XField field = item.getXField();
						if (field != null) {
							collectionFields.remove(field.getName());
						}
					}
				break;
			}
		} finally {
			TernaryPatcher.pre(stack, seen);
			stack.sawOpcode(this, seen);
			TernaryPatcher.post(stack, seen);
			if (isSyncCollection) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item item = stack.getStackItem(0);
					item.setUserValue(Boolean.TRUE);
				}
			}
		}
		
	}
	
	/**
	 * returns whether this instruction is creating a synchronized collection
	 * 
	 * @param seen the opcode of the currently parsed instruction
	 * @return whether a synchronized collection has just been created
	 */
	private boolean isSyncCollectionCreation(int seen) {
		if (seen == INVOKESPECIAL) {
			if ("<init>".equals(getNameConstantOperand())) {
				return (syncCollections.contains(getClassConstantOperand()));
			}
		} else if (seen == INVOKESTATIC) {
			if ("java/util/Collections".equals(getClassConstantOperand())) {
				String methodName = getNameConstantOperand();
				return ("synchronizedMap".equals(methodName) || "synchronizedSet".equals(methodName));
			}
		}
		return false;
	}
	
	/**
	 * sets the source line annotation of a store to a collection if that collection
	 * is synchronized.
	 */
	private void processCollectionStore() {
		String fieldClassName = getDottedClassConstantOperand();
		if (fieldClassName.equals(className)) {
			if (stack.getStackDepth() > 0) {
				OpcodeStack.Item item = stack.getStackItem(0);
				if (item.getUserValue() != null) {
					String fieldName = getNameConstantOperand();
					if (fieldName != null) {
						FieldInfo fi = collectionFields.get(fieldName);
						if (fi != null) {
							fi.getFieldAnnotation().setSourceLines(SourceLineAnnotation.fromVisitedInstruction(this));
							fi.setSynchronized();
						}
					}
				}
			}
		}
	}
    
    /**
     * removes collection fields that are passed to other methods as arguments
     */
    private void removeCollectionParameters() {
        int parmCount = Type.getArgumentTypes(getSigConstantOperand()).length;
        if (stack.getStackDepth() >= parmCount) {
            for (int i = 0; i < parmCount; i++) {
                OpcodeStack.Item item = stack.getStackItem(i);
                XField field = item.getXField();
                if (field != null) {
                    collectionFields.remove(field.getName());
                }
            }
        }
    }
	
	/**
	 * holds information about a field, namely the annotation and 
	 * whether the collection is synchronized.
	 */
	static class FieldInfo {
		private FieldAnnotation fieldAnnotation;
		private boolean isSynchronized;
		
		public FieldInfo(FieldAnnotation fa) {
			fieldAnnotation = fa;
			isSynchronized = false;
		}
		
		public void setSynchronized() {
			isSynchronized = true;
		}
		
		public FieldAnnotation getFieldAnnotation() {
			return fieldAnnotation;
		}
		
		public boolean isSynchronized() {
			return isSynchronized;
		}
	}
}
