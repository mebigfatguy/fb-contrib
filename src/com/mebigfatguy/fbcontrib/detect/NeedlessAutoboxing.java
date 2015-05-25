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

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.bcel.OpcodeStackDetector;

/**
 * Looks for methods that pass a primitive wrapper class object, to the
 * same classes Constructor.
 */
public class NeedlessAutoboxing extends OpcodeStackDetector
{
	enum State {SEEN_NOTHING, SEEN_VALUE, SEEN_VALUEOFSTRING, SEEN_PARSE, SEEN_CTOR, SEEN_VALUEOFPRIMITIVE, SEEN_ICONST, SEEN_GETSTATIC}
	
	private static final Map<String, String[]> boxClasses = new HashMap<String, String[]>();
	static {
		boxClasses.put("java/lang/Boolean", new String[] { "booleanValue()Z", "(Z)V", "(Z)Ljava/lang/Boolean;" });
		boxClasses.put("java/lang/Character", new String[] { "charValue()C", "(C)V", "(C)Ljava/lang/Character;" });
		boxClasses.put("java/lang/Byte", new String[] { "byteValue()B", "(B)V", "(B)Ljava/lang/Byte;" });
		boxClasses.put("java/lang/Short", new String[] { "shortValue()S", "(S)V", "(S)Ljava/lang/Short;" });
		boxClasses.put("java/lang/Integer", new String[] { "intValue()I", "(I)V", "(I)Ljava/lang/Integer;" });
		boxClasses.put("java/lang/Long", new String[] { "longValue()J", "(J)V", "(J)Ljava/lang/Long;" });
		boxClasses.put("java/lang/Float", new String[] { "floatValue()F", "(F)V", "(F)Ljava/lang/Float;" });
		boxClasses.put("java/lang/Double", new String[] { "doubleValue()D", "(D)V", "(D)Ljava/lang/Double;" });
	}
	
	private static final Map<String, String> parseClasses = new HashMap<String, String>();
	static {
		parseClasses.put("java/lang/Boolean", "parseBoolean(Ljava/lang/String;)Z");
		parseClasses.put("java/lang/Byte", "parseByte(Ljava/lang/String;)B");
		parseClasses.put("java/lang/Short",  "parseShort(Ljava/lang/String;)S");
		parseClasses.put("java/lang/Integer", "parseInt(Ljava/lang/String;)I");
		parseClasses.put("java/lang/Long", "parseLong(Ljava/lang/String;)J");
		parseClasses.put("java/lang/Float", "parseFloat(Ljava/lang/String;)F");
		parseClasses.put("java/lang/Double", "parseDouble(Ljava/lang/String;)D");
	}
	
	private BugReporter bugReporter;
	private State state;
	private String boxClass;
	private BitSet ternaryPCs;
	
	/**
     * constructs a NAB detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
	 */
	public NeedlessAutoboxing(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}
	
	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			ternaryPCs = new BitSet();
			super.visitClassContext(classContext);
		} finally {
			ternaryPCs = null;
		}
	}
	@Override
	public void visitMethod(Method obj) {
		state = State.SEEN_NOTHING;
		ternaryPCs.clear();
	}
	
	@Override
	public void sawOpcode(int seen) {
		
		if (ternaryPCs.get(getPC())) {
			ternaryPCs.clear(getPC());
			state = State.SEEN_NOTHING;
			return;
		}
		
		switch (state) {
			case SEEN_NOTHING:
				if (seen == INVOKEVIRTUAL) {
					boxClass = getClassConstantOperand();
					String[] boxSigs = boxClasses.get(boxClass);
					if (boxSigs != null) {
						String methodInfo = getNameConstantOperand() + getSigConstantOperand();
						if (boxSigs[0].equals(methodInfo)) {
							state = State.SEEN_VALUE;
						}
					}
				} else if (seen == INVOKESTATIC) {
					boxClass = getClassConstantOperand();
					String[] boxSigs = boxClasses.get(boxClass);
					if (boxSigs != null) {
						if ("valueOf".equals(getNameConstantOperand())) {
							String sig = getSigConstantOperand();
							if (sig.startsWith("(Ljava/lang/String;)")) {
								if (!"java/lang/Boolean".equals(boxClass) || (getClassContext().getJavaClass().getMajor() >= Constants.MAJOR_1_5))
									state = State.SEEN_VALUEOFSTRING;
							} else if (!sig.startsWith("(Ljava/lang/String;")) {
								state = State.SEEN_VALUEOFPRIMITIVE;
							}
						} else {
							String parseSig = parseClasses.get(boxClass);
							if (parseSig != null) {
								String methodInfo = getNameConstantOperand() + getSigConstantOperand();
								if (parseSig.equals(methodInfo)) {
									state = State.SEEN_PARSE;
								}
							}
						}
					}
				} else if (seen == INVOKESPECIAL) {
					boxClass = getClassConstantOperand();
					String[] boxSigs = boxClasses.get(boxClass);
					if (boxSigs != null) {
						if (Values.CONSTRUCTOR.equals(getNameConstantOperand()) && boxSigs[1].equals(getSigConstantOperand())) {
							state = State.SEEN_CTOR;
						}
					}
				} else if ((seen == ICONST_0) || (seen == ICONST_1)) {
					if (state == State.SEEN_NOTHING)
						state = State.SEEN_ICONST;
				} else if (seen == GETSTATIC) {
					String clsName = getClassConstantOperand();
					if ("java/lang/Boolean".equals(clsName)) {
						String fldName = getNameConstantOperand();
						if ("TRUE".equals(fldName) || "FALSE".equals(fldName))
							state = State.SEEN_GETSTATIC;
					}
				} else if ((seen == GOTO) || (seen == GOTO_W)) {
					if (stack.getStackDepth() > 0) {
						ternaryPCs.set(getBranchTarget());
					}
					state = State.SEEN_NOTHING;
				}
			break;
			
			case SEEN_VALUE:
				if (seen == INVOKESPECIAL) {
					if (boxClass.equals(getClassConstantOperand())) {
						String methodName = getNameConstantOperand();
						String boxSig = boxClasses.get(boxClass)[1];
						if (Values.CONSTRUCTOR.equals(methodName)) {
							String methodSig = getSigConstantOperand();
							if (boxSig.equals(methodSig)) {
								bugReporter.reportBug(new BugInstance(this, BugType.NAB_NEEDLESS_AUTOBOXING_CTOR.name(), NORMAL_PRIORITY)
									.addClass(this)
									.addMethod(this)
									.addSourceLine(this));
							}
						}
					}
				} else if (seen == INVOKESTATIC) {
					if (boxClass.equals(getClassConstantOperand())) {
						String methodName = getNameConstantOperand();
						String boxSig = boxClasses.get(boxClass)[2];
						if ("valueOf".equals(methodName)) {
							String methodSig = getSigConstantOperand();
							if (boxSig.equals(methodSig)) {
								bugReporter.reportBug(new BugInstance(this, BugType.NAB_NEEDLESS_AUTOBOXING_VALUEOF.name(), NORMAL_PRIORITY)
									.addClass(this)
									.addMethod(this)
									.addSourceLine(this));
							}
						}
					}
				}
				state = State.SEEN_NOTHING;
			break;
			
			case SEEN_CTOR:
			case SEEN_VALUEOFPRIMITIVE:
				if (seen == INVOKEVIRTUAL) {
					String[] boxSigs = boxClasses.get(boxClass);
					if (boxSigs[0].equals(getNameConstantOperand() + getSigConstantOperand())) {
						bugReporter.reportBug(new BugInstance(this, BugType.NAB_NEEDLESS_BOX_TO_UNBOX.name(), NORMAL_PRIORITY)
								   .addClass(this)
								   .addMethod(this)
								   .addSourceLine(this));
					} else if (getSigConstantOperand().startsWith("()") && getNameConstantOperand().endsWith("Value")) {
						bugReporter.reportBug(new BugInstance(this, BugType.NAB_NEEDLESS_BOX_TO_CAST.name(), NORMAL_PRIORITY)
						   .addClass(this)
						   .addMethod(this)
						   .addSourceLine(this));
					}
				}
				state = State.SEEN_NOTHING;			
			break;			
			
			case SEEN_VALUEOFSTRING:
				if (seen == INVOKEVIRTUAL) {
					String[] boxSigs = boxClasses.get(boxClass);
					if (boxSigs[0].equals(getNameConstantOperand() + getSigConstantOperand())) {
						bugReporter.reportBug(new BugInstance(this, BugType.NAB_NEEDLESS_BOXING_PARSE.name(), NORMAL_PRIORITY)
								   .addClass(this)
								   .addMethod(this)
								   .addSourceLine(this));
					}
				}
				state = State.SEEN_NOTHING;
			break;
			
			case SEEN_PARSE:
				if (seen == INVOKESTATIC) {
					if (boxClass.equals(getClassConstantOperand())) {
						if ("valueOf".equals(getNameConstantOperand())) {
							bugReporter.reportBug(new BugInstance(this, BugType.NAB_NEEDLESS_BOXING_VALUEOF.name(), NORMAL_PRIORITY)
									   .addClass(this)
									   .addMethod(this)
									   .addSourceLine(this));
						}
					}
				} else if (seen == INVOKESPECIAL) {
					if (Values.CONSTRUCTOR.equals(getNameConstantOperand()) && (boxClass.equals(getClassConstantOperand()))) {
						bugReporter.reportBug(new BugInstance(this, BugType.NAB_NEEDLESS_BOXING_STRING_CTOR.name(), NORMAL_PRIORITY)
								   .addClass(this)
								   .addMethod(this)
								   .addSourceLine(this));
					}
				}
				state = State.SEEN_NOTHING;
			break;
			
			case SEEN_ICONST:
				if (seen == INVOKESTATIC) {
					if (("java/lang/Boolean".equals(getClassConstantOperand())
					&&  ("valueOf".equals(getNameConstantOperand())
					&&  ("(Z)Ljava/lang/Boolean;".equals(getSigConstantOperand()))))) {
						bugReporter.reportBug(new BugInstance(this, BugType.NAB_NEEDLESS_BOOLEAN_CONSTANT_CONVERSION.name(), NORMAL_PRIORITY)
									.addClass(this)
									.addMethod(this)
									.addSourceLine(this));
					}
				}
				state = State.SEEN_NOTHING;
				sawOpcode(seen);
			break;
			
			case SEEN_GETSTATIC:
				if (seen == INVOKEVIRTUAL) {
					if (("java/lang/Boolean".equals(getClassConstantOperand())
					&&  ("booleanValue".equals(getNameConstantOperand())
					&&  ("()Z".equals(getSigConstantOperand()))))) {
						bugReporter.reportBug(new BugInstance(this, BugType.NAB_NEEDLESS_BOOLEAN_CONSTANT_CONVERSION.name(), NORMAL_PRIORITY)
									.addClass(this)
									.addMethod(this)
									.addSourceLine(this));
					}
				}
				state = State.SEEN_NOTHING;
				sawOpcode(seen);
			break;
		}
	}
}
