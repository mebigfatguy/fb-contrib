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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.FieldAnnotation;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XFactory;
import edu.umd.cs.findbugs.ba.XField;

/**
 * looks for classes that maintain collections or StringBuffer/StringBuilders in static member
 * variables, and that do not appear to provide a way to clear or remove items from these members.
 * Such class fields are likely causes of memory bloat.
 *
 */
public class PossibleMemoryBloat extends BytecodeScanningDetector
{
	private static final Set<String> bloatableSigs = new HashSet<String>();
	static {
        bloatableSigs.add("Ljava/util/concurrent/ArrayBlockingQueue;");
        bloatableSigs.add("Ljava/util/ArrayList;");
        bloatableSigs.add("Ljava/util/concurrent/BlockingQueue;");		
        bloatableSigs.add("Ljava/util/Collection;");
        bloatableSigs.add("Ljava/util/concurrent/ConcurrentHashMap;");
        bloatableSigs.add("Ljava/util/concurrent/ConcurrentSkipListMap;");
        bloatableSigs.add("Ljava/util/concurrent/ConcurrentSkipListSet;");
        bloatableSigs.add("Ljava/util/concurrent/CopyOnWriteArraySet;");
        bloatableSigs.add("Ljava/util/EnumSet;");
        bloatableSigs.add("Ljava/util/EnumMap;");
		bloatableSigs.add("Ljava/util/HashMap;");
		bloatableSigs.add("Ljava/util/HashSet;");
		bloatableSigs.add("Ljava/util/Hashtable;");
		bloatableSigs.add("Ljava/util/IdentityHashMap;");
        bloatableSigs.add("Ljava/util/concurrent/LinkedBlockingQueue;");
        bloatableSigs.add("Ljava/util/LinkedHashMap;");
        bloatableSigs.add("Ljava/util/LinkedHashSet;");
		bloatableSigs.add("Ljava/util/LinkedList;");
        bloatableSigs.add("Ljava/util/List;");
        bloatableSigs.add("Ljava/util/concurrent/PriorityBlockingQueue;");
        bloatableSigs.add("Ljava/util/PriorityQueue;");
		bloatableSigs.add("Ljava/util/Map;");
	    bloatableSigs.add("Ljava/util/Queue;");
		bloatableSigs.add("Ljava/util/Set;");
		bloatableSigs.add("Ljava/util/SortedSet;");
		bloatableSigs.add("Ljava/util/SortedMap;");
		bloatableSigs.add("Ljava/util/Stack;");
		bloatableSigs.add("Ljava/lang/StringBuffer;");
		bloatableSigs.add("Ljava/lang/StringBuilder;");
		bloatableSigs.add("Ljava/util/TreeMap;");
		bloatableSigs.add("Ljava/util/TreeSet;");
		bloatableSigs.add("Ljava/util/Vector;");
	}
	private static final Set<String> decreasingMethods = new HashSet<String>();
	static {
		decreasingMethods.add("clear");
		decreasingMethods.add("delete");
        decreasingMethods.add("deleteCharAt");
        decreasingMethods.add("drainTo");
        decreasingMethods.add("poll");
        decreasingMethods.add("pollFirst");
        decreasingMethods.add("pollLast");
        decreasingMethods.add("pop");
		decreasingMethods.add("remove");
		decreasingMethods.add("removeAll");
		decreasingMethods.add("removeAllElements");
		decreasingMethods.add("removeElementAt");
		decreasingMethods.add("removeRange");
        decreasingMethods.add("setLength");
        decreasingMethods.add("take");
	}
	
	private static final Set<String> increasingMethods = new HashSet<String>();
	static {
		increasingMethods.add("add");
		increasingMethods.add("addAll");
		increasingMethods.add("addElement");
		increasingMethods.add("addFirst");
		increasingMethods.add("addLast");
		increasingMethods.add("append");
		increasingMethods.add("insertElementAt");
        increasingMethods.add("offer");
        increasingMethods.add("put");
	}
	private final BugReporter bugReporter;
	private Map<XField, FieldAnnotation> bloatableCandidates;
	private Map<XField, FieldAnnotation> bloatableFields;
	private OpcodeStack stack;
	private String methodName;
	private Set<FieldAnnotation> threadLocalNonStaticFields;

	/**
     * constructs a PMB detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
	 */
	public PossibleMemoryBloat(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}
	
	/**
	 * collects static fields that are likely bloatable objects and if found
	 * allows the visitor to proceed, at the end report all leftover fields
	 * 
	 * @param classContext the class context object of the currently parsed java class
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			bloatableCandidates = new HashMap<XField, FieldAnnotation>();
			bloatableFields = new HashMap<XField, FieldAnnotation>();
			threadLocalNonStaticFields = new HashSet<FieldAnnotation>();
			parseFields(classContext);

			if (bloatableCandidates.size() > 0) {
				stack = new OpcodeStack();
				super.visitClassContext(classContext);

				reportMemoryBloatBugs();
				reportThreadLocalBugs();
			}
		} finally {
			stack = null;
			bloatableCandidates = null;
			bloatableFields = null;
			threadLocalNonStaticFields = null;
		}
	}

	private void reportThreadLocalBugs() {
		for(FieldAnnotation fieldAn: threadLocalNonStaticFields) {
			bugReporter.reportBug(new BugInstance(this, "PMB_INSTANCE_BASED_THREAD_LOCAL", NORMAL_PRIORITY)
			.addClass(this)
			.addField(fieldAn));
		}
		
	}

	private void reportMemoryBloatBugs() {
		for (Entry<XField, FieldAnnotation> entry : bloatableFields.entrySet()) {
			FieldAnnotation fieldAn = entry.getValue();
			if (fieldAn != null) {
				bugReporter.reportBug(new BugInstance(this, "PMB_POSSIBLE_MEMORY_BLOAT", NORMAL_PRIORITY)
				.addClass(this)
				.addField(fieldAn));
			}
		}
	}

	private void parseFields(ClassContext classContext) {
		JavaClass cls = classContext.getJavaClass();
		Field[] fields = cls.getFields();
		for (Field f : fields) {
			String sig = f.getSignature();
			if (f.isStatic()) {
				if (bloatableSigs.contains(sig)) {
					bloatableCandidates.put(XFactory.createXField(cls.getClassName(), f.getName(), f.getSignature(), f.isStatic()), FieldAnnotation.fromBCELField(cls, f));
				}
			} else if ("Ljava/lang/ThreadLocal;".equals(sig)) {
				threadLocalNonStaticFields.add(FieldAnnotation.fromBCELField(cls, f));
			} 
		}
	}

	/**
	 * implements the visitor to collect the method name
	 * 
	 * @param obj the context object of the currently parsed method
	 */
	@Override
	public void visitMethod(Method obj) {
		methodName = obj.getName();
	}
	
	/**
	 * implements the visitor to reset the opcode stack
	 * 
	 * @param obj the context object of the currently parsed code block
	 */
	@Override
	public void visitCode(Code obj) {
		stack.resetForMethodEntry(this);

		if ("<clinit>".equals(methodName) || "<init>".equals(methodName))
			return;

		if (bloatableCandidates.size() > 0)
			super.visitCode(obj);
	}
	
	/**
	 * implements the visitor to look for methods that empty a bloatable field
	 * if found, remove these fields from the current list
	 * 
	 * @param seen the opcode of the currently parsed instruction
	 */
	@Override
	public void sawOpcode(int seen) {
		try {
			if (bloatableCandidates.isEmpty())
				return;

			stack.precomputation(this);

			if ((seen == INVOKEVIRTUAL) || (seen == INVOKEINTERFACE)) {
				String sig = getSigConstantOperand();
				int argCount = Type.getArgumentTypes(sig).length;
				if (stack.getStackDepth() > argCount) {
					OpcodeStack.Item itm = stack.getStackItem(argCount);
					XField field = itm.getXField();
					if (field != null) {
						if (bloatableCandidates.containsKey(field)) {
							checkMethodAsDecreasingOrIncreasing(field);
						}
					}
				}
			}
			//Should not include private methods
			else if (seen == ARETURN) {
				removeFieldsThatGetReturned();
			}
		}
		finally {
			stack.sawOpcode(this, seen);
		}
	}

	protected void removeFieldsThatGetReturned() {
		if (stack.getStackDepth() > 0) {
			OpcodeStack.Item returnItem = stack.getStackItem(0);
			XField field = returnItem.getXField();
			if (field != null) {
				bloatableCandidates.remove(field);
				bloatableFields.remove(field);
			}
		}
	}

	protected void checkMethodAsDecreasingOrIncreasing(XField field) {
		String mName = getNameConstantOperand();
		if (decreasingMethods.contains(mName)) {
			bloatableCandidates.remove(field);
			bloatableFields.remove(field);
		} else if (increasingMethods.contains(mName)) {
			if (bloatableCandidates.containsKey(field)) {
				bloatableFields.put(field, bloatableCandidates.get(field));
			}
		}
	}
}
