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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;

import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XField;

/**
 * looks for classes that maintain two or more lists or arrays associated one-for-one through the same index to hold two or more pieces of related information.
 * It would be better to create a new class that holds all of these pieces of information, and place instances of this class in one list.
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

            listFields = new HashSet<>();
            Field[] flds = cls.getFields();
            for (Field f : flds) {
                String sig = f.getSignature();
                if (sig.startsWith(Values.SIG_QUALIFIED_CLASS_PREFIX)) {
                    sig = SignatureUtils.trimSignature(sig);
                    if (sig.startsWith("java/util/") && sig.endsWith("List")) {
                        listFields.add(f.getName());
                    }
                } else if (sig.startsWith(Values.SIG_ARRAY_PREFIX) && !sig.startsWith(Values.SIG_ARRAY_OF_ARRAYS_PREFIX)) {
                    listFields.add(f.getName());
                }
            }

            if (!listFields.isEmpty()) {
                stack = new OpcodeStack();
                indexToFieldMap = new HashMap<>();
                super.visitClassContext(classContext);
            }
        } finally {
            stack = null;
            indexToFieldMap = null;
        }
    }

    /**
     * implements the visitor to reset the opcode stack, and the file maps
     *
     * @param obj
     *            the currently parsed method code block
     */
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

            if (seen == Const.INVOKEINTERFACE) {
                String className = getClassConstantOperand();
                String methodName = getNameConstantOperand();
                String methodSig = getSigConstantOperand();

                if (Values.SLASHED_JAVA_UTIL_LIST.equals(className) && "get".equals(methodName) && SignatureBuilder.SIG_INT_TO_OBJECT.equals(methodSig)) {
                    checkParms();
                }
            } else if ((seen >= Const.IFEQ) && (seen <= Const.RETURN)) {
                indexToFieldMap.clear();
            } else if ((seen == Const.IINC) || OpcodeUtils.isIStore(seen)) {
                int reg = getIntOpRegister(seen);
                indexToFieldMap.remove(Integer.valueOf(reg));
            } else if ((seen >= Const.IALOAD) && (seen <= Const.SALOAD)) {
                checkParms();
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    /**
     * fetch the register from a integer op code
     *
     * @param seen
     *            the currently parsed opcode
     *
     * @return the register in use
     */
    private int getIntOpRegister(final int seen) {
        if ((seen == Const.ISTORE) || (seen == Const.IINC)) {
            return getRegisterOperand();
        }
        return seen - Const.ISTORE_0;
    }

    private void checkParms() {
        if (stack.getStackDepth() >= 2) {
            OpcodeStack.Item index = stack.getStackItem(0);
            OpcodeStack.Item list = stack.getStackItem(1);

            int indexReg = index.getRegisterNumber();
            XField field = list.getXField();

            if ((indexReg >= 0) && (field != null) && listFields.contains(field.getName())) {
                String f = indexToFieldMap.get(Integer.valueOf(indexReg));
                if ((f != null) && !f.equals(field.getName())) {
                    bugReporter
                            .reportBug(new BugInstance(this, "PL_PARALLEL_LISTS", NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this, getPC()));
                    listFields.remove(field.getName());
                    indexToFieldMap.clear();
                } else {
                    indexToFieldMap.put(Integer.valueOf(indexReg), field.getName());
                }
            }
        }
    }
}
