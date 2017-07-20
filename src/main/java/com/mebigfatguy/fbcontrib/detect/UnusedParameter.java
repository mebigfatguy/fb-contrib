/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2017 Dave Brosius
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.LocalVariable;
import org.apache.bcel.classfile.LocalVariableTable;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.StopOpcodeParsingException;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for private or static methods that have parameters that aren't used. These parameters can be removed.
 */
@CustomUserValue
public class UnusedParameter extends BytecodeScanningDetector {

    private static Set<String> IGNORE_METHODS = UnmodifiableSet.create(Values.CONSTRUCTOR, Values.STATIC_INITIALIZER, "main", "premain", "agentmain",
            "writeObject", "readObject", "readObjectNoData", "writeReplace", "readResolve", "writeExternal", "readExternal");

    private BugReporter bugReporter;

    private BitSet unusedParms;
    private Map<Integer, Integer> regToParm;
    private OpcodeStack stack;

    /**
     * constructs a UP detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public UnusedParameter(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to create parm bitset
     *
     * @param classContext
     *            the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            unusedParms = new BitSet();
            regToParm = new HashMap<>();
            stack = new OpcodeStack();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            regToParm = null;
            unusedParms.clear();
        }
    }

    /**
     * implements the visitor to clear the parm set, and check for potential methods
     *
     * @param obj
     *            the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        unusedParms.clear();
        regToParm.clear();
        stack.resetForMethodEntry(this);
        Method m = getMethod();
        String methodName = m.getName();
        if (IGNORE_METHODS.contains(methodName)) {
            return;
        }

        int accessFlags = m.getAccessFlags();
        if (((accessFlags & (Const.ACC_STATIC | Const.ACC_PRIVATE)) == 0) || ((accessFlags & Const.ACC_SYNTHETIC) != 0)) {
            return;
        }

        List<String> parmTypes = SignatureUtils.getParameterSignatures(m.getSignature());

        if (parmTypes.isEmpty()) {
            return;
        }
        int firstReg = 0;
        if ((accessFlags & Const.ACC_STATIC) == 0) {
            ++firstReg;
        }

        int reg = firstReg;
        for (int i = 0; i < parmTypes.size(); ++i) {
            unusedParms.set(reg);
            regToParm.put(Integer.valueOf(reg), Integer.valueOf(i + 1));
            String parmSig = parmTypes.get(i);
            reg += SignatureUtils.getSignatureSize(parmSig);
        }

        try {
            super.visitCode(obj);

            if (!unusedParms.isEmpty()) {
                LocalVariableTable lvt = m.getLocalVariableTable();

                reg = unusedParms.nextSetBit(firstReg);
                while (reg >= 0) {
                    LocalVariable lv = (lvt == null) ? null : lvt.getLocalVariable(reg, 0);
                    if (lv != null) {
                        String parmName = lv.getName();
                        bugReporter.reportBug(new BugInstance(this, BugType.UP_UNUSED_PARAMETER.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                                .addString("Parameter " + regToParm.get(Integer.valueOf(reg)) + ": " + parmName));
                    }
                    reg = unusedParms.nextSetBit(reg + 1);
                }
            }
        } catch (StopOpcodeParsingException e) {
            // no unusedParms left
        }
    }

    /**
     * implements the visitor to look for usage of parmeter registers.
     *
     * @param seen
     *            the opcode of the currently parsed instruction
     */
    @Override
    public void sawOpcode(int seen) {
        try {
            stack.precomputation(this);

            if (OpcodeUtils.isStore(seen) || OpcodeUtils.isLoad(seen)) {
                int reg = getRegisterOperand();
                unusedParms.clear(reg);
            } else if (OpcodeUtils.isReturn(seen) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item item = stack.getStackItem(0);
                int reg = item.getRegisterNumber();
                if (reg >= 0) {
                    unusedParms.clear(reg);
                }
            }
            if (unusedParms.isEmpty()) {
                throw new StopOpcodeParsingException();
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }
}
