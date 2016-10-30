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

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.collect.MethodInfo;
import com.mebigfatguy.fbcontrib.collect.Statistics;
import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for methods that are implemented using synchronized blocks, but are overly synchronized because the beginning of the block only accesses local
 * variables, and not member variables, or this.
 */
public class BloatedSynchronizedBlock extends BytecodeScanningDetector {
    private final BugReporter bugReporter;
    private static final String BSB_MIN_SAFE_CODE_SIZE = "fb-contrib.bsb.minsize";
    private OpcodeStack stack;
    private BitSet unsafeAliases;
    private Map<Integer, Integer> branchInfo;
    private int syncPC;
    private boolean isStatic;
    private final int minSafeCodeLength;
    private boolean unsafeCallOccurred;

    /**
     * constructs a BSB detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public BloatedSynchronizedBlock(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
        minSafeCodeLength = Integer.getInteger(BSB_MIN_SAFE_CODE_SIZE, 16).intValue();
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            unsafeAliases = new BitSet();
            branchInfo = new HashMap<>();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            unsafeAliases = null;
            branchInfo = null;
        }
    }

    /**
     * looks for methods that contain a MONITORENTER opcodes
     *
     * @param method
     *            the context object of the current method
     * @return if the class uses synchronization
     */
    private boolean prescreen(Method method) {
        BitSet bytecodeSet = getClassContext().getBytecodeSet(method);
        return (bytecodeSet != null) && (bytecodeSet.get(Constants.MONITORENTER));
    }

    /**
     * implement the visitor to reset the sync count, the stack, and gather some information
     *
     * @param obj
     *            the context object for the currently parsed method
     */
    @Override
    public void visitCode(Code obj) {
        Method m = getMethod();
        if (prescreen(m)) {
            if (m.isSynchronized()) {
                syncPC = 0;
            } else {
                syncPC = -1;
            }
            isStatic = m.isStatic();
            unsafeAliases.clear();
            unsafeAliases.set(0);
            branchInfo.clear();
            unsafeCallOccurred = false;
            stack.resetForMethodEntry(this);
        }
    }

    /**
     * implement the visitor to find bloated sync blocks. This implementation only checks the outer most block
     *
     * @param seen
     *            the opcode of the currently parsed instruction
     */
    @Override
    public void sawOpcode(int seen) {
        try {
            stack.precomputation(this);

            if (unsafeCallOccurred && OpcodeUtils.isAStore(seen)) {
                int storeReg = RegisterUtils.getAStoreReg(this, seen);
                if (storeReg >= 0) {
                    unsafeAliases.set(storeReg);
                }
            }

            if ((seen == INVOKEVIRTUAL) || (seen == INVOKESPECIAL) || (seen == INVOKEINTERFACE) || (seen == INVOKEDYNAMIC)) {
                String methodSig = getSigConstantOperand();

                MethodInfo mi = Statistics.getStatistics().getMethodStatistics(getClassConstantOperand(), getNameConstantOperand(), methodSig);
                if (mi.getModifiesState()) {
                    unsafeCallOccurred = true;
                } else {
                    if (!"V".equals(SignatureUtils.getReturnSignature(methodSig))) {
                        int parmCount = Type.getArgumentTypes(methodSig).length;
                        if (stack.getStackDepth() > parmCount) {
                            OpcodeStack.Item itm = stack.getStackItem(parmCount);
                            unsafeCallOccurred = unsafeAliases.get(itm.getRegisterNumber());
                        } else {
                            unsafeCallOccurred = false;
                        }
                    } else {
                        unsafeCallOccurred = false;
                    }
                }
            } else if (seen == INVOKESTATIC) {
                unsafeCallOccurred = getDottedClassConstantOperand().equals(this.getClassContext().getJavaClass().getClassName());
            } else if (((seen >= IFEQ) && (seen <= GOTO)) || (seen == GOTO_W)) {
                Integer from = Integer.valueOf(getPC());
                Integer to = Integer.valueOf(getBranchTarget());
                branchInfo.put(from, to);
                unsafeCallOccurred = false;
            } else {
                unsafeCallOccurred = false;
            }

            if (seen == MONITORENTER) {
                if (syncPC < 0) {
                    syncPC = getPC();
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item itm = stack.getStackItem(0);
                        int monitorReg = itm.getRegisterNumber();
                        if (monitorReg >= 0) {
                            unsafeAliases.set(monitorReg);
                        }
                    }
                }
            } else if (seen == MONITOREXIT) {
                syncPC = -1;
            } else if (syncPC >= 0) {
                processSyncBlockInstruction(seen);
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    private void processSyncBlockInstruction(int seen) {
        // TODO: probably static calls are unsafe only if the monitor is
        // on a static
        boolean unsafe = unsafeCallOccurred;
        unsafe |= ((seen == PUTFIELD) || (seen == GETFIELD) || (seen == GETSTATIC) || (seen == PUTSTATIC));
        unsafe |= (!isStatic) && ((seen == ALOAD_0) || (seen == ASTORE_0));
        int aloadReg = RegisterUtils.getALoadReg(this, seen);
        unsafe |= (aloadReg >= 0) && unsafeAliases.get(aloadReg);
        if (unsafe) {
            // If a branch exists in the safe code, make sure the entire
            // branch
            // is in the safe code, otherwise trim before the branch
            int pc = getPC();
            if ((pc - syncPC) > minSafeCodeLength) {
                for (Map.Entry<Integer, Integer> entry : branchInfo.entrySet()) {
                    int bStart = entry.getKey().intValue();
                    if ((bStart >= syncPC) && (bStart <= pc)) {
                        int bEnd = entry.getValue().intValue();
                        if (bEnd > pc) {
                            pc = bStart - 1;
                        }
                    }
                }
                if ((pc - syncPC) > minSafeCodeLength) {
                    bugReporter.reportBug(new BugInstance(this, BugType.BSB_BLOATED_SYNCHRONIZED_BLOCK.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                            .addSourceLineRange(this, syncPC + 1, pc));
                }
            }
            syncPC = -1;
        }
    }
}
