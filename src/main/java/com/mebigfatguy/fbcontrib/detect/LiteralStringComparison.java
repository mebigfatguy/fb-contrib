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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XField;
import edu.umd.cs.findbugs.ba.XMethod;

/**
 * looks for methods that compare strings against literal strings, where the literal string is passed as the parameter. If the .equals or .compareTo is called
 * on the literal itself, passing the variable as the parameter, you avoid the possibility of a NullPointerException.
 *
 * Updated for 1.7 to not throw false positives for string-based switch statements (which are susceptible to NPEs). String-based switch generate
 * String.equals(Constant) bytecodes, and thus, must be accounted for
 */
@CustomUserValue
public class LiteralStringComparison extends BytecodeScanningDetector {
    private BugReporter bugReporter;
    private OpcodeStack stack;
    /**
     * the object that was switched on, to the switch targets for that switch
     */
    private List<LookupDetails> lookupSwitches;

    /**
     * constructs a LSC detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public LiteralStringComparison(final BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to create and clear the stack
     *
     * @param classContext
     *            the context object for the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            lookupSwitches = new ArrayList<>();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            lookupSwitches = null;
        }
    }

    /**
     * looks for methods that contain a LDC or LDC_W opcodes
     *
     * @param method
     *            the context object of the current method
     * @return if the class loads constants
     */
    public boolean prescreen(Method method) {
        BitSet bytecodeSet = getClassContext().getBytecodeSet(method);
        return (bytecodeSet != null) && (bytecodeSet.get(Constants.LDC) || bytecodeSet.get(Constants.LDC_W));
    }

    /**
     * overrides the visitor to reset the opcode stack
     *
     * @param obj
     *            the code object for the currently parsed method
     */
    @Override
    public void visitCode(final Code obj) {
        if (prescreen(getMethod())) {
            stack.resetForMethodEntry(this);
            lookupSwitches.clear();
            super.visitCode(obj);
        }
    }

    /**
     * looks for strings comparisons where the stack object is a literal
     *
     * @param seen
     *            the currently parsed opcode
     */
    @Override
    public void sawOpcode(final int seen) {
        Object hashCodedStringRef = null;
        try {
            stack.precomputation(this);

            switch (seen) {
                case INVOKEVIRTUAL:
                    if (Values.SLASHED_JAVA_LANG_STRING.equals(getClassConstantOperand())) {
                        String calledMethodName = getNameConstantOperand();
                        String calledMethodSig = getSigConstantOperand();

                        if (("equals".equals(calledMethodName) && SignatureBuilder.SIG_OBJECT_TO_BOOLEAN.equals(calledMethodSig))
                                || ("compareTo".equals(calledMethodName) && SignatureBuilder.SIG_STRING_TO_INT.equals(calledMethodSig))
                                || ("equalsIgnoreCase".equals(calledMethodName) && SignatureBuilder.SIG_STRING_TO_BOOLEAN.equals(calledMethodSig))) {

                            if (stack.getStackDepth() > 0) {
                                OpcodeStack.Item itm = stack.getStackItem(0);
                                Object constant = itm.getConstant();
                                if ((constant != null) && constant.getClass().equals(String.class) && !lookupSwitchOnString()) {
                                    bugReporter.reportBug(new BugInstance(this, "LSC_LITERAL_STRING_COMPARISON", HIGH_PRIORITY) // very
                                                                                                                                // confident
                                            .addClass(this).addMethod(this).addSourceLine(this));
                                }
                            }
                        } else if ("hashCode".equals(calledMethodName) && (stack.getStackDepth() > 0)) {
                            OpcodeStack.Item item = stack.getStackItem(0);
                            int reg = item.getRegisterNumber();
                            if (reg >= 0) {
                                hashCodedStringRef = String.valueOf(reg);
                            } else {
                                XField xf = item.getXField();
                                if (xf != null) {
                                    hashCodedStringRef = xf.getName();
                                } else {
                                    XMethod xm = item.getReturnValueOf();
                                    if (xm != null) {
                                        hashCodedStringRef = xm.toString();
                                    }
                                }

                            }
                        }
                    }
                break;

                case LOOKUPSWITCH:
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item item = stack.getStackItem(0);
                        String stringRef = (String) item.getUserValue();
                        if (stringRef != null) {
                            int[] offsets = getSwitchOffsets();
                            BitSet bs = new BitSet();
                            int pc = getPC();
                            for (int offset : offsets) {
                                bs.set(pc + offset);
                            }
                            bs.set(pc + getDefaultSwitchOffset());
                            lookupSwitches.add(new LookupDetails(stringRef, bs));
                        }
                    }
                break;
                default:
                break;
            }

        } finally {
            stack.sawOpcode(this, seen);
            if ((hashCodedStringRef != null) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item item = stack.getStackItem(0);
                item.setUserValue(hashCodedStringRef);
            }

            if (!lookupSwitches.isEmpty()) {
                int innerMostSwitch = lookupSwitches.size() - 1;
                LookupDetails details = lookupSwitches.get(innerMostSwitch);
                if (details.switchTargets.get(getPC()) && (stack.getStackDepth() > 0)) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    item.setUserValue(details.getStringReference());
                }

                // previousSetBit is a jdk1.7 api - gonna cheat cause findbugs
                // runs on 1.7
                if (getPC() >= details.getSwitchTargets().previousSetBit(Integer.MAX_VALUE)) {
                    lookupSwitches.remove(innerMostSwitch);
                }
            }
        }
    }

    /**
     * looks to see if the string used in a equals or compareTo is the same as that of a switch statement's switch on string.
     *
     * @return if the string is used in a switch
     */
    private boolean lookupSwitchOnString() {
        if (stack.getStackDepth() > 1) {
            OpcodeStack.Item item = stack.getStackItem(1);
            String stringRef = (String) item.getUserValue();

            if (stringRef == null) {
                return false;
            }

            if (!lookupSwitches.isEmpty()) {
                LookupDetails details = lookupSwitches.get(lookupSwitches.size() - 1);
                return stringRef.equals(details.getStringReference());
            }
        }

        return true;
    }

    /**
     * holds information about a switch statement, and it's case PC values
     *
     */
    static class LookupDetails {
        private String stringReference;
        private BitSet switchTargets;

        public LookupDetails(String stringRef, BitSet switchOffs) {
            stringReference = stringRef;
            switchTargets = switchOffs;
        }

        public String getStringReference() {
            return stringReference;
        }

        public BitSet getSwitchTargets() {
            return switchTargets;
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }
}
