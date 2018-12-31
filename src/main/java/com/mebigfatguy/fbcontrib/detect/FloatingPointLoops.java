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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;

import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;

/**
 * looks for methods that use floating point indexes for loops. Since floating point math is inprecise, rounding errors will occur each time through the loop
 * causing hard to find problems. It is usually better to use integer indexing, and calculating the correct floating point value from the index.
 */
public class FloatingPointLoops extends BytecodeScanningDetector {
    enum State {
        SAW_LOAD, SAW_CMPX, SAW_IFX, SAW_STORE
    }

    BugReporter bugReporter;
    private Set<FloatForLoop> forLoops = new HashSet<>(5);

    /**
     * constructs a FPL detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public FloatingPointLoops(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to clear the forLoops set
     *
     * @param obj
     *            the context object for the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        forLoops = new HashSet<>();
        super.visitCode(obj);
        forLoops = null;
    }

    /**
     * implements the visitor to find for loops using floating point indexes
     *
     * @param seen
     *            the opcode of the currently parsed instruction
     */
    @Override
    public void sawOpcode(int seen) {
        if (!forLoops.isEmpty()) {
            Iterator<FloatForLoop> ffl = forLoops.iterator();
            while (ffl.hasNext()) {
                if (!ffl.next().sawOpcode(seen)) {
                    ffl.remove();
                }
            }
        }

        if (OpcodeUtils.isFLoad(seen) || OpcodeUtils.isDLoad(seen)) {
            forLoops.add(new FloatForLoop(RegisterUtils.getLoadReg(this, seen), getPC()));
        }
    }

    /**
     * maintains the state of a previously found for loop
     */
    public class FloatForLoop {
        private State state;
        private final int loopPC;
        private final int loopReg;
        private int gotoPC;

        public FloatForLoop(int reg, int pc) {
            loopReg = reg;
            loopPC = pc;
            state = State.SAW_LOAD;
            gotoPC = -1;
        }

        public boolean sawOpcode(final int seen) {
            switch (state) {
                case SAW_LOAD:
                    if ((seen == Const.FCMPG) || (seen == Const.FCMPL) || (seen == Const.DCMPG) || (seen == Const.DCMPL)) {
                        state = State.SAW_CMPX;
                        return true;
                    } else if (OpcodeUtils.isInvoke(seen)) {
                        String methodSig = FloatingPointLoops.this.getSigConstantOperand();
                        return !Values.SIG_VOID.equals(SignatureUtils.getReturnSignature(methodSig));
                    } else if ((seen < Const.ISTORE) || (seen > Const.SASTORE)) {
                        return true;
                    }
                break;

                case SAW_CMPX:
                    if ((seen >= Const.IFEQ) && (seen <= Const.IFLE)) {
                        state = State.SAW_IFX;
                        gotoPC = getBranchTarget() - 3;
                        return (gotoPC > getPC());
                    }
                break;

                case SAW_IFX:
                    if (getPC() < (gotoPC - 1)) {
                        return true;
                    }

                    if (getPC() > (gotoPC - 1)) {
                        return false;
                    }

                    if (!OpcodeUtils.isFStore(seen) && !OpcodeUtils.isDStore(seen)) {
                        return false;
                    }

                    int storeReg = RegisterUtils.getStoreReg(FloatingPointLoops.this, seen);

                    state = State.SAW_STORE;
                    return storeReg == loopReg;

                case SAW_STORE:
                    if (((seen == Const.GOTO) || (seen == Const.GOTO_W)) && (getBranchTarget() == loopPC)) {
                        bugReporter.reportBug(new BugInstance(FloatingPointLoops.this, "FPL_FLOATING_POINT_LOOPS", NORMAL_PRIORITY)
                                .addClass(FloatingPointLoops.this).addMethod(FloatingPointLoops.this).addSourceLine(FloatingPointLoops.this, loopPC));
                    }
                break;
            }
            return false;
        }
    }
}
