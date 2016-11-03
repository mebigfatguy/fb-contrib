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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.FieldAnnotation;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.SourceLineAnnotation;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XFactory;
import edu.umd.cs.findbugs.ba.XField;

/**
 * looks for interfaces that ignore 508 compliance, including not using JLabel.setLabelFor, Using null layouts,
 */
@CustomUserValue
public class Section508Compliance extends BytecodeScanningDetector {

    private enum S508UserValue {
        SAW_TEXT_LABEL, FROM_UIMANAGER, APPENDED_STRING
    };

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
            accessibleClass = Repository.lookupClass("javax/accessibility/Accessible");
        } catch (ClassNotFoundException cnfe) {
            accessibleClass = null;
            clsNFException = cnfe;
        }
    }

    private static final Map<FQMethod, Integer> displayTextMethods = new HashMap<>();

    static {
        String awtDialog = "java/awt/Dialog";
        String awtFrame = "java/awt/Frame";
        String awtGraphics = "java/awt/GraphicsConfiguration";
        String swingIcon = "javax/swing/Icon";
        displayTextMethods.put(new FQMethod("javax/swing/JLabel", Values.CONSTRUCTOR, SignatureBuilder.SIG_STRING_TO_VOID), Values.ZERO);
        displayTextMethods.put(new FQMethod("javax/swing/JLabel", Values.CONSTRUCTOR, new SignatureBuilder().withParamTypes(Values.SLASHED_JAVA_LANG_STRING, swingIcon, Values.SIG_PRIMITIVE_INT).toString()), Values.ONE);
        displayTextMethods.put(new FQMethod("javax/swing/JLabel", Values.CONSTRUCTOR, new SignatureBuilder().withParamTypes(Values.SLASHED_JAVA_LANG_STRING, Values.SIG_PRIMITIVE_INT).toString()), Values.TWO);
        displayTextMethods.put(new FQMethod("javax/swing/JButton", Values.CONSTRUCTOR, SignatureBuilder.SIG_STRING_TO_VOID), Values.ZERO);
        displayTextMethods.put(new FQMethod("javax/swing/JButton", Values.CONSTRUCTOR, new SignatureBuilder().withParamTypes(Values.SLASHED_JAVA_LANG_STRING, swingIcon).toString()), Values.ONE);
        displayTextMethods.put(new FQMethod("javax/swing/JFrame", Values.CONSTRUCTOR, SignatureBuilder.SIG_STRING_TO_VOID), Values.ZERO);
        displayTextMethods.put(new FQMethod("javax/swing/JFrame", Values.CONSTRUCTOR, new SignatureBuilder().withParamTypes(Values.SLASHED_JAVA_LANG_STRING, awtGraphics).toString()), Values.ONE);
        displayTextMethods.put(new FQMethod("javax/swing/JDialog", Values.CONSTRUCTOR, new SignatureBuilder().withParamTypes(awtDialog, Values.SLASHED_JAVA_LANG_STRING).toString()), Values.ZERO);
        displayTextMethods.put(new FQMethod("javax/swing/JDialog", Values.CONSTRUCTOR, new SignatureBuilder().withParamTypes(awtDialog, Values.SLASHED_JAVA_LANG_STRING, Values.SIG_PRIMITIVE_BOOLEAN).toString()), Values.ONE);
        displayTextMethods.put(new FQMethod("javax/swing/JDialog", Values.CONSTRUCTOR, new SignatureBuilder().withParamTypes(awtDialog, Values.SLASHED_JAVA_LANG_STRING, Values.SIG_PRIMITIVE_BOOLEAN, awtGraphics).toString()),
                Values.TWO);
        displayTextMethods.put(new FQMethod("javax/swing/JDialog", Values.CONSTRUCTOR, new SignatureBuilder().withParamTypes(awtFrame, Values.SLASHED_JAVA_LANG_STRING).toString()), Values.ZERO);
        displayTextMethods.put(new FQMethod("javax/swing/JDialog", Values.CONSTRUCTOR, new SignatureBuilder().withParamTypes(awtFrame, Values.SLASHED_JAVA_LANG_STRING, Values.SIG_PRIMITIVE_BOOLEAN).toString()), Values.ONE);
        displayTextMethods.put(new FQMethod("javax/swing/JDialog", Values.CONSTRUCTOR, new SignatureBuilder().withParamTypes(awtFrame, Values.SLASHED_JAVA_LANG_STRING, Values.SIG_PRIMITIVE_BOOLEAN, awtGraphics).toString()),
                Values.TWO);
        displayTextMethods.put(new FQMethod(awtDialog, "setTitle", SignatureBuilder.SIG_STRING_TO_VOID), Values.ZERO);
        displayTextMethods.put(new FQMethod(awtFrame, "setTitle", SignatureBuilder.SIG_STRING_TO_VOID), Values.ZERO);
        displayTextMethods.put(new FQMethod("javax/swing/JMenu", Values.CONSTRUCTOR, SignatureBuilder.SIG_STRING_TO_VOID), Values.ZERO);
        displayTextMethods.put(new FQMethod("javax/swing/JMenu", Values.CONSTRUCTOR, new SignatureBuilder().withParamTypes(Values.SLASHED_JAVA_LANG_STRING, Values.SIG_PRIMITIVE_BOOLEAN).toString()), Values.ONE);
        displayTextMethods.put(new FQMethod("javax/swing/JMenuItem", Values.CONSTRUCTOR, SignatureBuilder.SIG_STRING_TO_VOID), Values.ZERO);
        displayTextMethods.put(new FQMethod("javax/swing/JMenuItem", Values.CONSTRUCTOR, new SignatureBuilder().withParamTypes(Values.SLASHED_JAVA_LANG_STRING, swingIcon).toString()), Values.ONE);
        displayTextMethods.put(new FQMethod("javax/swing/JMenuItem", Values.CONSTRUCTOR, new SignatureBuilder().withParamTypes(Values.SLASHED_JAVA_LANG_STRING, Values.SIG_PRIMITIVE_INT).toString()), Values.ONE);
    }

    private final BugReporter bugReporter;
    private OpcodeStack stack;
    private Set<XField> fieldLabels;
    private Map<Integer, SourceLineAnnotation> localLabels;

    /**
     * constructs a S508C detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public Section508Compliance(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
        if (clsNFException != null) {
            bugReporter.reportMissingClass(clsNFException);
        }
    }

    /**
     * implements the visitor to create and clear the stack
     *
     * @param classContext
     *            the context object of the currently visited class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            if ((jcomponentClass != null) && (accessibleClass != null)) {
                JavaClass cls = classContext.getJavaClass();
                if (cls.instanceOf(jcomponentClass) && !cls.implementationOf(accessibleClass)) {
                    bugReporter.reportBug(new BugInstance(this, BugType.S508C_NON_ACCESSIBLE_JCOMPONENT.name(), NORMAL_PRIORITY).addClass(cls));
                }
            }

            stack = new OpcodeStack();
            fieldLabels = new HashSet<>();
            localLabels = new HashMap<>();
            super.visitClassContext(classContext);
            for (XField fa : fieldLabels) {
                bugReporter.reportBug(new BugInstance(this, BugType.S508C_NO_SETLABELFOR.name(), NORMAL_PRIORITY).addClass(this).addField(fa));
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
     * @param obj
     *            the field object of the current field
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
     * @param obj
     *            the context object for the currently visited code block
     */
    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        localLabels.clear();
        super.visitCode(obj);
        for (SourceLineAnnotation sla : localLabels.values()) {
            BugInstance bug = new BugInstance(this, BugType.S508C_NO_SETLABELFOR.name(), NORMAL_PRIORITY).addClass(this).addMethod(this);

            if (sla != null) {
                bug.addSourceLine(sla);
            }

            bugReporter.reportBug(bug);
        }
    }

    /**
     * implements the visitor to find 508 compliance concerns
     *
     * @param seen
     *            the opcode of the currently parsed instruction
     */
    @Override
    public void sawOpcode(int seen) {
        boolean sawTextLabel = false;
        boolean sawUIManager = false;
        boolean sawAppend = false;
        try {
            stack.precomputation(this);

            if ((seen == ASTORE) || ((seen >= ASTORE_0) && (seen <= ASTORE_3))) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    if ("Ljavax/swing/JLabel;".equals(item.getSignature()) && (S508UserValue.SAW_TEXT_LABEL == item.getUserValue())) {
                        int reg = RegisterUtils.getAStoreReg(this, seen);
                        localLabels.put(Integer.valueOf(reg), SourceLineAnnotation.fromVisitedInstruction(this));
                    }
                }
            } else if (seen == PUTFIELD) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    if (S508UserValue.SAW_TEXT_LABEL != item.getUserValue()) {
                        FieldAnnotation fa = new FieldAnnotation(getDottedClassName(), getNameConstantOperand(), getSigConstantOperand(), false);
                        fieldLabels.remove(XFactory.createXField(fa));
                    }
                }
            } else if (seen == INVOKESPECIAL) {
                String className = getClassConstantOperand();
                String methodName = getNameConstantOperand();
                if ("javax/swing/JLabel".equals(className) && Values.CONSTRUCTOR.equals(methodName)) {
                    String signature = getSigConstantOperand();
                    if (signature.indexOf(Values.SIG_JAVA_LANG_STRING) >= 0) {
                        sawTextLabel = true;
                    }
                }
            } else if (seen == INVOKEVIRTUAL) {
                String className = getClassConstantOperand();
                String methodName = getNameConstantOperand();

                if ("javax/swing/JLabel".equals(className)) {
                    if ("setLabelFor".equals(methodName) && (stack.getStackDepth() > 1)) {
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
                } else if (Values.isAppendableStringClassName(className)) {
                    if ("append".equals(methodName)) {
                        if (stack.getStackDepth() > 0) {
                            OpcodeStack.Item item = stack.getStackItem(0);
                            Object con = item.getConstant();
                            if (con instanceof String) {
                                String literal = (String) con;
                                sawAppend = !literal.startsWith("<");
                            } else {
                                sawAppend = true;
                            }
                        }
                    } else if ("toString".equals(methodName) && (stack.getStackDepth() > 0)) {
                        OpcodeStack.Item item = stack.getStackItem(0);
                        if (S508UserValue.APPENDED_STRING == item.getUserValue()) {
                            sawAppend = true;
                        }
                    }
                }

                processSetSizeOps(methodName);
                processNullLayouts(className, methodName);
                processSetColorOps(methodName);
            } else if ((seen == INVOKESTATIC) && "javax/swing/UIManager".equals(getClassConstantOperand())) {
                sawUIManager = true;
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
                    item.setUserValue(S508UserValue.SAW_TEXT_LABEL);
                }
            } else if (sawUIManager) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    item.setUserValue(S508UserValue.FROM_UIMANAGER);
                }
            } else if (sawAppend && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item item = stack.getStackItem(0);
                item.setUserValue(S508UserValue.APPENDED_STRING);
            }
        }
    }

    /**
     * looks for calls to set a readable string that is generated from a static constant, as these strings are not translatable. also looks for setting readable
     * strings that are appended together. This is likely not to be internationalizable.
     */
    private void processFaultyGuiStrings() {
        FQMethod methodInfo = new FQMethod(getClassConstantOperand(), getNameConstantOperand(), getSigConstantOperand());
        Integer parmIndex = displayTextMethods.get(methodInfo);
        if ((parmIndex != null) && (stack.getStackDepth() > parmIndex.intValue())) {
            OpcodeStack.Item item = stack.getStackItem(parmIndex.intValue());
            if (item.getConstant() != null) {
                bugReporter.reportBug(new BugInstance(this, BugType.S508C_NON_TRANSLATABLE_STRING.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                        .addSourceLine(this));
            } else if (S508UserValue.APPENDED_STRING == item.getUserValue()) {
                bugReporter.reportBug(
                        new BugInstance(this, BugType.S508C_APPENDED_STRING.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));

            }
        }
    }

    /**
     * looks for containers where a null layout is installed
     *
     * @param className
     *            class that a method call is made on
     * @param methodName
     *            name of the method that is called
     */
    private void processNullLayouts(String className, String methodName) {
        if ("java/awt/Container".equals(className) && "setLayout".equals(methodName) && (stack.getStackDepth() > 0) && stack.getStackItem(0).isNull()) {
            bugReporter.reportBug(new BugInstance(this, BugType.S508C_NULL_LAYOUT.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
        }
    }

    /**
     * looks for calls to set the color of components where the color isn't from UIManager
     *
     * @param methodName
     *            the method that is called
     *
     * @throws ClassNotFoundException
     *             if the gui component class can't be found
     */
    private void processSetColorOps(String methodName) throws ClassNotFoundException {
        if ("setBackground".equals(methodName) || "setForeground".equals(methodName)) {
            int argCount = SignatureUtils.getNumParameters(getSigConstantOperand());
            if (stack.getStackDepth() > argCount) {
                OpcodeStack.Item item = stack.getStackItem(0);
                if (S508UserValue.FROM_UIMANAGER != item.getUserValue()) {
                    item = stack.getStackItem(argCount);
                    JavaClass cls = item.getJavaClass();
                    if (((jcomponentClass != null) && cls.instanceOf(jcomponentClass)) || ((componentClass != null) && cls.instanceOf(componentClass))) {
                        bugReporter.reportBug(
                                new BugInstance(this, BugType.S508C_SET_COMP_COLOR.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
                    }
                }
            }
        }
    }

    /**
     * looks for calls to setSize on components, rather than letting the layout manager set them
     *
     * @param methodName
     *            the method that was called on a component
     *
     * @throws ClassNotFoundException
     *             if the gui class wasn't found
     */
    private void processSetSizeOps(String methodName) throws ClassNotFoundException {
        if ("setSize".equals(methodName)) {
            int argCount = SignatureUtils.getNumParameters(getSigConstantOperand());
            if ((windowClass != null) && (stack.getStackDepth() > argCount)) {
                OpcodeStack.Item item = stack.getStackItem(argCount);
                JavaClass cls = item.getJavaClass();
                if ((cls != null) && cls.instanceOf(windowClass)) {
                    bugReporter.reportBug(
                            new BugInstance(this, BugType.S508C_NO_SETSIZE.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
                }
            }
        }
    }
}
