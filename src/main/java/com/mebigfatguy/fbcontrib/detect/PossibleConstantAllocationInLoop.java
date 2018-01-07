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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.LocalVariable;
import org.apache.bcel.classfile.LocalVariableTable;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for allocations of objects using the default constructor in a loop, where the object allocated is never assigned to any object that is used outside the
 * loop. It is possible that this allocation can be done outside the loop to avoid excessive garbage.
 */
@CustomUserValue
public class PossibleConstantAllocationInLoop extends BytecodeScanningDetector {

    private static final Set<String> SYNTHETIC_ALLOCATION_CLASSES = UnmodifiableSet.create(Values.SLASHED_JAVA_LANG_STRINGBUFFER,
            Values.SLASHED_JAVA_LANG_STRINGBUILDER, "java/lang/AssertionError");

    private final BugReporter bugReporter;
    private OpcodeStack stack;
    /** allocation number, info where allocated */
    private Map<Integer, AllocationInfo> allocations;
    /** reg, allocation number */
    private Map<Integer, Integer> storedAllocations;
    private int nextAllocationNumber;
    private List<SwitchInfo> switchInfos;
    private int nextTernaryTarget;

    public PossibleConstantAllocationInLoop(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            allocations = new HashMap<>();
            storedAllocations = new HashMap<>();
            switchInfos = new ArrayList<>();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            allocations = null;
            storedAllocations = null;
            switchInfos = null;
        }
    }

    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        allocations.clear();
        storedAllocations.clear();
        nextAllocationNumber = 1;
        nextTernaryTarget = -1;
        super.visitCode(obj);

        for (AllocationInfo info : allocations.values()) {
            if (info.loopBottom != -1) {
                bugReporter.reportBug(new BugInstance(this, BugType.PCAIL_POSSIBLE_CONSTANT_ALLOCATION_IN_LOOP.name(), NORMAL_PRIORITY).addClass(this)
                        .addMethod(this).addSourceLine(getClassContext(), this, info.allocationPC));
            }
        }
    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "SF_SWITCH_FALLTHROUGH", justification = "This fall-through is deliberate and documented")
    @Override
    public void sawOpcode(int seen) {
        boolean sawAllocation = false;
        Integer sawAllocationNumber = null;

        try {
            if ((nextTernaryTarget == getPC()) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item itm = stack.getStackItem(0);
                sawAllocationNumber = (Integer) itm.getUserValue();
                sawAllocation = sawAllocationNumber != null;
                nextTernaryTarget = -1;
            }

            stack.precomputation(this);

            if ((sawAllocation) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item itm = stack.getStackItem(0);
                itm.setUserValue(sawAllocationNumber);
                sawAllocationNumber = null;
                sawAllocation = false;
            }

            switch (seen) {
                case Const.GOTO:
                case Const.GOTO_W:
                    if ((getBranchOffset() > 0) && (stack.getStackDepth() > 0)) {
                        // ternary
                        nextTernaryTarget = getBranchTarget();
                    }
                    // $FALL-THROUGH$
                case Const.IFEQ:
                case Const.IFNE:
                case Const.IFLT:
                case Const.IFGE:
                case Const.IFGT:
                case Const.IFLE:
                case Const.IF_ICMPEQ:
                case Const.IF_ICMPNE:
                case Const.IF_ICMPLT:
                case Const.IF_ICMPGE:
                case Const.IF_ICMPGT:
                case Const.IF_ICMPLE:
                case Const.IF_ACMPEQ:
                case Const.IF_ACMPNE:
                case Const.IFNULL:
                case Const.IFNONNULL:

                    processBranch();
                break;

                case Const.INVOKESPECIAL:
                    if (Values.CONSTRUCTOR.equals(getNameConstantOperand()) && SignatureBuilder.SIG_VOID_TO_VOID.equals(getSigConstantOperand())) {
                        String clsName = getClassConstantOperand();
                        if (!SYNTHETIC_ALLOCATION_CLASSES.contains(clsName) && switchInfos.isEmpty()) {
                            sawAllocationNumber = Integer.valueOf(nextAllocationNumber);
                            allocations.put(sawAllocationNumber, new AllocationInfo(getPC()));
                            sawAllocation = true;
                        }
                    }
                    //$FALL-THROUGH$

                case Const.INVOKEINTERFACE:
                case Const.INVOKEVIRTUAL:
                case Const.INVOKESTATIC:
                case Const.INVOKEDYNAMIC:
                    String signature = getSigConstantOperand();
                    int numParameters = SignatureUtils.getNumParameters(signature);
                    if (stack.getStackDepth() >= numParameters) {
                        for (int i = 0; i < numParameters; i++) {
                            OpcodeStack.Item item = stack.getStackItem(i);
                            Integer allocation = (Integer) item.getUserValue();
                            if (allocation != null) {
                                allocations.remove(allocation);
                            }
                        }
                        if (((seen == Const.INVOKEINTERFACE) || (seen == Const.INVOKEVIRTUAL) || (seen == Const.INVOKESPECIAL) || (seen == Const.INVOKEDYNAMIC))
                                // ignore possible method chaining
                                && (stack.getStackDepth() > numParameters)) {
                            OpcodeStack.Item item = stack.getStackItem(numParameters);
                            Integer allocation = (Integer) item.getUserValue();
                            if (allocation != null) {
                                String retType = SignatureUtils.getReturnSignature(signature);
                                if (!Values.SIG_VOID.equals(retType) && retType.equals(item.getSignature())) {
                                    sawAllocationNumber = allocation;
                                    sawAllocation = true;
                                }
                            }
                        }
                    } else if (numParameters > 0) {
                        // bad findbugs bug that the stack isn't consistent with reality, so don't
                        // assume anything
                        allocations.clear();
                        storedAllocations.clear();
                    }
                break;

                case Const.ASTORE:
                case Const.ASTORE_0:
                case Const.ASTORE_1:
                case Const.ASTORE_2:
                case Const.ASTORE_3:
                    processAStore(seen);
                break;

                case Const.AASTORE:
                    if (stack.getStackDepth() >= 2) {
                        OpcodeStack.Item item = stack.getStackItem(0);
                        Integer allocation = (Integer) item.getUserValue();
                        if (allocation != null) {
                            allocations.remove(allocation);
                        }
                    }
                break;

                case Const.ALOAD:
                case Const.ALOAD_0:
                case Const.ALOAD_1:
                case Const.ALOAD_2:
                case Const.ALOAD_3: {
                    Integer reg = Integer.valueOf(RegisterUtils.getALoadReg(this, seen));
                    Integer allocation = storedAllocations.get(reg);
                    if (allocation != null) {
                        AllocationInfo info = allocations.get(allocation);
                        if ((info != null) && (info.loopBottom != -1)) {
                            allocations.remove(allocation);
                            storedAllocations.remove(reg);
                        } else {
                            sawAllocationNumber = allocation;
                            sawAllocation = true;
                        }
                    }
                }
                break;

                case Const.PUTFIELD:
                    if (stack.getStackDepth() > 1) {
                        OpcodeStack.Item item = stack.getStackItem(0);
                        Integer allocation = (Integer) item.getUserValue();
                        allocations.remove(allocation);
                    }
                break;

                case Const.ARETURN:
                case Const.ATHROW:
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item item = stack.getStackItem(0);
                        Integer allocation = (Integer) item.getUserValue();
                        if (allocation != null) {
                            item.setUserValue(null);
                            allocations.remove(allocation);
                        }
                    }
                break;

                case Const.LOOKUPSWITCH:
                case Const.TABLESWITCH:
                    int[] offsets = getSwitchOffsets();
                    if (offsets.length > 0) {
                        int top = getPC();
                        int bottom = top + offsets[offsets.length - 1];
                        SwitchInfo switchInfo = new SwitchInfo(bottom);
                        switchInfos.add(switchInfo);
                    }
                break;

                default:
                break;
            }
        } finally {
            TernaryPatcher.pre(stack, seen);
            stack.sawOpcode(this, seen);
            TernaryPatcher.post(stack, seen);
            if (sawAllocation) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    item.setUserValue(sawAllocationNumber);
                }

                if (seen == Const.INVOKESPECIAL) {
                    nextAllocationNumber++;
                }
            }

            if (!switchInfos.isEmpty() && (getPC() >= switchInfos.get(switchInfos.size() - 1).switchBottom)) {
                switchInfos.remove(switchInfos.size() - 1);
            }
        }
    }

    private void processBranch() {
        if (getBranchOffset() < 0) {
            int branchLoc = getBranchTarget();
            int pc = getPC();
            for (AllocationInfo info : allocations.values()) {
                if ((info.loopTop == -1) && (branchLoc < info.allocationPC)) {
                    info.loopTop = branchLoc;
                    info.loopBottom = pc;
                }
            }
        } else if (!switchInfos.isEmpty()) {
            int target = getBranchTarget();
            SwitchInfo innerSwitch = switchInfos.get(switchInfos.size() - 1);
            if (target > innerSwitch.switchBottom) {
                innerSwitch.switchBottom = target;
            }
        }
    }

    private void processAStore(int seen) {
        if (stack.getStackDepth() > 0) {
            OpcodeStack.Item item = stack.getStackItem(0);
            Integer allocation = (Integer) item.getUserValue();
            if (allocation != null) {
                Integer reg = Integer.valueOf(RegisterUtils.getAStoreReg(this, seen));
                if (isFirstUse(reg.intValue())) {
                    if (storedAllocations.values().contains(allocation)) {
                        allocations.remove(allocation);
                        storedAllocations.remove(reg);
                    } else if (storedAllocations.containsKey(reg)) {
                        allocations.remove(allocation);
                        allocation = storedAllocations.remove(reg);
                        allocations.remove(allocation);
                    } else {
                        storedAllocations.put(reg, allocation);
                    }
                } else {
                    item.setUserValue(null);
                    allocations.remove(allocation);
                }
            }
        }
    }

    /**
     * looks to see if this register has already in scope or whether is a new assignment. return true if it's a new assignment. If you can't tell, return true
     * anyway. might want to change.
     *
     * @param reg
     *            the store register
     * @return whether this is a new register scope assignment
     */
    private boolean isFirstUse(int reg) {
        LocalVariableTable lvt = getMethod().getLocalVariableTable();
        if (lvt == null) {
            return true;
        }

        LocalVariable lv = lvt.getLocalVariable(reg, getPC());
        return lv == null;
    }

    static class AllocationInfo {

        int allocationPC;
        int loopTop;
        int loopBottom;

        public AllocationInfo(int pc) {
            allocationPC = pc;
            loopTop = -1;
            loopBottom = -1;
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }

    static class SwitchInfo {
        int switchBottom;

        public SwitchInfo(int bottom) {
            switchBottom = bottom;
        }
    }
}
