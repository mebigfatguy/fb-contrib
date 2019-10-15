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
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;

/**
 * looks for methods that set a setter with the value obtained from the same
 * bean's complimentary getter. This is usually a typo.
 */
public class SuspiciousGetterSetterUse extends BytecodeScanningDetector {

    private enum State {
        SEEN_NOTHING, SEEN_ALOAD, SEEN_GETFIELD, SEEN_DUAL_LOADS, SEEN_INVOKEVIRTUAL
    }

    private final BugReporter bugReporter;
    private State state;
    private String beanReference1;
    private String beanReference2;
    private String propName;
    private String propType;
    private boolean sawField;

    /**
     * constructs a SGSU detector given the reporter to report bugs on
     *
     * @param bugReporter the sync of bug reports
     */
    public SuspiciousGetterSetterUse(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * overrides the visitor to reset the state to SEEN_NOTHING, and clear the
     * beanReference, propName and propType
     *
     * @param obj the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        state = State.SEEN_NOTHING;
        beanReference1 = null;
        beanReference2 = null;
        propName = null;
        propType = null;
        sawField = false;
        super.visitCode(obj);
    }

    /**
     * overrides the visitor to look for a setXXX with the value returned from a
     * getXXX using the same base object.
     *
     * @param seen the currently parsed opcode
     */
    @Override
    public void sawOpcode(int seen) {
        boolean reset = true;
        switch (state) { // TODO: Refactor this to use state pattern, not nested
                         // switches
        case SEEN_NOTHING:
            reset = sawOpcodeAfterNothing(seen);
            break;

        case SEEN_ALOAD:
            reset = sawOpcodeAfterLoad(seen);
            break;

        case SEEN_GETFIELD:
            reset = sawOpcodeAfterGetField(seen);
            break;

        case SEEN_DUAL_LOADS:
            reset = sawOpcodeAfterDualLoads(seen);
            break;

        case SEEN_INVOKEVIRTUAL:
            if (seen == Const.INVOKEVIRTUAL) {
                checkForSGSU();
            }
            break;
        }

        if (reset) {
            beanReference1 = null;
            beanReference2 = null;
            propType = null;
            propName = null;
            sawField = false;
            state = State.SEEN_NOTHING;
        }
    }

    private boolean sawOpcodeAfterNothing(int seen) {
        if (!OpcodeUtils.isALoad(seen)) {
            return true;
        }

        beanReference1 = String.valueOf(getRegisterOperand());
        state = State.SEEN_ALOAD;
        return false;
    }

    private boolean sawOpcodeAfterLoad(int seen) {
        switch (seen) {
        case Const.ALOAD:
        case Const.ALOAD_0:
        case Const.ALOAD_1:
        case Const.ALOAD_2:
        case Const.ALOAD_3:
            if (!sawField && beanReference1.equals(String.valueOf(getRegisterOperand()))) {
                state = State.SEEN_DUAL_LOADS;
                return false;
            }
            break;

        case Const.GETFIELD: {
            if (sawField) {
                beanReference2 += ':' + getNameConstantOperand();
                if (beanReference1.equals(beanReference2)) {
                    state = State.SEEN_DUAL_LOADS;
                    return false;
                }
            } else {
                state = State.SEEN_GETFIELD;
                beanReference1 += ':' + getNameConstantOperand();
                sawField = true;
                return false;
            }
        }
            break;

        default:
            break;
        }
        return true;
    }

    private boolean sawOpcodeAfterGetField(int seen) {

        if (!OpcodeUtils.isALoad(seen)) {
            return true;
        }

        beanReference2 = String.valueOf(getRegisterOperand());
        state = State.SEEN_ALOAD;
        return false;
    }

    private boolean sawOpcodeAfterDualLoads(int seen) {
        if (seen != Const.INVOKEVIRTUAL) {
            return true;
        }
        String sig = getSigConstantOperand();
        String noParams = SignatureBuilder.PARAM_NONE;
        if (!sig.startsWith(noParams)) {
            return true;
        }
        propType = sig.substring(noParams.length());
        if (Values.SIG_VOID.equals(propType)) {
            return true;
        }
        String methodName = getNameConstantOperand();
        if (!methodName.startsWith("get")) {
            return true;
        }
        propName = methodName.substring("get".length());
        state = State.SEEN_INVOKEVIRTUAL;
        return false;
    }

    private void checkForSGSU() {
        if (!getSigConstantOperand().equals('(' + propType + ")V")) {
            return;
        }
        String name = getNameConstantOperand();
        if (name.startsWith("set") && propName.equals(name.substring("set".length()))) {
            bugReporter
                    .reportBug(new BugInstance(this, BugType.SGSU_SUSPICIOUS_GETTER_SETTER_USE.name(), NORMAL_PRIORITY)
                            .addClass(this).addMethod(this).addSourceLine(this));
        }
    }

}
