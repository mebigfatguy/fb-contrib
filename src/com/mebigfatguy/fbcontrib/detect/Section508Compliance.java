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

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.FieldAnnotation;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.SourceLineAnnotation;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XFactory;
import edu.umd.cs.findbugs.ba.XField;

/**
 * looks for interfaces that ignore 508 compliance, including not using JLabel.setLabelFor,
 * Using null layouts,
 */
public class Section508Compliance extends BytecodeScanningDetector
{
    private static final String SAW_TEXT_LABEL = "SAW_TEXT_LABEL";
    private static final String FROM_UIMANAGER = "FROM_UIMANAGER";
    private static final String APPENDED_STRING = "APPENDED_STRING";

	private static JavaClass windowClass;
	private static JavaClass componentClass;
	private static JavaClass jcomponentClass;
	private static JavaClass accessibleClass;
	private static ClassNotFoundException clsNFException;
	static {
		try {
			windowClass = Repository.lookupClass("java/awt/Window");
		} catch (ClassNotFoundException cnfe) {
			windowClass = null;
			clsNFException = cnfe;
		}
		try {
			componentClass = Repository.lookupClass("java/awt/Component");
		} catch (ClassNotFoundException cnfe) {
			componentClass = null;
			clsNFException = cnfe;
		}
		try {
			jcomponentClass = Repository.lookupClass("javax/swing/JComponent");
		} catch (ClassNotFoundException cnfe) {
			jcomponentClass = null;
			clsNFException = cnfe;
		}
		try {
			accessibleClass = Repository.lookupClass("javax.accessibility.Accessible");
		} catch (ClassNotFoundException cnfe) {
			accessibleClass = null;
			clsNFException = cnfe;
		}
	}

	private static final Map<String, Integer> displayTextMethods = new HashMap<String, Integer>();
	static {
	    Integer zero = Integer.valueOf(0);
	    Integer one = Integer.valueOf(1);
	    Integer two = Integer.valueOf(2);
	    
		displayTextMethods.put("javax/swing/JLabel#<init>(Ljava/lang/String;)", zero);
		displayTextMethods.put("javax/swing/JLabel#<init>(Ljava/lang/String;Ljavax/swing/Icon;I)", one);
		displayTextMethods.put("javax/swing/JLabel#<init>(Ljava/lang/String;I)", two);
		displayTextMethods.put("javax/swing/JButton#<init>(Ljava/lang/String;)", zero);
		displayTextMethods.put("javax/swing/JButton#<init>(Ljava/lang/String;Ljavax/swing/Icon;)", one);
		displayTextMethods.put("javax/swing/JFrame#<init>(Ljava/lang/String;)", zero);
		displayTextMethods.put("javax/swing/JFrame#<init>(Ljava/lang/String;Ljava/awt/GraphicsConfiguration;)", one);
		displayTextMethods.put("javax/swing/JDialog#<init>(Ljava/awt/Dialog;Ljava/lang/String;)", zero);
		displayTextMethods.put("javax/swing/JDialog#<init>(Ljava/awt/Dialog;Ljava/lang/String;Z)", one);
		displayTextMethods.put("javax/swing/JDialog#<init>(Ljava/awt/Dialog;Ljava/lang/String;ZLjava/awt/GraphicsConfiguration;)", two);
		displayTextMethods.put("javax/swing/JDialog#<init>(Ljava/awt/Frame;Ljava/lang/String;)", zero);
		displayTextMethods.put("javax/swing/JDialog#<init>(Ljava/awt/Frame;Ljava/lang/String;Z)", one);
		displayTextMethods.put("javax/swing/JDialog#<init>(Ljava/awt/Frame;Ljava/lang/String;ZLjava/awt/GraphicsConfiguration;)", two);
		displayTextMethods.put("java/awt/Dialog#setTitle(Ljava/lang/String;)", zero);
		displayTextMethods.put("java/awt/Frame#setTitle(Ljava/lang/String;)", zero);
		displayTextMethods.put("javax/swing/JMenu#<init>(Ljava/lang/String;)", zero);
		displayTextMethods.put("javax/swing/JMenu#<init>(Ljava/lang/String;Z)", one);
		displayTextMethods.put("javax/swing/JMenuItem#<init>(Ljava/lang/String;)", zero);
		displayTextMethods.put("javax/swing/JMenuItem#<init>(Ljava/lang/String;Ljavax/swing/Icon;)", one);
		displayTextMethods.put("javax/swing/JMenuItem#<init>(Ljava/lang/String;I)", one);
	}

	private final BugReporter bugReporter;
	private OpcodeStack stack;
	private Set<XField> fieldLabels;
	private Map<Integer, SourceLineAnnotation> localLabels;

	/**
	 * constructs a S508C detector given the reporter to report bugs on
	 * @param bugReporter the sync of bug reports
	 */
	public Section508Compliance(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
		if (clsNFException != null)
			bugReporter.reportMissingClass(clsNFException);
	}

	/**
	 * implements the visitor to create and clear the stack
	 *
	 * @param classContext the context object of the currently visited class
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			if ((jcomponentClass != null) && (accessibleClass != null)) {
				JavaClass cls = classContext.getJavaClass();
				if (cls.instanceOf(jcomponentClass)) {
					if (!cls.implementationOf(accessibleClass)) {
						bugReporter.reportBug(new BugInstance(this, "S508C_NON_ACCESSIBLE_JCOMPONENT", NORMAL_PRIORITY)
						.addClass(cls));
					}
				}
			}

			stack = new OpcodeStack();
			fieldLabels = new HashSet<XField>();
			localLabels = new HashMap<Integer, SourceLineAnnotation>();
			super.visitClassContext(classContext);
			for (XField fa : fieldLabels) {
				bugReporter.reportBug(new BugInstance(this, "S508C_NO_SETLABELFOR", NORMAL_PRIORITY)
				.addClass(this)
				.addField(fa));
			}
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
		} finally {
			stack = null;
			fieldLabels = null;
			localLabels = null;
		}
	}

	/**
	 * looks for fields that are JLabels and stores them in a set
	 *
	 * @param obj the field object of the current field
	 */
	@Override
	public void visitField(Field obj) {
		String fieldSig = obj.getSignature();
		if ("Ljavax/swing/JLabel;".equals(fieldSig)) {
			FieldAnnotation fa = FieldAnnotation.fromVisitedField(this);

			fieldLabels.add(XFactory.createXField(fa));
		}
	}

	/**
	 * implements the visitor to reset the stack
	 *
	 * @param obj the context object for the currently visited code block
	 */
	@Override
	public void visitCode(Code obj) {
		stack.resetForMethodEntry(this);
		localLabels.clear();
		super.visitCode(obj);
		for (SourceLineAnnotation sla : localLabels.values()) {
			BugInstance bug = new BugInstance(this, "S508C_NO_SETLABELFOR", NORMAL_PRIORITY)
			.addClass(this)
			.addMethod(this);

			if (sla != null) {
				bug.addSourceLine(sla);
			}

			bugReporter.reportBug(bug);
		}
	}

	/**
	 * implements the visitor to find 508 compliance concerns
	 *
	 * @param seen the opcode of the currently parsed instruction
	 */
	@Override
	public void sawOpcode(int seen) {
		boolean sawTextLabel = false;
		boolean sawUIManager = false;
		boolean sawAppend = false;
		try {
			stack.mergeJumps(this);
			if ((seen == ASTORE) || ((seen >= ASTORE_0) && (seen <= ASTORE_3))) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item item = stack.getStackItem(0);
					if ("Ljavax/swing/JLabel;".equals(item.getSignature())
							&&  (SAW_TEXT_LABEL.equals(item.getUserValue()))) {
						int reg = RegisterUtils.getAStoreReg(this, seen);
						localLabels.put(Integer.valueOf(reg), SourceLineAnnotation.fromVisitedInstruction(this));
					}
				}
			} else if (seen == PUTFIELD) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item item = stack.getStackItem(0);
					if (!SAW_TEXT_LABEL.equals(item.getUserValue())) {
						FieldAnnotation fa = new FieldAnnotation(getDottedClassName(), getNameConstantOperand(), getSigConstantOperand(), false);
						fieldLabels.remove(XFactory.createXField(fa));
					}
				}
			} else if (seen == INVOKESPECIAL) {
				String className = getClassConstantOperand();
				String methodName = getNameConstantOperand();
				if ("javax/swing/JLabel".equals(className)
						&&  "<init>".equals(methodName)) {
					String signature = getSigConstantOperand();
					if (signature.indexOf("Ljava/lang/String;") >= 0) {
						sawTextLabel = true;
					}
				}
			} else if (seen == INVOKEVIRTUAL) {
				String className = getClassConstantOperand();
				String methodName = getNameConstantOperand();

				if ("javax/swing/JLabel".equals(className)) {
					if ("setLabelFor".equals(methodName)) {
						if (stack.getStackDepth() > 1) {
							OpcodeStack.Item item = stack.getStackItem(1);
							XField field = item.getXField();
							if (field != null) {
								fieldLabels.remove(field);
							} else {
								int reg = item.getRegisterNumber();
								if (reg >= 0) {
									localLabels.remove(Integer.valueOf(reg));
								}
							}
						}
					}
				} else if ("java/lang/StringBuffer".equals(className) || "java/lang/StringBuilder".equals(className)) {
				    if ("append".equals(methodName)) {
				        if (stack.getStackDepth() > 0) {
				            OpcodeStack.Item item = stack.getStackItem(0);
				            Object con = item.getConstant();
				            if (con instanceof String) {
				                String literal = (String)con;
				                sawAppend = !literal.startsWith("<");
				            } else {
				                sawAppend = true;
				            }
				        }
				    } else if ("toString".equals(methodName)) {
				        if (stack.getStackDepth() > 0) {
				            OpcodeStack.Item item = stack.getStackItem(0);
				            if (APPENDED_STRING.equals(item.getUserValue())) {
				                sawAppend = true;
				            }
				        }
				    }
				}

				processSetSizeOps(methodName);
                processNullLayouts(className, methodName);
				processSetColorOps(methodName);
			} else if (seen == INVOKESTATIC) {
			    if ("javax/swing/UIManager".equals(getClassConstantOperand())) {
			        sawUIManager = true;
			    }
			}

			if ((seen == INVOKEVIRTUAL) || (seen == INVOKESPECIAL) || (seen == INVOKEINTERFACE)) {
			    processFaultyGuiStrings();
			}
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
		} finally {
			TernaryPatcher.pre(stack, seen);
			stack.sawOpcode(this, seen);
			TernaryPatcher.post(stack, seen);
			if (sawTextLabel) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item item = stack.getStackItem(0);
					item.setUserValue(SAW_TEXT_LABEL);
				}
			} else if (sawUIManager) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    item.setUserValue(FROM_UIMANAGER);
                }
			} else if (sawAppend) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    item.setUserValue(APPENDED_STRING);
                }
			}
		}
	}

	/**
	 * looks for calls to set a readable string that is generated from a static constant, as these strings
	 * are not translatable. also looks for setting readable strings that are appended together. This is
	 * likely not to be internationalizable.
	 */
	private void processFaultyGuiStrings() {
        StringBuilder methodInfo = new StringBuilder();
        methodInfo.append(getClassConstantOperand());
        methodInfo.append("#");
        methodInfo.append(getNameConstantOperand());
        String signature = getSigConstantOperand();
        signature = signature.substring(0, signature.indexOf(')') + 1);
        methodInfo.append(signature);
        Integer parmIndex = displayTextMethods.get(methodInfo.toString());
        if (parmIndex != null) {
            if (stack.getStackDepth() > parmIndex.intValue()) {
                OpcodeStack.Item item = stack.getStackItem(parmIndex.intValue());
                if (item.getConstant() != null) {
                    bugReporter.reportBug(new BugInstance(this, "S508C_NON_TRANSLATABLE_STRING", NORMAL_PRIORITY)
                                .addClass(this)
                                .addMethod(this)
                                .addSourceLine(this));
                } else if (APPENDED_STRING.equals(item.getUserValue())) {
                    bugReporter.reportBug(new BugInstance(this, "S508C_APPENDED_STRING", NORMAL_PRIORITY)
                    .addClass(this)
                    .addMethod(this)
                    .addSourceLine(this));

                }
            }
        }
	}

	/**
	 * looks for containers where a null layout is installed
	 *
	 * @param className class that a method call is made on
	 * @param methodName name of the method that is called
	 */
	private void processNullLayouts(String className, String methodName) {
        if ("java/awt/Container".equals(className)) {
            if ("setLayout".equals(methodName)) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    if (item.isNull()) {
                        bugReporter.reportBug(new BugInstance(this, "S508C_NULL_LAYOUT", NORMAL_PRIORITY)
                        .addClass(this)
                        .addMethod(this)
                        .addSourceLine(this));
                    }
                }
            }
        }
	}

	/**
	 * looks for calls to set the color of components where the color isn't from UIManager
	 *
	 * @param methodName the method that is called
	 *
	 * @throws ClassNotFoundException if the gui component class can't be found
	 */
	private void processSetColorOps(String methodName) throws ClassNotFoundException {
        if ("setBackground".equals(methodName)
                ||  "setForeground".equals(methodName)) {
            int argCount = Type.getArgumentTypes(getSigConstantOperand()).length;
            if (stack.getStackDepth() > argCount) {
                OpcodeStack.Item item = stack.getStackItem(0);
                if (!FROM_UIMANAGER.equals(item.getUserValue())) {
                    item = stack.getStackItem(argCount);
                    JavaClass cls = item.getJavaClass();
                    if (((jcomponentClass != null) && cls.instanceOf(jcomponentClass))
                            ||  ((componentClass != null) && cls.instanceOf(componentClass))) {
                        bugReporter.reportBug(new BugInstance(this, "S508C_SET_COMP_COLOR", NORMAL_PRIORITY)
                        .addClass(this)
                        .addMethod(this)
                        .addSourceLine(this));
                    }
                }
            }
        }
	}

	/**
	 * looks for calls to setSize on components, rather than letting the layout manager set them
	 *
	 * @param methodName the method that was called on a component
	 *
	 * @throws ClassNotFoundException if the gui class wasn't found
	 */
	private void processSetSizeOps(String methodName) throws ClassNotFoundException {
	    if ("setSize".equals(methodName)) {
            int argCount = Type.getArgumentTypes(getSigConstantOperand()).length;
            if ((windowClass != null) && (stack.getStackDepth() > argCount)) {
                OpcodeStack.Item item = stack.getStackItem(argCount);
                JavaClass cls = item.getJavaClass();
                if ((cls != null) && cls.instanceOf(windowClass)) {
                    bugReporter.reportBug(new BugInstance(this, "S508C_NO_SETSIZE", NORMAL_PRIORITY)
                    .addClass(this)
                    .addMethod(this)
                    .addSourceLine(this));
                }
            }
        }
	}
}
