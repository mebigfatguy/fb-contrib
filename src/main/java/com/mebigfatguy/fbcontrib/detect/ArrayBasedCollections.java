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

import java.util.ArrayList;
import java.util.List;

import org.apache.bcel.classfile.Code;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for methods that use arrays for items in the keyset of a map, or as an element of a set, or in a list when using the contains method. Since arrays do
 * not, and cannot define an equals method, reference equality is used for these collections, which is probably not desired.
 */
public class ArrayBasedCollections extends BytecodeScanningDetector {
    private BugReporter bugReporter;
    private OpcodeStack stack;
    private List<BugInstance> mapBugs;
    private List<BugInstance> setBugs;
    private boolean hasMapComparator;
    private boolean hasSetComparator;

    /**
     * constructs a ABC detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public ArrayBasedCollections(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implement the visitor to report bugs if no Tree comparators were found
     *
     * @param classContext
     *            the context object for the class currently being parsed
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            mapBugs = new ArrayList<>();
            setBugs = new ArrayList<>();
            hasMapComparator = false;
            hasSetComparator = false;
            super.visitClassContext(classContext);
            if (!hasMapComparator) {
                for (BugInstance bi : mapBugs) {
                    bugReporter.reportBug(bi);
                }
            }

            if (!hasSetComparator) {
                for (BugInstance bi : setBugs) {
                    bugReporter.reportBug(bi);
                }
            }
        } finally {
            stack = null;
            mapBugs = null;
            setBugs = null;
        }
    }

    /**
     * implements the visitor to reset the stack of opcodes
     *
     * @param obj
     *            the context object for the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        super.visitCode(obj);
    }

    /**
     * implements the visitor to find accesses to maps, sets and lists using arrays
     *
     * @param seen
     *            the currently visitor opcode
     */
    @Override
    public void sawOpcode(int seen) {
        try {
            stack.precomputation(this);

            if (seen == INVOKEINTERFACE) {
                processInvokeInterface();

            } else if (seen == INVOKESPECIAL) {
                processInvokeSpecial();
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    private void processInvokeInterface() {
        String className = getClassConstantOperand();
        String methodName = getNameConstantOperand();
        String methodSig = getSigConstantOperand();

        if (Values.SLASHED_JAVA_UTIL_MAP.equals(className) && "put".equals(methodName)
                && SignatureBuilder.SIG_TWO_OBJECTS_TO_OBJECT.equals(methodSig)) {
            if (stack.getStackDepth() > 1) {
                OpcodeStack.Item itm = stack.getStackItem(1);
                String pushedSig = itm.getSignature();
                if (pushedSig.startsWith(Values.SIG_ARRAY_PREFIX)) {
                    foundBugFor(mapBugs);
                }
            }
        } else if (Values.SLASHED_JAVA_UTIL_SET.equals(className) && "add".equals(methodName) && SignatureBuilder.SIG_OBJECT_TO_BOOLEAN.equals(methodSig)) {
            if (stack.getStackDepth() > 0) {
                OpcodeStack.Item itm = stack.getStackItem(0);
                String pushedSig = itm.getSignature();
                if (pushedSig.startsWith(Values.SIG_ARRAY_PREFIX)) {
                    foundBugFor(setBugs);
                }
            }
        } else if (Values.SLASHED_JAVA_UTIL_LIST.equals(className) && "contains".equals(methodName) && SignatureBuilder.SIG_OBJECT_TO_BOOLEAN.equals(methodSig)
                && (stack.getStackDepth() > 0)) {
            OpcodeStack.Item itm = stack.getStackItem(0);
            String pushedSig = itm.getSignature();
            if (pushedSig.startsWith(Values.SIG_ARRAY_PREFIX)) {
                foundBugFor(null);
            }
        }
    }

    private void foundBugFor(List<BugInstance> bugList) {
        BugInstance bi = new BugInstance(this, BugType.ABC_ARRAY_BASED_COLLECTIONS.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                .addSourceLine(this);
        if (bugList == null) {
            bugReporter.reportBug(bi);
        } else {
            bugList.add(bi);
        }
    }

    private void processInvokeSpecial() {
        String methodName = getNameConstantOperand();

        if (Values.CONSTRUCTOR.equals(methodName)) {
            String className = getClassConstantOperand();
            String sig = getSigConstantOperand();
            if (!hasMapComparator && "java/util/TreeMap".equals(className)) {
                List<String> parmSignatures = SignatureUtils.getParameterSignatures(sig);
                if (hasComparator(parmSignatures)) {
                    hasMapComparator = true;
                }
            } else if (!hasSetComparator && "java/util/TreeSet".equals(className)) {
                List<String> parmSignatures = SignatureUtils.getParameterSignatures(sig);
                if (hasComparator(parmSignatures)) {
                    hasSetComparator = true;
                }
            }
        }
    }

    private static boolean hasComparator(List<String> parmSignatures) {
        return (parmSignatures.size() == 1) && SignatureUtils.classToSignature(Values.SLASHED_JAVA_UTIL_COMPARATOR).equals(parmSignatures.get(0));
    }
}
