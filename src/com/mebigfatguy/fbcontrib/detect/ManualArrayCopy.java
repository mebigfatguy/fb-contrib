/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2015 Dave Brosius
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

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Method;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;

/**
 * looks for methods that copy data from one array to another using a loop. It
 * is better performing to use System.arraycopy to do such copying as this is a
 * native method.
 */
public class ManualArrayCopy extends BytecodeScanningDetector {
    enum State {
        SAW_NOTHING, SAW_ICMP, SAW_ARRAY1_LOAD, SAW_ARRAY1_INDEX, SAW_ARRAY2_LOAD, SAW_ARRAY2_INDEX, SAW_ELEM_LOAD, SAW_ELEM_STORE
    }

    private static final BitSet arrayLoadOps = new BitSet();

    static {
        arrayLoadOps.set(AALOAD);
        arrayLoadOps.set(BALOAD);
        arrayLoadOps.set(CALOAD);
        arrayLoadOps.set(SALOAD);
        arrayLoadOps.set(IALOAD);
        arrayLoadOps.set(LALOAD);
        arrayLoadOps.set(DALOAD);
        arrayLoadOps.set(FALOAD);
    }

    private final BugReporter bugReporter;
    private State state;
    private int arrayIndexReg;
    private int loadInstruction;

    /**
     * constructs a MAC detector given the reporter to report bugs on
     * 
     * @param bugReporter
     *            the sync of bug reports
     */
    public ManualArrayCopy(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * looks for methods that contain array load opcodes
     * 
     * @param method
     *            the context object of the current method
     * @return if the class loads array contents
     */
    private boolean prescreen(Method method) {
        BitSet bytecodeSet = getClassContext().getBytecodeSet(method);
        return (bytecodeSet != null) && bytecodeSet.intersects(arrayLoadOps);
    }

    /**
     * implements the visitor to reset the state
     * 
     * @param obj
     *            the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        if (prescreen(getMethod())) {
            state = State.SAW_NOTHING;
            super.visitCode(obj);
        }
    }

    @Override
    public void sawOpcode(int seen) {
        switch (state) {
        case SAW_NOTHING:
            if ((seen == IF_ICMPGE) || (seen == IF_ICMPGT))
                state = State.SAW_ICMP;
            break;

        case SAW_ICMP:
            if ((seen == ALOAD) || ((seen >= ALOAD_0) && (seen <= ALOAD_3)))
                state = State.SAW_ARRAY1_LOAD;
            break;

        case SAW_ARRAY1_LOAD:
            if (seen == ILOAD) {
                arrayIndexReg = getRegisterOperand();
                state = State.SAW_ARRAY1_INDEX;
            } else if ((seen >= ILOAD_0) && (seen <= ILOAD_3)) {
                arrayIndexReg = seen - ILOAD_0;
                state = State.SAW_ARRAY1_INDEX;
            } else
                state = State.SAW_NOTHING;
            break;

        case SAW_ARRAY1_INDEX:
            if ((seen == ALOAD) || ((seen >= ALOAD_0) && (seen <= ALOAD_3)))
                state = State.SAW_ARRAY2_LOAD;
            else
                state = State.SAW_NOTHING;
            break;

        case SAW_ARRAY2_LOAD:
            if (seen == ILOAD) {
                if (arrayIndexReg == this.getRegisterOperand())
                    state = State.SAW_ARRAY2_INDEX;
                else
                    state = State.SAW_NOTHING;
            } else if ((seen >= ILOAD_0) && (seen <= ILOAD_3)) {
                if (arrayIndexReg == (seen - ILOAD_0))
                    state = State.SAW_ARRAY2_INDEX;
                else
                    state = State.SAW_NOTHING;
            } else
                state = State.SAW_NOTHING;
            break;

        case SAW_ARRAY2_INDEX:
            if ((seen == AALOAD) || (seen == BALOAD) || (seen == CALOAD) || (seen == SALOAD) || (seen == IALOAD) || (seen == LALOAD) || (seen == DALOAD)
                    || (seen == FALOAD)) {
                loadInstruction = seen;
                state = State.SAW_ELEM_LOAD;
            } else
                state = State.SAW_NOTHING;
            break;

        case SAW_ELEM_LOAD:
            if ((seen == AASTORE) || (seen == BASTORE) || (seen == CASTORE) || (seen == SASTORE) || (seen == IASTORE) || (seen == LASTORE) || (seen == DASTORE)
                    || (seen == FASTORE)) {
                if (similarArrayInstructions(loadInstruction, seen)) {
                    state = State.SAW_ELEM_STORE;
                } else {
                    state = State.SAW_NOTHING;
                }
            } else {
                state = State.SAW_NOTHING;
            }
            break;

        case SAW_ELEM_STORE:
            if (seen == IINC) {
                bugReporter.reportBug(new BugInstance(this, "MAC_MANUAL_ARRAY_COPY", NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
            }
            state = State.SAW_NOTHING;
            break;
        }
    }

    private static boolean similarArrayInstructions(int load, int store) {
        if ((load == AALOAD) && (store == AASTORE))
            return true;
        if ((load == IALOAD) && (store == IASTORE))
            return true;
        if ((load == DALOAD) && (store == DASTORE))
            return true;
        if ((load == LALOAD) && (store == LASTORE))
            return true;
        if ((load == FALOAD) && (store == FASTORE))
            return true;
        if ((load == BALOAD) && (store == BASTORE))
            return true;
        if ((load == CALOAD) && (store == CASTORE))
            return true;
        if ((load == SALOAD) && (store == SASTORE))
            return true;
        return false;
    }
}
