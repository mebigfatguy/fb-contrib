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

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.Attribute;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.ConstantUtf8;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.classfile.RuntimeVisibleAnnotations;
import org.apache.bcel.classfile.Unknown;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.classfile.DescriptorFactory;

/** looks for odd uses of the Assert class of the JUnit framework */
public class JUnitAssertionOddities extends BytecodeScanningDetector
{
    private enum State {SAW_NOTHING, SAW_IF_ICMPNE, SAW_ICONST_1, SAW_GOTO, SAW_ICONST_0, SAW_EQUALS};
    
	private static final String RUNTIME_VISIBLE_ANNOTATIONS = "RuntimeVisibleAnnotations";
	private static final String TESTCASE_CLASS = "junit.framework.TestCase";
	private static final String TEST_CLASS = "org.junit.Test";
	private static final String TEST_ANNOTATION_SIGNATURE = "Lorg/junit/Test;";
	private static final String OLD_ASSERT_CLASS = "junit/framework/Assert";
	private static final String NEW_ASSERT_CLASS = "org/junit/Assert";
	
	private BugReporter bugReporter;
	private JavaClass testCaseClass;
	private JavaClass testAnnotationClass;
	private OpcodeStack stack;
	private boolean isTestCaseDerived;
	private boolean isAnnotationCapable;
	private State state;

	/**
     * constructs a JOA detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
	 */
	public JUnitAssertionOddities(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
		
      try {
            testCaseClass = Repository.lookupClass(TESTCASE_CLASS);
        } catch (ClassNotFoundException cnfe) {
            testCaseClass = null;
            bugReporter.reportMissingClass(DescriptorFactory.createClassDescriptor(TESTCASE_CLASS.replaceAll("\\.",  "/")));
        }
        try {
            testAnnotationClass = Repository.lookupClass(TEST_CLASS);
        } catch (ClassNotFoundException cnfe) {
            testAnnotationClass = null;
            bugReporter.reportMissingClass(DescriptorFactory.createClassDescriptor(TEST_CLASS.replaceAll("\\.",  "/")));
        }
	}

	/**
	 * override the visitor to see if this class could be a test class
	 *
	 * @param classContext the context object of the currently parsed class
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			JavaClass cls = classContext.getJavaClass();
			isTestCaseDerived = ((testCaseClass != null) && cls.instanceOf(testCaseClass));
			isAnnotationCapable = (cls.getMajor() >= 5) && (testAnnotationClass != null);
			if (isTestCaseDerived || isAnnotationCapable) {
				stack = new OpcodeStack();
				super.visitClassContext(classContext);
			}
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
		} finally {
			stack = null;
		}
	}

	@Override
	public void visitCode(Code obj) {
		Method m = getMethod();
		boolean isTestMethod = isTestCaseDerived && m.getName().startsWith("test");

		if (!isTestMethod && isAnnotationCapable) {
			Attribute[] atts = m.getAttributes();
			for (Attribute att : atts) {
				ConstantPool cp = att.getConstantPool();
				Constant c = cp.getConstant(att.getNameIndex());
				if (c instanceof ConstantUtf8) {
					String name = ((ConstantUtf8) c).getBytes();
					if (RUNTIME_VISIBLE_ANNOTATIONS.equals(name)) {
						if (att instanceof Unknown) {
							Unknown unAtt = (Unknown)att;
							byte[] bytes = unAtt.getBytes();
							int constantPoolIndex = bytes[3] & 0x000000FF;
							c = cp.getConstant(constantPoolIndex);
							if (c instanceof ConstantUtf8) {
								name = ((ConstantUtf8) c).getBytes();
								if (TEST_ANNOTATION_SIGNATURE.equals(name)) {
									isTestMethod = true;
									break;
								}
							}
						} else if (att instanceof RuntimeVisibleAnnotations) {
						    RuntimeVisibleAnnotations rva = (RuntimeVisibleAnnotations) att;
						    
						    AnnotationEntry[] entries = rva.getAnnotationEntries();
						    for (AnnotationEntry entry : entries) {
						        if (TEST_ANNOTATION_SIGNATURE.equals(entry.getAnnotationType())) {
						            isTestMethod = true;
						            break;
						        }
						    }
						}
					}
				}
			}
		}

		if (isTestMethod) {
			stack.resetForMethodEntry(this);
			state = State.SAW_NOTHING;
			super.visitCode(obj);
		}
	}

	@Override
	public void sawOpcode(int seen) {
		String userValue = null;

		try {
			stack.mergeJumps(this);

			if (seen == INVOKESTATIC) {
				String clsName = getClassConstantOperand();
				if (OLD_ASSERT_CLASS.equals(clsName) || NEW_ASSERT_CLASS.equals(clsName)) {
					String methodName = getNameConstantOperand();
					if ("assertEquals".equals(methodName)) {
						String signature = getSigConstantOperand();
						Type[] argTypes = Type.getArgumentTypes(signature);
						if (argTypes.length == 2) {
    						if (argTypes[0].equals(Type.STRING) && argTypes[1].equals(Type.STRING))
    							return;

    						if (stack.getStackDepth() >= 2) {
    							OpcodeStack.Item item1 = stack.getStackItem(1);
    							Object cons1 = item1.getConstant();
    							if ((cons1 != null) && (argTypes[argTypes.length-1].equals(Type.BOOLEAN)) && (argTypes[argTypes.length-2].equals(Type.BOOLEAN))) {
    								bugReporter.reportBug(new BugInstance(this, "JAO_JUNIT_ASSERTION_ODDITIES_BOOLEAN_ASSERT", NORMAL_PRIORITY)
    								   .addClass(this)
    								   .addMethod(this)
    								   .addSourceLine(this));
    								return;
    							}
    							OpcodeStack.Item item0 = stack.getStackItem(0);
    							if (item0.getConstant() != null) {
    								bugReporter.reportBug(new BugInstance(this, "JAO_JUNIT_ASSERTION_ODDITIES_ACTUAL_CONSTANT", NORMAL_PRIORITY)
    										   .addClass(this)
    										   .addMethod(this)
    										   .addSourceLine(this));
    								return;
    							}
    							if (argTypes[0].equals(Type.OBJECT) && argTypes[1].equals(Type.OBJECT)) {
    								if ("Ljava/lang/Double;".equals(item0.getSignature()) && "Ljava/lang/Double;".equals(item1.getSignature())) {
    									bugReporter.reportBug(new BugInstance(this, "JAO_JUNIT_ASSERTION_ODDITIES_INEXACT_DOUBLE", NORMAL_PRIORITY)
    									   .addClass(this)
    									   .addMethod(this)
    									   .addSourceLine(this));
    									return;
    								}
    							}
    						}
						}
					} else if ("assertNotNull".equals(methodName)) {
						if (stack.getStackDepth() > 0) {
							if ("valueOf".equals(stack.getStackItem(0).getUserValue())) {
								bugReporter.reportBug(new BugInstance(this, "JAO_JUNIT_ASSERTION_ODDITIES_IMPOSSIBLE_NULL", NORMAL_PRIORITY)
										   .addClass(this)
										   .addMethod(this)
										   .addSourceLine(this));
							}
						}
					} else if ("assertTrue".equals(methodName)) {
					    if ((state == State.SAW_ICONST_0) || (state == State.SAW_EQUALS)) {
					        bugReporter.reportBug(new BugInstance(this, "JAO_JUNIT_ASSERTION_ODDITIES_USE_ASSERT_EQUALS", NORMAL_PRIORITY)
					                        .addClass(this)
					                        .addMethod(this)
					                        .addSourceLine(this));
					    }
					}
				} else {
					String methodName = getNameConstantOperand();
					String sig = getSigConstantOperand();
					if (clsName.startsWith("java/lang/")
					&&  "valueOf".equals(methodName)
					&&  (sig.indexOf(")Ljava/lang/") >= 0)) {
						userValue = "valueOf";
					}
				}
			} else if (seen == ATHROW) {
			    if (stack.getStackDepth() > 0) {
			        OpcodeStack.Item item = stack.getStackItem(0);
    			    String throwClass = item.getSignature();
    			    if ("Ljava/lang/AssertionError;".equals(throwClass)) {
    			        bugReporter.reportBug(new BugInstance(this, "JAO_JUNIT_ASSERTION_ODDITIES_ASSERT_USED", NORMAL_PRIORITY)
    			                                .addClass(this)
    			                                .addMethod(this)
    			                                .addSourceLine(this));
    			    }
			    }
			}
			
			switch (state) {
			case SAW_NOTHING:
			case SAW_EQUALS:
			    if (seen == IF_ICMPNE)
			        state = State.SAW_IF_ICMPNE;
			    else
			        state = State.SAW_NOTHING;
			    break;
			
			case SAW_IF_ICMPNE:
			    if (seen == ICONST_1)
			        state = State.SAW_ICONST_1;
			    else
			        state = State.SAW_NOTHING;
			    break;
			    
			case SAW_ICONST_1:
			    if (seen == GOTO)
			        state = State.SAW_GOTO;
			    else
			        state = State.SAW_NOTHING;
			            break;
			    
			case SAW_GOTO:
			    if (seen == ICONST_0)
			        state = State.SAW_ICONST_0;
		        else
		            state = State.SAW_NOTHING;
		        break;
			    
			    default:
			        state = State.SAW_NOTHING;
			    break;
			}
			
			if (seen == INVOKEVIRTUAL) {
			    String methodName = getNameConstantOperand();
			    String sig = getSigConstantOperand();
			    if ("equals".equals(methodName) && "(Ljava/lang/Object;)Z".equals(sig)) {
			        state = State.SAW_EQUALS;
			    }
			}
			
			
		} finally {
			TernaryPatcher.pre(stack, seen);
			stack.sawOpcode(this, seen);
			TernaryPatcher.post(stack, seen);
			if ((userValue != null) && (stack.getStackDepth() > 0)) {
				OpcodeStack.Item item = stack.getStackItem(0);
				item.setUserValue(userValue);
			}
		}
	}
}
