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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XField;

/**
 * looks for classes that maintain two or more lists or arrays associated
 * one-for-one through the same index to hold two or more pieces of related
 * information. It would be better to create a new class that holds all of these
 * pieces of information, and place instances of this class in one list.
 */
public class ParallelLists extends BytecodeScanningDetector {
    private BugReporter bugReporter;
    private OpcodeStack stack;
    private Set<String> listFields;
    private Map<Integer, String> indexToFieldMap;

    /**
     * constructs a PL detector given the reporter to report bugs on
     * 
     * @param bugReporter
     *            the sync of bug reports
     */
    public ParallelLists(final BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(final ClassContext classContext) {
        try {
            JavaClass cls = classContext.getJavaClass();

            listFields = new HashSet<String>();
            Field[] flds = cls.getFields();
            for (Field f : flds) {
                String sig = f.getSignature();
                if (sig.charAt(0) == 'L') {
                    sig = sig.substring(1, sig.length() - 1);
                    if (sig.startsWith("java/util/") && sig.endsWith("List")) {
                        listFields.add(f.getName());
                    }
                } else if ((sig.charAt(0) == '[') && (sig.charAt(1) != '['))
                    listFields.add(f.getName());
            }

            if (listFields.size() > 0) {
                stack = new OpcodeStack();
                indexToFieldMap = new HashMap<Integer, String>();
                super.visitClassContext(classContext);
            }
        } finally {
            stack = null;
            indexToFieldMap = null;
        }
    }

    @Override
    public void visitCode(final Code obj) {
        stack.resetForMethodEntry(this);
        indexToFieldMap.clear();
        super.visitCode(obj);
    }

    @Override
    public void sawOpcode(final int seen) {
        try {
            stack.precomputation(this);

            if (seen == INVOKEINTERFACE) {
                String className = getClassConstantOperand();
                String methodName = getNameConstantOperand();
                String methodSig = getSigConstantOperand();

                if ("java/util/List".equals(className) && "get".equals(methodName) && "(I)Ljava/lang/Object;".equals(methodSig)) {
                    checkParms();
                }
            } else if ((seen >= IFEQ) && (seen <= RETURN)) {
                indexToFieldMap.clear();
            } else if ((seen == ISTORE) || (seen == IINC) || ((seen >= ISTORE_0) && (seen <= ISTORE_3))) {
                int reg = getIntOpRegister(seen);
                indexToFieldMap.remove(Integer.valueOf(reg));
            } else if ((seen >= IALOAD) && (seen <= SALOAD)) {
                checkParms();
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    private int getIntOpRegister(final int seen) {
        if ((seen == ISTORE) || (seen == IINC))
            return getRegisterOperand();
        return seen - ISTORE_0;
    }

    private void checkParms() {
        if (stack.getStackDepth() >= 2) {
            OpcodeStack.Item index = stack.getStackItem(0);
            OpcodeStack.Item list = stack.getStackItem(1);

            int indexReg = index.getRegisterNumber();
            XField field = list.getXField();

            if ((indexReg >= 0) && (field != null)) {
                if (listFields.contains(field.getName())) {
                    String f = indexToFieldMap.get(Integer.valueOf(indexReg));
                    if ((f != null) && (!f.equals(field.getName()))) {
                        bugReporter.reportBug(
                                new BugInstance(this, "PL_PARALLEL_LISTS", NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this, getPC()));
                        listFields.remove(field.getName());
                        indexToFieldMap.clear();
                    } else
                        indexToFieldMap.put(Integer.valueOf(indexReg), field.getName());
                }
            }
        }
    }
}
