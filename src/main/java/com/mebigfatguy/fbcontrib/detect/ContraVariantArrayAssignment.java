/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2012-2019 Bhaskar Maddala
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
import org.apache.bcel.classfile.LocalVariable;
import org.apache.bcel.classfile.LocalVariableTable;
import org.apache.bcel.generic.ArrayType;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;

/**
 * Finds contravariant array assignments. Since arrays are mutable data structures, their use must be restricted to covariant or invariant usage
 *
 * <pre>
 * class A {
 * }
 * class B extends A {
 * }
 *
 * B[] b = new B[2];
 * A[] a = b;
 * a[0] = new A(); // results in ArrayStoreException (Runtime)
 * </pre>
 *
 * Contravariant array assignments are reported as low or normal priority bugs. In cases where the detector can determine an ArrayStoreException the bug is
 * reported with high priority.
 *
 */
public class ContraVariantArrayAssignment extends BytecodeScanningDetector {
    private final BugReporter bugReporter;
    private final OpcodeStack stack;

    /**
     * constructs a CVAA detector given the reporter to report bugs on.
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public ContraVariantArrayAssignment(final BugReporter bugReporter) {
        this.bugReporter = bugReporter;
        stack = new OpcodeStack();
    }

    /**
     * implements the visitor to pass through constructors and static initializers to the byte code scanning code. These methods are not reported, but are used
     * to build SourceLineAnnotations for fields, if accessed.
     *
     * @param obj
     *            the context object of the currently parsed code attribute
     */
    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        LocalVariableTable lvt = getMethod().getLocalVariableTable();
        if (lvt != null) {
            super.visitCode(obj);
        }
    }

    @Override
    public void sawOpcode(int seen) {
        try {
            stack.precomputation(this);

            switch (seen) {
                case ASTORE:
                case ASTORE_0:
                case ASTORE_1:
                case ASTORE_2:
                case ASTORE_3:
                    if (stack.getStackDepth() > 0) {
                        LocalVariable lv = getMethod().getLocalVariableTable().getLocalVariable(RegisterUtils.getAStoreReg(this, seen), getNextPC());
                        if (lv != null) {
                            OpcodeStack.Item item = stack.getStackItem(0);
                            String sourceSignature = item.getSignature();
                            String targetSignature = lv.getSignature();
                            checkSignatures(sourceSignature, targetSignature);
                        }
                    }
                break;
                case PUTFIELD:
                case PUTSTATIC:
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item item = stack.getStackItem(0);
                        String sourceSignature = item.getSignature();
                        String targetSignature = getSigConstantOperand();
                        checkSignatures(sourceSignature, targetSignature);
                    }
                break;
                case AASTORE:
                /*
                 * OpcodeStack.Item arrayref = stack.getStackItem(2);
                 * OpcodeStack.Item value = stack.getStackItem(0);
                 *
                 * if(!value.isNull()) { String sourceSignature =
                 * value.getSignature(); String targetSignature =
                 * arrayref.getSignature(); if
                 * (!"Ljava/lang/Object;".equals(targetSignature)) { try{
                 * if(Type.getType(sourceSignature) instanceof ObjectType ) {
                 * ObjectType sourceType = (ObjectType)
                 * Type.getType(sourceSignature); ObjectType targetType =
                 * (ObjectType) ((ArrayType)
                 * Type.getType(targetSignature)).getBasicType();
                 * if(!sourceType.equals(targetType) &&
                 * !sourceType.subclassOf(targetType)){
                 * bugReporter.reportBug(new BugInstance(this,
                 * BugType.CVAA_CONTRAVARIANT_ARRAY_ASSIGNMENT.name(),
                 * HIGH_PRIORITY) .addClass(this) .addMethod(this)
                 * .addSourceLine(this)); } } } catch (ClassNotFoundException
                 * cnfe) { bugReporter.reportMissingClass(cnfe); } } }
                 */
                break;
            }
            super.sawOpcode(seen);
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    private static boolean isObjectType(Type type) {
        return ((ArrayType) type).getBasicType() instanceof ObjectType;
    }

    private void checkSignatures(String sourceSignature, String targetSignature) {
        try {
            if (Values.SIG_JAVA_LANG_OBJECT.equals(targetSignature)) {
                return;
            }

            Type sourceType = Type.getType(sourceSignature);
            Type targetType = Type.getType(targetSignature);
            if ((sourceType instanceof ArrayType) && (targetType instanceof ArrayType) && isObjectType(sourceType) && isObjectType(targetType)) {
                ObjectType sourceElementType = (ObjectType) ((ArrayType) sourceType).getBasicType();
                ObjectType targetElementType = (ObjectType) ((ArrayType) targetType).getBasicType();
                if (!targetElementType.isCastableTo(sourceElementType)) {
                    bugReporter.reportBug(new BugInstance(this, BugType.CVAA_CONTRAVARIANT_ELEMENT_ASSIGNMENT.name(), NORMAL_PRIORITY).addClass(this)
                            .addMethod(this).addSourceLine(this));
                }
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        }
    }
}
