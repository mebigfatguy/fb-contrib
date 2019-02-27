/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2019 Dave Brosius
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.StopOpcodeParsingException;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.FieldAnnotation;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XFactory;
import edu.umd.cs.findbugs.ba.XField;
import edu.umd.cs.findbugs.ba.XMethod;
import edu.umd.cs.findbugs.classfile.MethodDescriptor;

/**
 * looks for classes that maintain collections or StringBuffer/StringBuilders in static member variables, and that do not appear to provide a way to clear or
 * remove items from these members. Such class fields are likely causes of memory bloat.
 *
 */
@CustomUserValue
public class PossibleMemoryBloat extends BytecodeScanningDetector {

    private static final Set<String> bloatableSigs = UnmodifiableSet.create("Ljava/util/concurrent/ArrayBlockingQueue;", "Ljava/util/ArrayList;",
            "Ljava/util/concurrent/BlockingQueue;", "Ljava/util/Collection;", "Ljava/util/concurrent/ConcurrentHashMap;",
            "Ljava/util/concurrent/ConcurrentSkipListMap;", "Ljava/util/concurrent/ConcurrentSkipListSet;", "Ljava/util/concurrent/CopyOnWriteArraySet;",
            "Ljava/util/EnumSet;", "Ljava/util/EnumMap;", "Ljava/util/HashMap;", "Ljava/util/HashSet;", "Ljava/util/Hashtable;", "Ljava/util/IdentityHashMap;",
            "Ljava/util/concurrent/LinkedBlockingQueue;", "Ljava/util/LinkedHashMap;", "Ljava/util/LinkedHashSet;", "Ljava/util/LinkedList;",
            "Ljava/util/List;", "Ljava/util/concurrent/PriorityBlockingQueue;", "Ljava/util/PriorityQueue;", "Ljava/util/Map;", "Ljava/util/Queue;",
            "Ljava/util/Set;", "Ljava/util/SortedSet;", "Ljava/util/SortedMap;", "Ljava/util/Stack;", "Ljava/lang/StringBuffer;", "Ljava/lang/StringBuilder;",
            "Ljava/util/TreeMap;", "Ljava/util/TreeSet;", "Ljava/util/Vector;");

    private static final Set<String> nonBloatableSigs = UnmodifiableSet.create("Ljava/util/WeakHashMap;");

    private static final Set<String> decreasingMethods = UnmodifiableSet.create("clear", "delete", "deleteCharAt", "drainTo", "poll", "pollFirst", "pollLast",
            "pop", "remove", "removeAll", "removeAllElements", "removeElementAt", "removeRange", "setLength", "take");

    private static final Set<String> increasingMethods = UnmodifiableSet.create("add", "addAll", "addElement", "addFirst", "addLast", "append",
            "insertElementAt", "offer", "put");

    private static final Set<String> mapSubsets = UnmodifiableSet.create("keySet", "entrySet", "values");
    
    private static final FQMethod jaxbNewInstance = new FQMethod("javax/xml/bind/JAXBContext", "newInstance", "([Ljava/lang/Class;)Ljavax/xml/bind/JAXBContext;");

    private final BugReporter bugReporter;
    private Map<XField, FieldAnnotation> bloatableCandidates;
    private Map<XField, FieldAnnotation> bloatableFields;
    private OpcodeStack stack;
    private String methodName;
    private Set<FieldAnnotation> threadLocalNonStaticFields;
    private Map<Integer, XField> userValues;
    private Map<Integer, Integer> jaxbContextRegs;

    /**
     * constructs a PMB detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public PossibleMemoryBloat(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * collects static fields that are likely bloatable objects and if found allows the visitor to proceed, at the end report all leftover fields
     *
     * @param classContext
     *            the class context object of the currently parsed java class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            bloatableCandidates = new HashMap<>();
            bloatableFields = new HashMap<>();
            threadLocalNonStaticFields = new HashSet<>();
            userValues = new HashMap<>();
            jaxbContextRegs = new HashMap<>();
            parseFields(classContext);

            stack = new OpcodeStack();
            super.visitClassContext(classContext);

            reportMemoryBloatBugs();
            reportThreadLocalBugs();

        } finally {
            stack = null;
            bloatableCandidates = null;
            bloatableFields = null;
            userValues = null;
            threadLocalNonStaticFields = null;
            jaxbContextRegs = null;
        }
    }

    private void reportThreadLocalBugs() {
        for (FieldAnnotation fieldAn : threadLocalNonStaticFields) {
            bugReporter.reportBug(new BugInstance(this, BugType.PMB_INSTANCE_BASED_THREAD_LOCAL.name(), NORMAL_PRIORITY).addClass(this).addField(fieldAn));
        }

    }

    private void reportMemoryBloatBugs() {
        for (Entry<XField, FieldAnnotation> entry : bloatableFields.entrySet()) {
            FieldAnnotation fieldAn = entry.getValue();
            if (fieldAn != null) {
                bugReporter.reportBug(new BugInstance(this, BugType.PMB_POSSIBLE_MEMORY_BLOAT.name(), NORMAL_PRIORITY).addClass(this).addField(fieldAn));
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
                    bloatableCandidates.put(XFactory.createXField(cls.getClassName(), f.getName(), f.getSignature(), f.isStatic()),
                            FieldAnnotation.fromBCELField(cls, f));
                }
            } else if ("Ljava/lang/ThreadLocal;".equals(sig)) {
                threadLocalNonStaticFields.add(FieldAnnotation.fromBCELField(cls, f));
            }
        }
    }

    /**
     * implements the visitor to collect the method name
     *
     * @param obj
     *            the context object of the currently parsed method
     */
    @Override
    public void visitMethod(Method obj) {
        methodName = obj.getName();
    }

    /**
     * implements the visitor to reset the opcode stack
     *
     * @param obj
     *            the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        userValues.clear();
        jaxbContextRegs.clear();

        if (Values.STATIC_INITIALIZER.equals(methodName) || Values.CONSTRUCTOR.equals(methodName)) {
            return;
        }

        super.visitCode(obj);
        
        for (Integer pc : jaxbContextRegs.values()) {
        	bugReporter.reportBug(new BugInstance(this, BugType.PMB_LOCAL_BASED_JAXB_CONTEXT.name(), "<clinit>".equals(getMethodName()) ? LOW_PRIORITY : NORMAL_PRIORITY)
        			.addClass(this).addMethod(this).addSourceLine(this, pc.intValue()));
        }
    }

    /**
     * implements the visitor to look for methods that empty a bloatable field if found, remove these fields from the current list
     *
     * @param seen
     *            the opcode of the currently parsed instruction
     */
    @Override
    public void sawOpcode(int seen) {
        XField userValue = null;
        try {
            stack.precomputation(this);

            if ((seen == Const.INVOKEVIRTUAL) || (seen == Const.INVOKEINTERFACE) || (seen == Const.INVOKEDYNAMIC)) {
                String sig = getSigConstantOperand();
                int argCount = SignatureUtils.getNumParameters(sig);
                if (stack.getStackDepth() > argCount) {
                    OpcodeStack.Item itm = stack.getStackItem(argCount);
                    XField field = itm.getXField();
                    if ((field != null) && bloatableCandidates.containsKey(field)) {
                        checkMethodAsDecreasingOrIncreasing(field);
                    }
                    String calledMethod = getNameConstantOperand();
                    if ("iterator".equals(calledMethod)) {
                        userValue = (XField) itm.getUserValue();
                        if (userValue == null) {
                            userValue = field;
                        }
                    } else {
                        if (field == null) {
                            field = (XField) itm.getUserValue();
                        }
                        if (field != null) {
                            if (mapSubsets.contains(calledMethod)) {
                                userValue = field;
                            } else if ("remove".equals(calledMethod) && "java/util/Iterator".equals(getClassConstantOperand())) {
                                bloatableCandidates.remove(field);
                                bloatableFields.remove(field);
                            }
                        }
                    }
                    
                    for (int i = 0; i < argCount; i++) {
                    	itm = stack.getStackItem(i);
                    	jaxbContextRegs.remove(itm.getRegisterNumber());
                    }
                }
            } else if (seen == PUTFIELD) {
                if (stack.getStackDepth() > 0) {
                	OpcodeStack.Item item = stack.getStackItem(0);
	                jaxbContextRegs.remove(item.getRegisterNumber());
                }
            } else if (seen == Const.PUTSTATIC) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    if (nonBloatableSigs.contains(item.getSignature())) {
                        XField field = item.getXField();
                        bloatableFields.remove(field);
                    }
                    
                    jaxbContextRegs.remove(item.getRegisterNumber());
                }
            }
            // Should not include private methods
            else if (seen == Const.ARETURN) {
                removeFieldsThatGetReturned();
            } else if (OpcodeUtils.isALoad(seen)) {
                userValue = userValues.get(RegisterUtils.getALoadReg(this, seen));
            } else if (OpcodeUtils.isAStore(seen)) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item itm = stack.getStackItem(0);
                    userValues.put(RegisterUtils.getAStoreReg(this, seen), (XField) itm.getUserValue());
                    
            		XMethod xm = itm.getReturnValueOf();
            		if (xm != null) {
            			MethodDescriptor md = xm.getMethodDescriptor();
            			FQMethod calledMethod = new FQMethod(xm.getClassName().replace('.', '/'), xm.getName(), xm.getSignature());
                    	if (jaxbNewInstance.equals(calledMethod)) {
                    		jaxbContextRegs.put(RegisterUtils.getAStoreReg(this, seen), getPC());
                    	}
            		}
                }
            }
        } finally {
            stack.sawOpcode(this, seen);
            if ((userValue != null) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item itm = stack.getStackItem(0);
                itm.setUserValue(userValue);
            }
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
            FieldAnnotation fieldAn = bloatableCandidates.get(field);
            if (fieldAn != null) {
                bloatableFields.put(field, fieldAn);
            }
        }
    }
}
