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

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XField;

public class WriteOnlyCollection extends BytecodeScanningDetector {

	private static Set<String> collectionClasses = new HashSet<String>();
	static
	{
		collectionClasses.add(Set.class.getName());
		collectionClasses.add(Map.class.getName());
		collectionClasses.add(List.class.getName());
		collectionClasses.add(SortedSet.class.getName());
		collectionClasses.add(SortedMap.class.getName());
		collectionClasses.add(Collection.class.getName());
        collectionClasses.add(EnumSet.class.getName());
        collectionClasses.add(EnumMap.class.getName());
        collectionClasses.add(HashSet.class.getName());
        collectionClasses.add(IdentityHashMap.class.getName());
		collectionClasses.add(TreeSet.class.getName());
		collectionClasses.add(LinkedHashSet.class.getName());
		collectionClasses.add(HashMap.class.getName());
		collectionClasses.add(TreeMap.class.getName());
		collectionClasses.add(Hashtable.class.getName());
		collectionClasses.add(LinkedHashMap.class.getName());
		collectionClasses.add(Vector.class.getName());
		collectionClasses.add(ArrayList.class.getName());
		collectionClasses.add(LinkedList.class.getName());
		/* these are java 6 classes */
        collectionClasses.add("java.util.Deque");
        collectionClasses.add("java.util.Queue");
        collectionClasses.add("java.util.ArrayDeque");
        collectionClasses.add("java.util.LinkedBlockingDeque");
        collectionClasses.add("java.util.NavigableMap");
        collectionClasses.add("java.util.concurrent.ConcurrentMap");
        collectionClasses.add("java.util.concurrent.ConcurrentNavigableMap");
        collectionClasses.add("java.util.concurrent.ConcurrentSkipListMap");
        collectionClasses.add("java.util.concurrent.ConcurrentHashMap");
        collectionClasses.add("java.util.concurrent.ConcurrentSkipListSet");
        collectionClasses.add("java.util.concurrent.CopyOnWriteArrayList");
	}

	private static Set<String> nonInformationalMethods = new HashSet<String>();
	static
	{
		nonInformationalMethods.add("add");
		nonInformationalMethods.add("addAll");
		nonInformationalMethods.add("addElement");
		nonInformationalMethods.add("addFirst");
		nonInformationalMethods.add("addLast");
		nonInformationalMethods.add("clear");
		nonInformationalMethods.add("clone");
		nonInformationalMethods.add("ensureCapacity");
		nonInformationalMethods.add("insertElementAt");
		nonInformationalMethods.add("push");
		nonInformationalMethods.add("put");
		nonInformationalMethods.add("putAll");
		nonInformationalMethods.add("remove");
		nonInformationalMethods.add("removeAll");
		nonInformationalMethods.add("removeElement");
		nonInformationalMethods.add("removeElementAt");
		nonInformationalMethods.add("removeRange");
		nonInformationalMethods.add("set");
		nonInformationalMethods.add("setElementAt");
		nonInformationalMethods.add("setSize");
		nonInformationalMethods.add("trimToSize");
	}

	private final BugReporter bugReporter;
	private OpcodeStack stack;
	private String clsSignature;
	/** register to first allocation PC */
	private Map<Integer, Integer> localWOCollections;
	/** fieldname to field sig */
	private Map<String, String> fieldWOCollections;


	/**
     * constructs a WOC detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
	 */
	public WriteOnlyCollection(BugReporter bugReporter) {
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
			clsSignature = "L" + classContext.getJavaClass().getClassName().replaceAll("\\.", "/") + ";";
			stack = new OpcodeStack();
			localWOCollections = new HashMap<Integer, Integer>();
			fieldWOCollections = new HashMap<String, String>();
			super.visitClassContext(classContext);

			if (fieldWOCollections.size() > 0) {
				String clsName = classContext.getJavaClass().getClassName();
				for (Map.Entry<String, String> entry : fieldWOCollections.entrySet()) {
					String fieldName = entry.getKey();
					String signature = entry.getValue();
					bugReporter.reportBug(new BugInstance(this, "WOC_WRITE_ONLY_COLLECTION_FIELD", NORMAL_PRIORITY)
								.addClass(this)
								.addField(clsName, fieldName, signature, false));
				}
			}
		} finally {
			stack = null;
			localWOCollections = null;
			fieldWOCollections = null;
		}
	}

	@Override
	public void visitField(Field obj) {
		if (obj.isPrivate() && !obj.isSynthetic()) {
			String sig = obj.getSignature();
			if (sig.startsWith("L")) {
				String type = sig.substring(1, sig.length() - 1).replace('/', '.');
				if (collectionClasses.contains(type)) {
					fieldWOCollections.put(obj.getName(), obj.getSignature());
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
		localWOCollections.clear();
		super.visitCode(obj);

		for (Integer pc : localWOCollections.values()) {
			bugReporter.reportBug(new BugInstance(this, "WOC_WRITE_ONLY_COLLECTION_LOCAL", NORMAL_PRIORITY)
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

		try {
			switch (seen) {
				case INVOKESPECIAL:
					String methodName = getNameConstantOperand();
					if ("<init>".equals(methodName)) {
						String clsName = getClassConstantOperand().replace('/', '.');
						if (collectionClasses.contains(clsName))
                        {
                            userObject = Boolean.TRUE;
                        }
					}
					processMethodParms();
				break;

				case INVOKEINTERFACE:
				case INVOKEVIRTUAL: {
					String sig = getSigConstantOperand();
					int numParms = Type.getArgumentTypes(sig).length;
					if (stack.getStackDepth() > numParms) {
						OpcodeStack.Item item = stack.getStackItem(numParms);
						Object uo = item.getUserValue();
						if (uo != null) {
							String name = getNameConstantOperand();
							if (!nonInformationalMethods.contains(name)) {
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

				case ASTORE_0:
				case ASTORE_1:
				case ASTORE_2:
				case ASTORE_3:
				case ASTORE:
					if (stack.getStackDepth() > 0) {
						OpcodeStack.Item item = stack.getStackItem(0);
						Object uo = item.getUserValue();
						if (uo != null) {
							if (uo instanceof Boolean) {
								int reg = RegisterUtils.getAStoreReg(this, seen);
								localWOCollections.put(Integer.valueOf(reg), Integer.valueOf(getPC()));
							} else {
								clearUserValue(item);
							}
						}
					}
				break;

				case ALOAD_0:
				case ALOAD_1:
				case ALOAD_2:
				case ALOAD_3:
				case ALOAD:
					int reg = RegisterUtils.getALoadReg(this, seen);
					if (localWOCollections.containsKey(Integer.valueOf(reg))) {
						userObject = Integer.valueOf(reg);
					}
				break;

				case AASTORE:
				    if (stack.getStackDepth() >= 3) {
				        OpcodeStack.Item item = stack.getStackItem(0);
				        clearUserValue(item);
				    }
				break;

				case PUTFIELD:
					if (stack.getStackDepth() > 1) {
						OpcodeStack.Item item = stack.getStackItem(0);
						Object uo = item.getUserValue();
						if (!(uo instanceof Boolean)) {
							clearUserValue(item);
						}
					}
				break;

				case GETFIELD:
					if (stack.getStackDepth() > 0) {
						OpcodeStack.Item item = stack.getStackItem(0);
						String sig = item.getSignature();
						if ((item.getRegisterNumber() == 0) || ((sig != null) && sig.equals(clsSignature))) {
							XField field = getXFieldOperand();
							if (field != null) {
								String fieldName = field.getName();
								if (fieldWOCollections.containsKey(fieldName)) {
									userObject = fieldName;
								}
							}
						}
					}
				break;

				case PUTSTATIC:
					if (stack.getStackDepth() > 0) {
						OpcodeStack.Item item = stack.getStackItem(0);
						Object uo = item.getUserValue();
						if (!(uo instanceof Boolean)) {
							clearUserValue(item);
						}
					}
				break;

				case GETSTATIC:
					XField field = getXFieldOperand();
					if (field != null) {
						String fieldName = field.getName();
						if (fieldWOCollections.containsKey(fieldName)) {
							userObject = fieldName;
						}
					}
				break;

				case GOTO:
					if (stack.getStackDepth() > 0) {
						OpcodeStack.Item item = stack.getStackItem(0);
						Object uo = item.getUserValue();
						if (!(uo instanceof Boolean)) {
							clearUserValue(item);
						}
					}
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
		}
	}

	private boolean nextOpIsPop() {
		int nextPC = getNextPC();
		return getCode().getCode()[nextPC] == POP;
	}

	private void clearUserValue(OpcodeStack.Item item) {
		Object uo = item.getUserValue();
		if (uo instanceof Integer) {
			localWOCollections.remove(uo);
		} else if (uo instanceof String) {
			fieldWOCollections.remove(uo);
		}
		item.setUserValue(null);
	}

	private void processMethodParms() {
		String sig = getSigConstantOperand();
		int numParms = Type.getArgumentTypes(sig).length;
		if (stack.getStackDepth() >= numParms) {
			for (int i = 0; i < numParms; i++) {
				clearUserValue(stack.getStackItem(i));
			}
		}
	}
}


