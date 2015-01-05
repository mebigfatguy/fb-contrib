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

import org.apache.bcel.classfile.Code;

import com.mebigfatguy.fbcontrib.utils.BugType;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;

/**
 * looks for methods that set a setter with the value obtained from the same bean's
 * complimentary getter. This is usually a typo.
 */
public class SuspiciousGetterSetterUse extends BytecodeScanningDetector {

	private static enum State {SEEN_NOTHING, SEEN_ALOAD, SEEN_GETFIELD, SEEN_DUAL_LOADS, SEEN_INVOKEVIRTUAL};
	private final BugReporter bugReporter;
	private State state;
	private String beanReference1;
	private String beanReference2;
	private String propName;
	private String propType;
	private boolean sawField;
	
	/**
     * constructs a SGSU detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
	 */
	public SuspiciousGetterSetterUse(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}
	
	/**
	 * overrides the visitor to reset the state to SEEN_NOTHING, and clear the beanReference, propName
	 * and propType
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
	 * overrides the visitor to look for a setXXX with the value returned from a getXXX
	 * using the same base object.
	 * 
	 * @param seen the currently parsed opcode
	 */
	@Override
	public void sawOpcode(int seen) {
		boolean reset = true;
		switch (state) {		//TODO: Refactor this to use state pattern, not nested switches
			case SEEN_NOTHING:
				switch (seen) {
					case ALOAD:
					case ALOAD_0:
					case ALOAD_1:
					case ALOAD_2:
					case ALOAD_3:
						beanReference1 = String.valueOf(getRegisterOperand());
						state = State.SEEN_ALOAD;
						reset = false;
					break;
					default:
						break;
				}
			break;
			
			case SEEN_ALOAD:
				switch (seen) {
					case ALOAD:
					case ALOAD_0:
					case ALOAD_1:
					case ALOAD_2:
					case ALOAD_3:
						if (!sawField && beanReference1.equals(String.valueOf(getRegisterOperand()))) {
							state = State.SEEN_DUAL_LOADS;
							reset = false;
						}
					break;
					
					case GETFIELD: {
						if (sawField) {
							beanReference2 += ":" + getNameConstantOperand();
							if (beanReference1.equals(beanReference2)) {
								state = State.SEEN_DUAL_LOADS;
								reset = false;
							}
						} else {
							state = State.SEEN_GETFIELD;
							beanReference1 += ":" + getNameConstantOperand();
							sawField = true;
							reset = false;
						}
					}
					break;
					default:
						break;
				}
			break;
			
			case SEEN_GETFIELD: {
				switch (seen) {
					case ALOAD:
					case ALOAD_0:
					case ALOAD_1:
					case ALOAD_2:
					case ALOAD_3:
						beanReference2 = String.valueOf(getRegisterOperand());
						state = State.SEEN_ALOAD;
						reset = false;
					break;
					default:
						break;
				}
			}
			break;
			
			case SEEN_DUAL_LOADS:
				if (seen == INVOKEVIRTUAL) {
					String sig = getSigConstantOperand();
					if (sig.startsWith("()")) {
						propType = sig.substring("()".length());
						if (!"V".equals(propType)) {
							propName = getNameConstantOperand();
							if (propName.startsWith("get")) {
								propName = propName.substring("get".length());
								state = State.SEEN_INVOKEVIRTUAL;
								reset = false;
							}
						}
					}
				}
			break;
			
			case SEEN_INVOKEVIRTUAL:
				if (seen == INVOKEVIRTUAL) {
					String sig = getSigConstantOperand();
					if (sig.equals("(" + propType + ")V")) {
						String name = getNameConstantOperand();
						if (name.startsWith("set")) {
							if (propName.equals(name.substring("set".length()))) {
								bugReporter.reportBug(new BugInstance(this, BugType.SGSU_SUSPICIOUS_GETTER_SETTER_USE.name(), NORMAL_PRIORITY)
																	.addClass(this)
																	.addMethod(this)
																	.addSourceLine(this));
							}
						}
					}
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
}
