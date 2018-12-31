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
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQField;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.SourceLineAnnotation;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XField;

/**
 * looks for collections or arrays that hold objects that are unrelated thru class or interface inheritance other than java.lang.Object. Doing so, makes for
 * brittle code, relying either on positional correspondence for type, or a reliance on instanceof to determine type. A better design usually can be had by
 * creating a separate class, which defines the different types required, and add an instance of that class to the collection, or array.
 */
public class UnrelatedCollectionContents extends BytecodeScanningDetector {
    private static final Set<String> COLLECTION_CLASSES = UnmodifiableSet.create(Values.SLASHED_JAVA_UTIL_COLLECTION, Values.SLASHED_JAVA_UTIL_LIST,
            Values.SLASHED_JAVA_UTIL_MAP, Values.SLASHED_JAVA_UTIL_SET, "java/util/SortedMap", "java/util/SortedSet");

    private final BugReporter bugReporter;
    private OpcodeStack stack;
    private Map<FQField, Set<String>> memberCollections;
    private Map<Integer, Set<String>> localCollections;
    private Map<Integer, BitSet> localScopeEnds;
    private Map<String, Set<SourceLineAnnotation>> memberSourceLineAnnotations;
    private Map<Integer, Set<SourceLineAnnotation>> localSourceLineAnnotations;

    /**
     * constructs a UCC detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public UnrelatedCollectionContents(final BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to create and destroy the stack and member collections
     *
     * @param classContext
     *            the context object for the currently parsed class
     */
    @Override
    public void visitClassContext(final ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            memberCollections = new HashMap<>();
            memberSourceLineAnnotations = new HashMap<>();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            memberCollections = null;
            memberSourceLineAnnotations = null;
        }
    }

    /**
     * implements the visitor to reset the opcode stack, and clear the various collections
     *
     * @param obj
     *            the currently parsed code block
     */
    @Override
    public void visitCode(final Code obj) {
        try {
            localCollections = new HashMap<>();
            localScopeEnds = new HashMap<>();
            localSourceLineAnnotations = new HashMap<>();
            stack.resetForMethodEntry(this);
            super.visitCode(obj);
        } finally {
            localCollections = null;
            localScopeEnds = null;
            localSourceLineAnnotations = null;
        }
    }

    /**
     * implements the visitor to look for collection method calls that put objects into the collection that are unrelated by anything besides java.lang.Objecct
     *
     * @param seen
     *            the currently parsed opcode
     */
    @Override
    public void sawOpcode(final int seen) {
        try {
            stack.precomputation(this);

            BitSet regs = localScopeEnds.remove(Integer.valueOf(getPC()));
            if (regs != null) {
                int i = regs.nextSetBit(0);
                while (i >= 0) {
                    localCollections.remove(i);
                    localSourceLineAnnotations.remove(i);
                    i = regs.nextSetBit(i + 1);
                }
            }
            if (seen == Const.INVOKEINTERFACE) {
                String className = getClassConstantOperand();
                if (COLLECTION_CLASSES.contains(className)) {
                    String methodName = getNameConstantOperand();
                    String methodSig = getSigConstantOperand();
                    if ("add".equals(methodName) && SignatureBuilder.SIG_OBJECT_TO_BOOLEAN.equals(methodSig)) {
                        if (stack.getStackDepth() > 1) {
                            OpcodeStack.Item colItm = stack.getStackItem(1);
                            OpcodeStack.Item addItm = stack.getStackItem(0);
                            checkAdd(colItm, addItm);
                        }
                    } else if (("put".equals(methodName) && SignatureBuilder.SIG_TWO_OBJECTS_TO_OBJECT.equals(methodSig)) && (stack.getStackDepth() > 2)) {
                        // For maps, just check the keys
                        OpcodeStack.Item colItm = stack.getStackItem(2);
                        OpcodeStack.Item addItm = stack.getStackItem(1);
                        checkAdd(colItm, addItm);
                    }
                }
            } else if (seen == Const.AASTORE) {
                if (stack.getStackDepth() > 2) {
                    OpcodeStack.Item arrayItm = stack.getStackItem(2);
                    OpcodeStack.Item addItm = stack.getStackItem(0);
                    checkAdd(arrayItm, addItm);
                }
            } else if (OpcodeUtils.isAStore(seen)) {
                Integer reg = Integer.valueOf(RegisterUtils.getAStoreReg(this, seen));
                localCollections.remove(reg);
                localSourceLineAnnotations.remove(reg);
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    /**
     * processes an add into a collection, by processing all the super classes/interfaces of an object and removing the possible set of parent classes that have
     * been seen so far, by doing what amounts to a intersection of what has been seen before, and this occurance.
     *
     * @param colItm
     *            the collection that is being added to
     * @param addItm
     *            the added item
     * @throws ClassNotFoundException
     *             if a super class is not found
     */
    private void checkAdd(final OpcodeStack.Item colItm, final OpcodeStack.Item addItm) throws ClassNotFoundException {
        int reg = colItm.getRegisterNumber();
        if (reg == -1) {
            XField field = colItm.getXField();
            if (field == null) {
                return;
            }

            Set<SourceLineAnnotation> sla = memberSourceLineAnnotations.get(field.getName());
            if (sla == null) {
                sla = new HashSet<>();
                memberSourceLineAnnotations.put(field.getName(), sla);
            }
            sla.add(SourceLineAnnotation.fromVisitedInstruction(this));
            FQField fqField = new FQField(field.getClassName(), field.getName(), field.getSignature());
            Set<String> commonSupers = memberCollections.get(fqField);
            if (commonSupers == null) {
                commonSupers = new HashSet<>();
                memberCollections.put(fqField, commonSupers);
                addNewItem(commonSupers, addItm);
            } else {
                mergeItem(commonSupers, sla, addItm);
            }
        } else {
            Integer regNumber = Integer.valueOf(reg);
            Set<SourceLineAnnotation> pcs = localSourceLineAnnotations.get(regNumber);
            if (pcs == null) {
                pcs = new HashSet<>();
                localSourceLineAnnotations.put(regNumber, pcs);
            }
            pcs.add(SourceLineAnnotation.fromVisitedInstruction(this, getPC()));
            Set<String> commonSupers = localCollections.get(regNumber);
            if (commonSupers == null) {
                commonSupers = new HashSet<>();
                localCollections.put(Integer.valueOf(reg), commonSupers);
                addNewItem(commonSupers, addItm);
                Integer scopeEnd = Integer.valueOf(RegisterUtils.getLocalVariableEndRange(getMethod().getLocalVariableTable(), reg, getNextPC()));
                BitSet regs = localScopeEnds.get(scopeEnd);
                if (regs == null) {
                    regs = new BitSet();
                    localScopeEnds.put(scopeEnd, regs);
                }
                regs.set(regNumber);
            } else {
                mergeItem(commonSupers, pcs, addItm);
            }
        }
    }

    /**
     * intersects the set of possible superclass that this collection might have seen before with this one. If we find that there is no commonality between
     * superclasses, report it as a bug.
     *
     * @param supers
     *            the collection of possible superclass/interfaces that has been seen for this collection thus far
     * @param sla
     *            the location of this add
     * @param addItm
     *            the currently added item
     * @throws ClassNotFoundException
     *             if a superclass/interface can not be found
     */
    private void mergeItem(final Set<String> supers, final Set<SourceLineAnnotation> sla, final OpcodeStack.Item addItm) throws ClassNotFoundException {

        if (supers.isEmpty()) {
            return;
        }

        Set<String> s = new HashSet<>();
        addNewItem(s, addItm);

        if (s.isEmpty()) {
            return;
        }

        intersection(supers, s);

        if (supers.isEmpty()) {
            BugInstance bug = new BugInstance(this, BugType.UCC_UNRELATED_COLLECTION_CONTENTS.name(), NORMAL_PRIORITY).addClass(this);

            if (addItm.getRegisterNumber() != -1) {
                bug.addMethod(this);
            }

            for (SourceLineAnnotation a : sla) {
                bug.addSourceLine(a);
            }

            bugReporter.reportBug(bug);
        }
    }

    /**
     * adds this item's type and all of it's superclass/interfaces to the set of possible types that could define this added item
     *
     * @param supers
     *            the current set of superclass items
     * @param addItm
     *            the item we are adding
     * @throws ClassNotFoundException
     *             if a superclass/interface is not found
     */
    private static void addNewItem(final Set<String> supers, final OpcodeStack.Item addItm) throws ClassNotFoundException {

        String itemSignature = addItm.getSignature();
        if (itemSignature.length() == 0) {
            return;
        }

        if (itemSignature.startsWith(Values.SIG_ARRAY_PREFIX)) {
            supers.add(itemSignature);
            return;
        }

        JavaClass cls = addItm.getJavaClass();
        if ((cls == null) || Values.DOTTED_JAVA_LANG_OBJECT.equals(cls.getClassName())) {
            return;
        }

        supers.add(cls.getClassName());

        JavaClass[] infs = cls.getAllInterfaces();
        for (JavaClass inf : infs) {
            String infName = inf.getClassName();
            if (!"java.io.Serializable".equals(infName) && !"java.lang.Cloneable".equals(infName)) {
                supers.add(infName);
            }
        }

        JavaClass[] sups = cls.getSuperClasses();
        for (JavaClass sup : sups) {
            String name = sup.getClassName();
            if (!Values.DOTTED_JAVA_LANG_OBJECT.equals(name)) {
                supers.add(name);
            }
        }
    }

    /**
     * performs a typical set intersection between what types of possible superclasses/interfaces has been seen before, for this collection, and what is now
     * seen.
     *
     * @param orig
     *            the existing set of superclasses used for this collection
     * @param add
     *            the currently added item
     */
    private static void intersection(final Set<String> orig, final Set<String> add) {
        Iterator<String> it = orig.iterator();
        while (it.hasNext()) {
            if (!add.contains(it.next())) {
                it.remove();
            }
        }
    }
}
