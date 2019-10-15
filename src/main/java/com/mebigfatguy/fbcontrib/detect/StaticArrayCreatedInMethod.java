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

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;

/**
 * looks for creation of arrays where the contents are Const, or static defined
 * as static fields so the method doesn't constantly recreate the array each
 * time it is called.
 */
public class StaticArrayCreatedInMethod extends BytecodeScanningDetector {
    enum State {
        SEEN_NOTHING, SEEN_ARRAY_SIZE, SEEN_NEWARRAY, SEEN_DUP, SEEN_INDEX, SEEN_LDC, SEEN_INDEX_STORE
    }

    private BugReporter bugReporter;
    private int arraySize;
    private int storeCount;
    private State state;

    public StaticArrayCreatedInMethod(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor by forwarding calls for methods that are the static
     * initializer
     *
     * @param obj the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        if (!Values.STATIC_INITIALIZER.equals(getMethodName())) {
            state = State.SEEN_NOTHING;
            super.visitCode(obj);
        }
    }

    /**
     * implements the visitor to look for creation of local arrays using constant
     * values
     *
     * @param seen the opcode of the currently parsed instruction
     */
    @Override
    public void sawOpcode(int seen) {
        int index;

        switch (state) {
        case SEEN_NOTHING:
            if (seen == Const.BIPUSH) {
                arraySize = getIntConstant();
                if (arraySize > 0) {
                    state = State.SEEN_ARRAY_SIZE;
                }
            } else if ((seen >= Const.ICONST_M1) && (seen <= Const.ICONST_5)) {
                arraySize = seen - Const.ICONST_M1 - 1;
                if (arraySize > 0) {
                    state = State.SEEN_ARRAY_SIZE;
                }
            }
            break;

        case SEEN_ARRAY_SIZE:
            if ((seen == Const.ANEWARRAY) || (seen == Const.NEWARRAY)) {
                state = State.SEEN_NEWARRAY;
                storeCount = 0;
            } else {
                state = State.SEEN_NOTHING;
            }
            break;

        case SEEN_NEWARRAY:
            if (seen == Const.DUP) {
                state = State.SEEN_DUP;
            } else {
                state = State.SEEN_NOTHING;
            }
            break;

        case SEEN_DUP:
            if (seen == Const.BIPUSH) {
                index = getIntConstant();
            } else if ((seen >= Const.ICONST_M1) && (seen <= Const.ICONST_5)) {
                index = seen - Const.ICONST_M1 - 1;
            } else {
                state = State.SEEN_NOTHING;
                return;
            }
            if (index != storeCount) {
                state = State.SEEN_NOTHING;
            } else {
                state = State.SEEN_INDEX;
            }
            break;

        case SEEN_INDEX:
            if ((seen == Const.LDC) || (seen == Const.LDC_W)) {
                state = State.SEEN_LDC;
            } else {
                state = State.SEEN_NOTHING;
            }
            break;

        case SEEN_LDC:
            if ((seen >= Const.IASTORE) && (seen <= Const.SASTORE)) {
                if ((++storeCount) == arraySize) {
                    state = State.SEEN_INDEX_STORE;
                } else {
                    state = State.SEEN_NEWARRAY;
                }
            }
            break;

        case SEEN_INDEX_STORE:
            if (OpcodeUtils.isAStore(seen)) {
                bugReporter.reportBug(new BugInstance(this, BugType.SACM_STATIC_ARRAY_CREATED_IN_METHOD.name(),
                        (arraySize < 3) ? LOW_PRIORITY : ((arraySize < 10) ? NORMAL_PRIORITY : HIGH_PRIORITY))
                                .addClass(this).addMethod(this).addSourceLine(this, getPC()));
            }
            state = State.SEEN_NOTHING;
            break;
        }
    }
}
