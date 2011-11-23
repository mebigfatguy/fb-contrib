/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2011 Dave Brosius
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
import java.util.Map;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;

import com.mebigfatguy.fbcontrib.utils.RegisterUtils;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for code that builds an array by using a StringTokenizer to break up
 * a string and place individual elements into an array. It is simpler to use
 * String.split instead.

 */
public class UseSplit extends BytecodeScanningDetector 
{
	enum State {SEEN_NOTHING, SEEN_STRINGTOKENIZER, SEEN_COUNTTOKENS, SEEN_NEWARRAY, SEEN_HASMORE, SEEN_NEXT, SEEN_ARRAYSTORE}
	
	
	private BugReporter bugReporter;
	private OpcodeStack stack;
	private Map<Integer, State> regValueType;
	private State state;
	private int loopStart, loopEnd;
	
	public UseSplit(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}
	
	@Override
	public void visitClassContext(ClassContext classContext) {
		try {	
			JavaClass cls = classContext.getJavaClass();
			if (cls.getMajor() >= MAJOR_1_4) {
				stack = new OpcodeStack();
				regValueType = new HashMap<Integer, State>();
				super.visitClassContext(classContext);
			}
		} finally {
			stack = null;
			regValueType = null;
		}
	}
	
	/**
	 * implements the visitor to reset the stack
	 * 
	 * @param obj the context object of the currently parsed code block
	 */
	@Override
	public void visitCode(Code obj) {
		stack.resetForMethodEntry(this);
		regValueType.clear();
		state = State.SEEN_NOTHING;
		loopStart = -1;
		loopEnd = -1;
		super.visitCode(obj);
	}
	
	@Override
	public void sawOpcode(int seen) {
		try {
			stack.mergeJumps(this);
			
			int pc = getPC();
			if ((loopEnd != -1) && (pc > loopEnd)) {
				loopStart = -1;
				loopEnd = -1;
				regValueType.clear();
			}
			
			if ((seen == ALOAD) || ((seen >= ALOAD_0) && (seen <= ALOAD_3))) {
				int reg = RegisterUtils.getALoadReg(this, seen);
				State type = regValueType.get(Integer.valueOf(reg));
				if (type != null)
					state = type;
				else
					state = State.SEEN_NOTHING;				
				return;
			}
			if ((seen == ASTORE) || ((seen >= ASTORE_0) && (seen <= ASTORE_3))) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item item = stack.getStackItem(0);
					int reg = RegisterUtils.getAStoreReg(this, seen);
					regValueType.put(Integer.valueOf(reg), (State)item.getUserValue());
				}
				state = State.SEEN_NOTHING;
				return;
			}			
			if ((seen == ILOAD) || ((seen >= ILOAD_0) && (seen <= ILOAD_3))) {
				int reg = RegisterUtils.getLoadReg(this, seen);
				State type = regValueType.get(Integer.valueOf(reg));
				if (type != null)
					state = type;
				else
					state = State.SEEN_NOTHING;
				return;
			}
			if ((seen == ISTORE) || ((seen >= ISTORE_0) && (seen <= ISTORE_3))) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item item = stack.getStackItem(0);
					int reg = RegisterUtils.getStoreReg(this, seen);
					regValueType.put(Integer.valueOf(reg), (State)item.getUserValue());
				}
				state = State.SEEN_NOTHING;
				return;
			}
			
			
			switch (state) {
				case SEEN_NOTHING:
					if (seen == INVOKESPECIAL) {
						if (("java/util/StringTokenizer".equals(getClassConstantOperand()))
						&&  ("<init>".equals(getNameConstantOperand()))
						&&  ("(Ljava/lang/String;Ljava/lang/String;)V".equals(getSigConstantOperand())))
							state = State.SEEN_STRINGTOKENIZER;
					}
				break;
				
				case SEEN_STRINGTOKENIZER:
					if (seen == INVOKEVIRTUAL) {
						String methodName = getNameConstantOperand();
						String signature = getSigConstantOperand();
						if (("countTokens".equals(methodName))
						&&  ("()I".equals(signature)))
							state = State.SEEN_COUNTTOKENS;
						else if ("hasMoreTokens".equals(methodName) || "hasMoreElements".equals(methodName))
							state = State.SEEN_HASMORE;
						else if ("nextToken".equals(methodName) || "nextElement".equals(methodName)) {
							if ((pc < loopStart) || (pc > loopEnd))
								regValueType.clear();
							else
								state = State.SEEN_NEXT;
						}
					}
				break;
				
				case SEEN_COUNTTOKENS:
					if (seen == ANEWARRAY)
						state = State.SEEN_NEWARRAY;
					else if (seen == IF_ICMPGE) {
						int target = getBranchTarget() - 3;//sizeof goto
						byte[] code = getCode().getCode();
						if ((code[target] & 0x000000FF) == GOTO) {
							int offset = (code[target+1] << 1) + code[target+2];
							int gotoTarget = target + offset + 3;
							if (gotoTarget < getPC()) {
								loopStart = gotoTarget;
								loopEnd = target;
							}
						}	
					}
				break;
				
				case SEEN_HASMORE:
					if (seen == IFEQ) {
						int target = getBranchTarget() - 3;//sizeof goto
						byte[] code = getCode().getCode();
						if ((code[target] & 0x000000FF) == GOTO) {
							int offset = (code[target+1] << 1) + code[target+2];
							int gotoTarget = target + offset + 3;
							if (gotoTarget < getPC()) {
								loopStart = gotoTarget;
								loopEnd = target;
							}
						}
					}
					state = State.SEEN_NOTHING;
				break;
				
				case SEEN_NEXT:
					if (seen == AASTORE) {
						if ((pc > loopStart) && (pc < loopEnd)) {
							if (stack.getStackDepth() > 2) {
								OpcodeStack.Item arrayItem = stack.getStackItem(2);
								State arrayType = (State)arrayItem.getUserValue();
								OpcodeStack.Item elemItem = stack.getStackItem(0);
								State elemType = (State)elemItem.getUserValue();
								if ((arrayType == State.SEEN_NEWARRAY) && (elemType == State.SEEN_NEXT)) {
									bugReporter.reportBug(new BugInstance(this, "USS_USE_STRING_SPLIT", NORMAL_PRIORITY)
											   .addClass(this)
											   .addMethod(this)
											   .addSourceLine(this));
								}
							}
						}
					}
					state = State.SEEN_NOTHING;
				break;
			}
		} finally {
			stack.sawOpcode(this, seen);
			if (state != State.SEEN_NOTHING) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item item = stack.getStackItem(0);
					item.setUserValue(state);
				}
			}
		}
	}

}
