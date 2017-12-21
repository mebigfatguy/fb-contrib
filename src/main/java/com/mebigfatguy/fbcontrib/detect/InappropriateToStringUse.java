/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2017 Dave Brosius
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
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XMethod;

/**
 * looks for methods that rely on the format of the string fetched from another object's toString method, when that method appears not to be owned by the author
 * of the calling method. As the implementation of toString() is often considered a private implementation detail of a class, and not something that should be
 * relied on, depending on it's format is dangerous.
 */
@CustomUserValue
public class InappropriateToStringUse extends BytecodeScanningDetector {

    private static final Set<String> validToStringClasses = UnmodifiableSet.create(
    // @formatter:off
            Values.SLASHED_JAVA_LANG_OBJECT, // too many fps
            Values.SLASHED_JAVA_LANG_BYTE,
            Values.SLASHED_JAVA_LANG_CHARACTER,
            Values.SLASHED_JAVA_LANG_SHORT,
            Values.SLASHED_JAVA_LANG_INTEGER,
            Values.SLASHED_JAVA_LANG_BOOLEAN,
            Values.SLASHED_JAVA_LANG_FLOAT,
            Values.SLASHED_JAVA_LANG_DOUBLE,
            Values.SLASHED_JAVA_LANG_LONG,
            Values.SLASHED_JAVA_LANG_STRING,
            "java/lang/Number",
            Values.SLASHED_JAVA_LANG_STRINGBUFFER,
            Values.SLASHED_JAVA_LANG_STRINGBUILDER,
            "java/io/StringWriter"
            // @formatter:on
    );

    private static final Set<String> stringAlgoMethods = UnmodifiableSet.create("indexOf", "contains", "startsWith", "endsWith", "substring");

    private final BugReporter bugReporter;
    private OpcodeStack stack;
    private Map<Integer, String> toStringRegisters;
    private String packageName;

    /**
     * constructs a ITU detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public InappropriateToStringUse(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * overrides the visitor to reset the stack
     *
     * @param classContext
     *            the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            toStringRegisters = new HashMap<>();
            packageName = classContext.getJavaClass().getPackageName();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            toStringRegisters = null;
        }
    }

    /**
     * overrides the visitor to resets the stack for this method.
     *
     * @param obj
     *            the context object for the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        toStringRegisters.clear();
        super.visitCode(obj);
    }

    /**
     * overrides the visitor to look for suspicious operations on toString
     */
    @Override
    public void sawOpcode(int seen) {
        String methodPackage = null;
        try {
            stack.precomputation(this);

            if (seen == INVOKEVIRTUAL) {
                methodPackage = processInvokeVirtual();

            } else if (OpcodeUtils.isAStore(seen)) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    Integer reg = Integer.valueOf(RegisterUtils.getAStoreReg(this, seen));
                    if (item.getUserValue() != null) {
                        XMethod xm = item.getReturnValueOf();
                        if (xm != null) {
                            toStringRegisters.put(reg, xm.getPackageName());
                        } else {
                            toStringRegisters.remove(reg);
                        }
                    } else {
                        toStringRegisters.remove(reg);
                    }
                }
            } else if (OpcodeUtils.isALoad(seen)) {
                Integer reg = Integer.valueOf(RegisterUtils.getAStoreReg(this, seen));
                methodPackage = toStringRegisters.get(reg);
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        } finally {
            TernaryPatcher.pre(stack, seen);
            stack.sawOpcode(this, seen);
            TernaryPatcher.post(stack, seen);
            if ((methodPackage != null) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item item = stack.getStackItem(0);
                item.setUserValue(methodPackage);
            }
        }
    }

    @Nullable
    private String processInvokeVirtual() throws ClassNotFoundException {
        String methodName = getNameConstantOperand();
        if (Values.TOSTRING.equals(methodName)) {
            String signature = getSigConstantOperand();
            if (SignatureBuilder.SIG_VOID_TO_STRING.equals(signature)) {
                String className = getClassConstantOperand();
                if (!validToStringClasses.contains(className) && (stack.getStackDepth() > 0)) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    JavaClass cls = item.getJavaClass();
                    if (cls != null) {
                        return cls.getPackageName();
                    }
                }
            }
        } else if (stringAlgoMethods.contains(methodName)) {
            String className = getClassConstantOperand();
            if (Values.SLASHED_JAVA_LANG_STRING.equals(className)) {
                String signature = getSigConstantOperand();
                int numParms = SignatureUtils.getNumParameters(signature);
                if (stack.getStackDepth() > numParms) {
                    OpcodeStack.Item item = stack.getStackItem(numParms);
                    if (item.getUserValue() != null) {
                        XMethod xm = item.getReturnValueOf();
                        String tsPackage = null;
                        if (xm != null) {
                            tsPackage = xm.getPackageName();
                        }
                        if ((tsPackage == null) || !SignatureUtils.similarPackages(tsPackage, packageName, 2)) {
                            bugReporter.reportBug(new BugInstance(this, BugType.ITU_INAPPROPRIATE_TOSTRING_USE.name(), NORMAL_PRIORITY).addClass(this)
                                    .addMethod(this).addSourceLine(this));
                        }
                    }
                }
            }
        }

        return null;
    }

}
