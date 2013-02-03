/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2013 Dave Brosius
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

public class PossibleConstantAllocationInLoop extends BytecodeScanningDetector {

    private static final Set<String> SYNTHETIC_ALLOCATION_CLASSES = new HashSet<String>();
    static {
        SYNTHETIC_ALLOCATION_CLASSES.add("java/lang/StringBuffer");
        SYNTHETIC_ALLOCATION_CLASSES.add("java/lang/StringBuilder");
        SYNTHETIC_ALLOCATION_CLASSES.add("java/lang/AssertionError");
    }

	private final BugReporter bugReporter;
	private OpcodeStack stack;
	/** allocation number, info where allocated */
	private Map<Integer, AllocationInfo> allocations;
	/** reg, allocation number */
	private Map<Integer, Integer> storedAllocations;
	private int nextAllocationNumber;

	public PossibleConstantAllocationInLoop(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}

	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			stack = new OpcodeStack();
			allocations = new HashMap<Integer, AllocationInfo>();
			storedAllocations = new HashMap<Integer, Integer>();
			super.visitClassContext(classContext);
		} finally {
			stack = null;
			allocations = null;
			storedAllocations = null;
		}
	}

	@Override
	public void visitCode(Code obj) {
		stack.resetForMethodEntry(this);
		allocations.clear();
		storedAllocations.clear();
		nextAllocationNumber = 1;
		super.visitCode(obj);

		for (AllocationInfo info : allocations.values()) {
			if (info.loopBottom != -1) {
				bugReporter.reportBug(new BugInstance(this, "PCAIL_POSSIBLE_CONSTANT_ALLOCATION_IN_LOOP", NORMAL_PRIORITY)
							.addClass(this)
							.addMethod(this)
							.addSourceLine(getClassContext(), this, info.allocationPC));
			}
		}
	}

	@Override
	public void sawOpcode(int seen) {
		boolean sawAllocation = false;
		Integer sawAllocationNumber = null;

		try {
			switch (seen)  {
				case IFEQ:
				case IFNE:
				case IFLT:
				case IFGE:
				case IFGT:
				case IFLE:
				case IF_ICMPEQ:
				case IF_ICMPNE:
				case IF_ICMPLT:
				case IF_ICMPGE:
				case IF_ICMPGT:
				case IF_ICMPLE:
				case IF_ACMPEQ:
				case IF_ACMPNE:
				case IFNULL:
				case IFNONNULL:
				case GOTO:
				case GOTO_W:
					if (getBranchOffset() < 0) {
						int branchLoc = getBranchTarget();
						int pc = getPC();
						for (AllocationInfo info : allocations.values()) {
							if ((info.loopTop == -1) && (branchLoc < info.allocationPC)) {
								info.loopTop = branchLoc;
								info.loopBottom = pc;
							}
						}
					}
				break;

				case INVOKESPECIAL:
					if ("<init>".equals(getNameConstantOperand()) && "()V".equals(getSigConstantOperand())) {
						String clsName = getClassConstantOperand();
						if (!SYNTHETIC_ALLOCATION_CLASSES.contains(clsName)) {
							sawAllocationNumber = Integer.valueOf(nextAllocationNumber);
							allocations.put(sawAllocationNumber, new AllocationInfo(getPC()));
							sawAllocation = true;
						}
					}
				//$FALL-THROUGH$

				case INVOKEINTERFACE:
				case INVOKEVIRTUAL:
				case INVOKESTATIC:
				    String signature = getSigConstantOperand();
					Type[] types = Type.getArgumentTypes(signature);
					if (stack.getStackDepth() >= types.length) {
						for (int i = 0; i < types.length; i++) {
							OpcodeStack.Item item = stack.getStackItem(i);
							Integer allocation = (Integer)item.getUserValue();
							if (allocation != null) {
								allocations.remove(allocation);
							}
						}
		                if ((seen == INVOKEINTERFACE) || (seen == INVOKEVIRTUAL) || ((seen == INVOKESPECIAL))) {
		                    //ignore possible method chaining
		                    if (stack.getStackDepth() > types.length) {
    		                    OpcodeStack.Item item = stack.getStackItem(types.length);
    		                    Integer allocation = (Integer)item.getUserValue();
                                if (allocation != null) {
                                    String retType = Type.getReturnType(signature).getSignature();
                                    if (!"V".equals(retType) && retType.equals(item.getSignature())) {
                                        sawAllocationNumber = allocation;
                                        sawAllocation = true;
                                    }
                                }
		                    }
		                }
					}
				break;

				case ASTORE:
				case ASTORE_0:
				case ASTORE_1:
				case ASTORE_2:
				case ASTORE_3:
					if (stack.getStackDepth() > 0) {
						OpcodeStack.Item item = stack.getStackItem(0);
						Integer allocation = (Integer)item.getUserValue();
						if (allocation != null) {
							Integer reg = Integer.valueOf(RegisterUtils.getAStoreReg(this, seen));
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
						}
					}
				break;

				case AASTORE:
					if (stack.getStackDepth() >= 2) {
						OpcodeStack.Item item = stack.getStackItem(0);
						Integer allocation = (Integer)item.getUserValue();
						if (allocation != null) {
							allocations.remove(allocation);
						}
					}
				break;

				case ALOAD:
				case ALOAD_0:
				case ALOAD_1:
				case ALOAD_2:
				case ALOAD_3: {
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

				case PUTFIELD:
					if (stack.getStackDepth() > 1) {
						OpcodeStack.Item item = stack.getStackItem(0);
						Integer allocation = (Integer)item.getUserValue();
						allocations.remove(allocation);
					}
				break;

				case ARETURN:
				case ATHROW:
					if (stack.getStackDepth() > 0) {
						OpcodeStack.Item item = stack.getStackItem(0);
						Integer allocation = (Integer)item.getUserValue();
						if (allocation != null) {
							allocations.remove(allocation);
						}
					}
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

				if (seen == INVOKESPECIAL)
					nextAllocationNumber++;
			}
		}
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
	}
}
