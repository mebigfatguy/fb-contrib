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

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.LocalVariable;
import org.apache.bcel.classfile.LocalVariableTable;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;

/**
 * looks for non derivable method that declare parameters and then cast those parameters to more specific types in the method. This is misleading and dangerous
 * as you are not documenting through parameter types what is necessary for these parameters to function correctly.
 */
public class PoorlyDefinedParameter extends BytecodeScanningDetector {
    enum State {
        SAW_NOTHING, SAW_LOAD, SAW_CHECKCAST
    }

    BugReporter bugReporter;
    Map<Integer, String> parmSigs;
    Map<Integer, BugInfo> bugs;
    int loadedReg;
    String parmSig;
    State state;
    String castClass;
    int downwardBranchTarget;

    /**
     * constructs a PDP detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public PoorlyDefinedParameter(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to see if the method has parameters
     *
     * @param obj
     *            the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        try {
            Method m = getMethod();
            if (m.isSynthetic()) {
                return;
            }

            if (m.isStatic() || m.isPrivate() || Values.CONSTRUCTOR.equals(m.getName())) {
                parmSigs = SignatureUtils.getParameterSlotAndSignatures(m.isStatic(), m.getSignature());
                if (!parmSigs.isEmpty() && prescreen(m)) {
                    state = State.SAW_NOTHING;
                    bugs = new HashMap<>();
                    downwardBranchTarget = -1;
                    super.visitCode(obj);
                    for (BugInfo bi : bugs.values()) {
                        bugReporter.reportBug(bi.bug);
                    }
                }
            }
        } finally {
            bugs = null;
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
     * implements the visitor to look for check casts of parameters to more specific types
     */
    @Override
    public void sawOpcode(int seen) {
        if (downwardBranchTarget == -1) {
            switch (state) {
                case SAW_NOTHING:
                    sawOpcodeAfterNothing(seen);
                break;

                case SAW_LOAD:
                    sawOpcodeAfterLoad(seen);
                break;

                case SAW_CHECKCAST:
                    sawOpcodeAfterCheckCast(seen);
                break;
            }

            int insTarget = -1;
            if (((seen >= IFEQ) && (seen <= IF_ACMPNE)) || (seen == GOTO) || (seen == GOTO_W)) {
                insTarget = getBranchTarget();
                if (insTarget < getPC()) {
                    insTarget = -1;
                }
            } else if ((seen == LOOKUPSWITCH) || (seen == TABLESWITCH)) {
                insTarget = this.getDefaultSwitchOffset() + getPC();
            }

            if (insTarget > downwardBranchTarget) {
                downwardBranchTarget = insTarget;
            }
        } else {
            state = State.SAW_NOTHING;
            if (getPC() >= downwardBranchTarget) {
                downwardBranchTarget = -1;
            }
        }
    }

    private void sawOpcodeAfterNothing(int seen) {
        if (OpcodeUtils.isALoad(seen)) {
            loadedReg = RegisterUtils.getALoadReg(this, seen);
            parmSig = parmSigs.get(Integer.valueOf(loadedReg));
            if (parmSig != null) {
                state = State.SAW_LOAD;
            }
        }
    }

    private void sawOpcodeAfterLoad(int seen) {
        if (seen == CHECKCAST) {
            castClass = SignatureUtils.classToSignature(getClassConstantOperand());
            if (!castClass.equals(parmSig)) {
                state = State.SAW_CHECKCAST;
                return;
            }
        } else if (seen == INSTANCEOF) {
            // probably an if guard... assume the code is reasonable
            parmSigs.remove(Integer.valueOf(loadedReg));
        }
        state = State.SAW_NOTHING;
    }

    private void sawOpcodeAfterCheckCast(int seen) {
        state = State.SAW_NOTHING;
        if (!((seen == PUTFIELD) || OpcodeUtils.isAStore(seen))) {
            return;
        }
        String parmName = null;
        LocalVariableTable lvt = getMethod().getLocalVariableTable();
        if (lvt != null) {
            LocalVariable lv = lvt.getLocalVariable(loadedReg, 1);
            if (lv != null) {
                parmName = lv.getName();
            }
        }
        if (parmName == null) {
            parmName = "(" + loadedReg + ')';
        }

        BugInstance bug = new BugInstance(this, BugType.PDP_POORLY_DEFINED_PARAMETER.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this)
                .addString(parmName);
        Integer lr = Integer.valueOf(loadedReg);
        BugInfo bi = bugs.get(lr);
        if (bi == null) {
            bugs.put(lr, new BugInfo(castClass, bug));
        } else {
            // If there are casts to multiple different types, don't
            // report it altho suspect
            if (!bi.castClass.equals(castClass)) {
                bugs.remove(lr);
            }
        }
    }

    private static class BugInfo {
        String castClass;
        BugInstance bug;

        BugInfo(String cast, BugInstance bi) {
            castClass = cast;
            bug = bi;
        }
    }
}
