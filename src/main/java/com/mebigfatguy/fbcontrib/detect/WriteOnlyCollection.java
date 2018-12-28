/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2018 Dave Brosius
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
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
import java.util.NavigableMap;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingDeque;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.internalAnnotations.DottedClassName;

/**
 * Looks for allocations and initializations of java collections, but that are never read from or accessed to gain information. This represents a collection of
 * no use, and most probably can be removed. It is similar to a dead local store.
 */
@CustomUserValue
@SuppressWarnings({ "PMD.ReplaceHashtableWithMap", "PMD.ReplaceVectorWithList" })
public class WriteOnlyCollection extends MissingMethodsDetector {

    private static final Set<String> collectionClasses;

    private static final Set<FQMethod> collectionFactoryMethods;

    private static final Set<String> nonInformationalMethods;

    private int firstLocalRegister;

    static {
        Set<String> cc = new HashSet<>(35);
        cc.add(Set.class.getName());
        cc.add(Map.class.getName());
        cc.add(List.class.getName());
        cc.add(SortedSet.class.getName());
        cc.add(SortedMap.class.getName());
        cc.add(Collection.class.getName());
        cc.add(EnumSet.class.getName());
        cc.add(EnumMap.class.getName());
        cc.add(HashSet.class.getName());
        cc.add(IdentityHashMap.class.getName());
        cc.add(TreeSet.class.getName());
        cc.add(LinkedHashSet.class.getName());
        cc.add(HashMap.class.getName());
        cc.add(TreeMap.class.getName());
        cc.add(Hashtable.class.getName());
        cc.add(LinkedHashMap.class.getName());
        cc.add(Vector.class.getName());
        cc.add(ArrayList.class.getName());
        cc.add(LinkedList.class.getName());
        cc.add(Deque.class.getName());
        cc.add(Queue.class.getName());
        cc.add(ArrayDeque.class.getName());
        cc.add(LinkedBlockingDeque.class.getName());
        cc.add(NavigableMap.class.getName());
        cc.add(ConcurrentLinkedQueue.class.getName());
        cc.add(ConcurrentMap.class.getName());
        cc.add(ConcurrentNavigableMap.class.getName());
        cc.add(ConcurrentSkipListMap.class.getName());
        cc.add(ConcurrentHashMap.class.getName());
        cc.add(ConcurrentSkipListSet.class.getName());
        cc.add(CopyOnWriteArrayList.class.getName());
        collectionClasses = Collections.unmodifiableSet(cc);
    }

    static {
        Set<String> nim = new HashSet<>(20);
        nim.add("add");
        nim.add("addAll");
        nim.add("addElement");
        nim.add("addFirst");
        nim.add("addLast");
        nim.add("clear");
        nim.add("ensureCapacity");
        nim.add("insertElementAt");
        nim.add("push");
        nim.add("put");
        nim.add("putAll");
        nim.add("remove");
        nim.add("removeAll");
        nim.add("removeElement");
        nim.add("removeElementAt");
        nim.add("removeRange");
        nim.add("set");
        nim.add("setElementAt");
        nim.add("setSize");
        nim.add("trimToSize");
        nonInformationalMethods = Collections.unmodifiableSet(nim);
    }

    static {
        Set<FQMethod> cfm = new HashSet<>(25);
        cfm.add(new FQMethod("com/google/common/collect/Lists", "newArrayList", noParamsReturnType(ArrayList.class)));
        cfm.add(new FQMethod("com/google/common/collect/Lists", "newArrayListWithCapacity",
                new SignatureBuilder().withParamTypes(Values.SIG_PRIMITIVE_INT).withReturnType(ArrayList.class).toString()));
        cfm.add(new FQMethod("com/google/common/collect/Lists", "newArrayListWithExpectedSize",
                new SignatureBuilder().withParamTypes(Values.SIG_PRIMITIVE_INT).withReturnType(ArrayList.class).toString()));
        cfm.add(new FQMethod("com/google/common/collect/Lists", "newLinkedList", noParamsReturnType(LinkedList.class)));
        cfm.add(new FQMethod("com/google/common/collect/Lists", "newCopyOnWriteArrayList", noParamsReturnType(CopyOnWriteArrayList.class)));
        cfm.add(new FQMethod("com/google/common/collect/Sets", "newHashSet", noParamsReturnType(HashSet.class)));
        cfm.add(new FQMethod("com/google/common/collect/Sets", "newHashSetWithExpectedSize", noParamsReturnType(HashSet.class)));
        cfm.add(new FQMethod("com/google/common/collect/Sets", "newConcurrentHashSet", noParamsReturnType(Set.class)));
        cfm.add(new FQMethod("com/google/common/collect/Sets", "newLinkedHashSet", noParamsReturnType(LinkedHashSet.class)));
        cfm.add(new FQMethod("com/google/common/collect/Sets", "newLinkedHashSetWithExpectedSize",
                new SignatureBuilder().withParamTypes(Values.SIG_PRIMITIVE_INT).withReturnType(LinkedHashSet.class).toString()));
        cfm.add(new FQMethod("com/google/common/collect/Sets", "newTreeSet", noParamsReturnType(TreeSet.class)));
        cfm.add(new FQMethod("com/google/common/collect/Sets", "newTreeSet",
                new SignatureBuilder().withParamTypes(Values.SLASHED_JAVA_UTIL_COMPARATOR).withReturnType(TreeSet.class).toString()));
        cfm.add(new FQMethod("com/google/common/collect/Sets", "newIdentityHashSet", noParamsReturnType(Set.class)));
        cfm.add(new FQMethod("com/google/common/collect/Sets", "newCopyOnWriteArraySet", noParamsReturnType(CopyOnWriteArraySet.class)));
        cfm.add(new FQMethod("com/google/common/collect/Maps", "newHashMap", noParamsReturnType(HashMap.class)));
        cfm.add(new FQMethod("com/google/common/collect/Maps", "newHashMapWithExpectedSize",
                new SignatureBuilder().withParamTypes(Values.SIG_PRIMITIVE_INT).withReturnType(HashMap.class).toString()));
        cfm.add(new FQMethod("com/google/common/collect/Maps", "newLinkedHashMap", noParamsReturnType(LinkedHashMap.class)));
        cfm.add(new FQMethod("com/google/common/collect/Maps", "newConcurrentMap", noParamsReturnType(ConcurrentHashMap.class)));
        cfm.add(new FQMethod("com/google/common/collect/Maps", "newTreeMap", noParamsReturnType(TreeMap.class)));
        cfm.add(new FQMethod("com/google/common/collect/Maps", "newTreeMap",
                new SignatureBuilder().withParamTypes(Values.SLASHED_JAVA_UTIL_COMPARATOR).withReturnType(TreeMap.class).toString()));
        cfm.add(new FQMethod("com/google/common/collect/Maps", "newIdentityHashMap", noParamsReturnType(IdentityHashMap.class)));

        collectionFactoryMethods = Collections.<FQMethod> unmodifiableSet(cfm);
    }

    private static String noParamsReturnType(Class<?> type) {
        return new SignatureBuilder().withReturnType(type).toString();
    }

    /**
     * constructs a WOC detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public WriteOnlyCollection(BugReporter bugReporter) {
        super(bugReporter);
    }

    /**
     * overrides the visitor to see what how many register slots are taken by parameters.
     *
     * @param obj
     *            the currently parsed method
     */
    @Override
    public void visitMethod(Method obj) {
        firstLocalRegister = SignatureUtils.getFirstRegisterSlot(obj);
        super.visitMethod(obj);
    }

    /**
     * overrides the visitor to look for PUTFIELDS of collections
     *
     * @param seen
     *            the currently parsed opcode
     */
    @Override
    public void sawOpcode(int seen) {
        if (seen == Const.PUTFIELD) {
            OpcodeStack stack = getStack();
            if (stack.getStackDepth() > 0) {
                int reg = stack.getStackItem(0).getRegisterNumber();
                if ((reg >= 0) && (reg < firstLocalRegister)) {
                    clearSpecialField(getNameConstantOperand());
                }
            }
        }
        super.sawOpcode(seen);
    }

    /**
     * implements the MissingMethodsDetector to generate a Bug Instance when a bug is found around collections stored in fields
     *
     * @return the BugInstance
     */
    @Override
    protected BugInstance makeFieldBugInstance() {
        return new BugInstance(this, BugType.WOC_WRITE_ONLY_COLLECTION_FIELD.name(), NORMAL_PRIORITY);
    }

    /**
     * implements the MissingMethodsDetector to generate a Bug Instance when a bug is found around collections stored in a local variable
     *
     * @return the BugInstance
     */
    @Override
    protected BugInstance makeLocalBugInstance() {
        return new BugInstance(this, BugType.WOC_WRITE_ONLY_COLLECTION_LOCAL.name(), NORMAL_PRIORITY);
    }

    /**
     * implements the MissingMethodsDetector to determine whether this class type is a collection
     *
     * @param type
     *            the class type to check
     * @return whether this class is a collection
     */
    @Override
    protected boolean doesObjectNeedToBeWatched(@DottedClassName String type) {
        return collectionClasses.contains(type);
    }

    /**
     * implements the MissingMethodsDetector to determine whether this factory-like method returns a collection
     *
     * @param clsName
     *            the clsName the class name of the factory
     * @param methodName
     *            the method name of the factory
     * @param signature
     *            the signature of the factory method
     *
     * @return whether this class is a collection
     */
    @Override
    protected boolean doesStaticFactoryReturnNeedToBeWatched(String clsName, String methodName, String signature) {
        return collectionFactoryMethods.contains(new FQMethod(clsName, methodName, signature));
    }

    /**
     * determines if the method is returns information that could be used by the caller
     *
     * @param methodName
     *            collection method name
     * @return true if the caller could use the return value to learn something about the collection
     */
    @Override
    protected boolean isMethodThatShouldBeCalled(String methodName) {
        // If it's not a nonInformational method, i.e. something like get() or
        // size(), then we don't need to report
        // the collection
        return !nonInformationalMethods.contains(methodName);
    }
}
