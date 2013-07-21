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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantInteger;
import org.apache.bcel.classfile.Method;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XMethod;

/**
 * looks for methods that access arrays or classes that implement java.util.List
 * using a constant integer for the index. This is often a typo intented to be a loop
 * variable, but if specific indices mean certain things, perhaps a first class object
 * would be a better choice for a container.
 */
public class ConstantListIndex extends BytecodeScanningDetector
{
	enum State {SAW_NOTHING, SAW_CONSTANT_0, SAW_CONSTANT}
	
	private static final String MAX_ICONST0_LOOP_DISTANCE_PROPERTY = "fb-contrib.cli.maxloopdistance";
	private static final Set<String> ubiquitousMethods = new HashSet<String>(2);
	static {
		ubiquitousMethods.add("java.lang.String.split(Ljava/lang/String;)[Ljava/lang/String;");
	}
	
	
	private final BugReporter bugReporter;
	private State state;
	private Set<Integer> iConst0Looped;
	private final int max_iConst0LoopDistance;
	private OpcodeStack stack;
	
	/**
     * constructs a CLI detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
	 */	
	public ConstantListIndex(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
		max_iConst0LoopDistance = Integer.getInteger(MAX_ICONST0_LOOP_DISTANCE_PROPERTY, 30).intValue();
	}

	/**
	 * implements the visitor to create and clear the const0loop set
	 * 
	 * @param classContext the context object of the currently parsed class
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			iConst0Looped = new HashSet<Integer>(10);
			stack = new OpcodeStack();
			super.visitClassContext(classContext);
		} finally {
			iConst0Looped = null;
			stack = null;
		}
	}
	
	/**
	 * implements the visitor to reset the state
	 * 
	 * @param obj the context object for the currently parsed code block
	 */
	@Override
	public void visitMethod(Method obj) {
		state = State.SAW_NOTHING;
		iConst0Looped.clear();
		stack.resetForMethodEntry(this);
	}
	
	/**
	 * implements the visitor to find accesses to lists or arrays using constants
	 * 
	 * @param seen the currently visitor opcode
	 */
	@Override	
	public void sawOpcode(int seen) {
		try {
			switch (state) {
				case SAW_NOTHING:
					if (seen == ICONST_0)
						state = State.SAW_CONSTANT_0;
					else if ((seen >= ICONST_1) && (seen <= ICONST_5))
						state = State.SAW_CONSTANT;
					else if ((seen == LDC) || (seen == LDC_W)) {
						Constant c = getConstantRefOperand();
						if (c instanceof ConstantInteger)
							state = State.SAW_CONSTANT;
					}
				break;
				
				case SAW_CONSTANT_0:
				case SAW_CONSTANT:
					switch (seen) {
						case AALOAD:
							if ("main".equals(this.getMethodName()))
								break;
							//$FALL-THROUGH$
						case IALOAD:
						case LALOAD:
						case FALOAD:
						case DALOAD:
						//case BALOAD: byte and char indexing seems prevalent, and 
						//case CALOAD: usually harmless so ignore
						case SALOAD:
							if (state == State.SAW_CONSTANT_0)
								iConst0Looped.add(Integer.valueOf(getPC()));
							else {
								if (stack.getStackDepth() > 1) {
									OpcodeStack.Item item = stack.getStackItem(1);
									if (!isArrayFromUbiquitousMethod(item)) {
										bugReporter.reportBug(new BugInstance(this, "CLI_CONSTANT_LIST_INDEX", NORMAL_PRIORITY)
													.addClass(this)
													.addMethod(this)
													.addSourceLine(this));
									}
								}

							}
						break;
						
						case INVOKEVIRTUAL:
							if ("java/util/List".equals(getClassConstantOperand())) {
								String methodName = getNameConstantOperand();
								if ("get".equals(methodName)) {
									if (state == State.SAW_CONSTANT_0) 
										iConst0Looped.add(Integer.valueOf(getPC()));
									else {
										bugReporter.reportBug(new BugInstance(this, "CLI_CONSTANT_LIST_INDEX", NORMAL_PRIORITY)
																.addClass(this)
																.addMethod(this)
																.addSourceLine(this));
									}
								}
							}
						break;
					}
					state = State.SAW_NOTHING;
				break;
			}
			
			if (((seen >= IFEQ) && (seen <= GOTO)) || (seen == GOTO_W)) {
				int branchTarget = this.getBranchTarget();
				Iterator<Integer> it = iConst0Looped.iterator();
				while (it.hasNext()) {
					int bugPC = it.next().intValue();
					if (branchTarget < bugPC) {
						if ((bugPC - branchTarget) < max_iConst0LoopDistance) {
							bugReporter.reportBug(new BugInstance(this, "CLI_CONSTANT_LIST_INDEX", NORMAL_PRIORITY)
													.addClass(this)
													.addMethod(this)
													.addSourceLine(this, bugPC));
						}
						it.remove();
					}
				}
			}
		} finally {
			stack.sawOpcode(this, seen);
		}
	}
	
	/**
	 * returns whether the array item was returned from a common method that the user can't do anything about
	 * and so don't report CLI in this case.
	 * 
	 * @param item the stack item representing the array
	 * @return if the array was returned from a common method
	 */
	private boolean isArrayFromUbiquitousMethod(OpcodeStack.Item item) {
		XMethod method = item.getReturnValueOf();
		if (method == null)
			return false;
		
		String methodDesc = method.getClassName() + '.' + method.getName() + method.getSignature();
		return ubiquitousMethods.contains(methodDesc);
	}
}
