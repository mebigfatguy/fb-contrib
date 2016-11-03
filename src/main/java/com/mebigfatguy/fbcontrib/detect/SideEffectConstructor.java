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

import org.apache.bcel.classfile.Code;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for constructors that operate through side effects, specifically constructors that aren't assigned to any variable or field.
 */
@CustomUserValue
public class SideEffectConstructor extends BytecodeScanningDetector {

    private enum State {
        SAW_NOTHING, SAW_CTOR
    };

    private final BugReporter bugReporter;
    private OpcodeStack stack;
    private State state;

    /**
     * constructs a SEC detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public SideEffectConstructor(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * overrides the visitor to set up and tear down the opcode stack
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
     * overrides the visitor to reset the state and reset the opcode stack
     *
     * @param obj
     *            the context object of the currently parsed code
     */
    @Override
    public void visitCode(Code obj) {
        state = State.SAW_NOTHING;
        stack.resetForMethodEntry(this);
        super.visitCode(obj);
    }

    /**
     * overrides the visitor to look for constructors who's value is popped off the stack, and not assigned before the pop of the value, or if a return is
     * issued with that object still on the stack.
     *
     * @param seen
     *            the opcode of the currently parse opcode
     */
    @Override
    public void sawOpcode(int seen) {
        int pc = 0;
        try {
            stack.precomputation(this);

            switch (state) {
                case SAW_NOTHING:
                    pc = sawOpcodeAfterNothing(seen);
                break;

                case SAW_CTOR:
                    if ((seen == POP) || (seen == RETURN)) {
                        bugReporter.reportBug(new BugInstance(this, BugType.SEC_SIDE_EFFECT_CONSTRUCTOR.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                                .addSourceLine(this));
                    }
                    state = State.SAW_NOTHING;
                break;
            }
        } finally {
            TernaryPatcher.pre(stack, seen);
            stack.sawOpcode(this, seen);
            TernaryPatcher.post(stack, seen);
            if ((pc != 0) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item item = stack.getStackItem(0);
                item.setUserValue(Integer.valueOf(pc));
            }
        }
    }

    private int sawOpcodeAfterNothing(int seen) {
        if (seen == INVOKESPECIAL) {
            String name = getNameConstantOperand();
            if (Values.CONSTRUCTOR.equals(name)) {
                String sig = getSigConstantOperand();
                int numArgs = SignatureUtils.getNumParameters(sig);
                if (stack.getStackDepth() > numArgs) {
                    OpcodeStack.Item caller = stack.getStackItem(numArgs);
                    if (caller.getRegisterNumber() != 0) {
                        state = State.SAW_CTOR;
                        return getPC();
                    }
                }
            }
        } else if (seen == RETURN) {
            int depth = stack.getStackDepth();
            for (int i = 0; i < depth; i++) {
                OpcodeStack.Item item = stack.getStackItem(i);
                Integer secPC = (Integer) item.getUserValue();
                if (secPC != null) {
                    bugReporter.reportBug(new BugInstance(this, BugType.SEC_SIDE_EFFECT_CONSTRUCTOR.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                            .addSourceLine(this, secPC.intValue()));
                    break;
                }

            }
        }
        return 0;
    }
}
