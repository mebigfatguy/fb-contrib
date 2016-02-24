/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2016 Dave Brosius
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
import java.util.Map;

import org.apache.bcel.Constants;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for classes that break the fundamental rule of equivalence, which is symmetry. If a equals b, then b equals a. While it is usually wrong to allow
 * equals to compare different types, at the very least you should make sure that each class knows about each other and is able to compare themselves with each
 * other.
 */
public class NonSymmetricEquals extends BytecodeScanningDetector {

    private BugReporter bugReporter;
    private OpcodeStack stack;
    private Map<String, Map<String, BugInstance>> possibleBugs = new HashMap<String, Map<String, BugInstance>>();

    /**
     * constructs a NSE detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public NonSymmetricEquals(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to create the stack object
     *
     * @param classContext
     *            the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
        }
    }

    /**
     * implements the visitor to see if this method is equals(Object o)
     *
     * @param obj
     *            the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        Method m = getMethod();
        String name = m.getName();
        String signature = m.getSignature();

        if ("equals".equals(name) && "(Ljava/lang/Object;)Z".equals(signature) && prescreen(m)) {
            stack.resetForMethodEntry(this);
            super.visitCode(obj);
        }
    }

    /**
     * looks for methods that contain a checkcast instruction
     *
     * @param method
     *            the context object of the current method
     * @return if the class does checkcast instructions
     */
    private boolean prescreen(Method method) {
        BitSet bytecodeSet = getClassContext().getBytecodeSet(method);
        return (bytecodeSet != null) && (bytecodeSet.get(Constants.CHECKCAST));
    }

    /**
     * implements the visitor to look for checkcasts of the parameter to other types, and enter instances in a map for further processing in doReport.
     *
     * @param seen
     *            the opcode of the currently parsed instruction
     */
    @Override
    public void sawOpcode(int seen) {
        try {
            stack.precomputation(this);

            if ((seen == CHECKCAST) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item item = stack.getStackItem(0);
                if (item.getRegisterNumber() == 1) {
                    String thisCls = getClassName();
                    String equalsCls = getClassConstantOperand();
                    if (!thisCls.equals(equalsCls)) {
                        JavaClass thisJavaClass = getClassContext().getJavaClass();
                        JavaClass equalsJavaClass = Repository.lookupClass(equalsCls);
                        boolean inheritance = thisJavaClass.instanceOf(equalsJavaClass) || equalsJavaClass.instanceOf(thisJavaClass);

                        BugInstance bug = new BugInstance(this, BugType.NSE_NON_SYMMETRIC_EQUALS.name(), inheritance ? LOW_PRIORITY : NORMAL_PRIORITY)
                                .addClass(this).addMethod(this).addSourceLine(this).addString(equalsCls);
                        Map<String, BugInstance> bugs = possibleBugs.get(thisCls);
                        if (bugs == null) {
                            bugs = new HashMap<String, BugInstance>();
                            possibleBugs.put(thisCls, bugs);
                        }
                        bugs.put(equalsCls, bug);
                    }
                }
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    /**
     * reports all the collected issues from the parse of this class
     */
    @Override
    public void report() {
        for (Map.Entry<String, Map<String, BugInstance>> thisEntry : possibleBugs.entrySet()) {

            Map<String, BugInstance> equalsClassesMap = thisEntry.getValue();
            for (Map.Entry<String, BugInstance> equalsEntry : equalsClassesMap.entrySet()) {
                String equalsCls = equalsEntry.getKey();

                Map<String, BugInstance> reverseEqualsClassMap = possibleBugs.get(equalsCls);
                if (reverseEqualsClassMap == null) {
                    bugReporter.reportBug(equalsClassesMap.values().iterator().next());
                    break;
                }

                if (!reverseEqualsClassMap.containsKey(thisEntry.getKey())) {
                    bugReporter.reportBug(equalsClassesMap.values().iterator().next());
                    break;
                }
            }
        }
        possibleBugs.clear();
    }
}
