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

import java.util.HashSet;
import java.util.Set;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for classes that appear to implement the old style type safe enum pattern that was used before java added Enum support to the language. Since this
 * class is compiled with java 1.5 or later, it would be simpler to just use java enums
 */
public class DeprecatedTypesafeEnumPattern extends BytecodeScanningDetector {
    enum State {
        SAW_NOTHING, SAW_INVOKESPECIAL, SAW_BUG
    }

    private final BugReporter bugReporter;
    private int firstEnumPC;
    private int enumCount;
    private Set<String> enumConstNames;
    State state;

    /**
     * constructs a DTEP detector given the reporter to report bugs on.
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public DeprecatedTypesafeEnumPattern(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to look for classes compiled with 1.5 or better that have all constructors that are private
     *
     * @param context
     *            the currently parsed class context object
     */
    @Override
    public void visitClassContext(ClassContext context) {
        try {
            JavaClass cls = context.getJavaClass();
            if (!cls.isEnum() && (cls.getMajor() >= Constants.MAJOR_1_5)) {
                Method[] methods = cls.getMethods();
                for (Method m : methods) {
                    if (Values.CONSTRUCTOR.equals(m.getName()) && !m.isPrivate()) {
                        return;
                    }
                }
                firstEnumPC = 0;
                enumCount = 0;
                enumConstNames = new HashSet<String>(10);
                super.visitClassContext(context);
            }
        } finally {
            enumConstNames = null;
        }
    }

    /**
     * implements the visitor to look for fields that are public static final and are the same type as the owning class. it collects these object names for
     * later
     *
     * @param obj
     *            the context object of the currently parsed field
     */
    @Override
    public void visitField(Field obj) {
        if (obj.isStatic() && obj.isPublic() && obj.isFinal()) {
            JavaClass cls = getClassContext().getJavaClass();
            if (!obj.isEnum()) {
                String fieldClass = obj.getSignature();
                if (fieldClass.startsWith(Values.SIG_QUALIFIED_CLASS_PREFIX)) {
                    fieldClass = fieldClass.substring(1, fieldClass.length() - 1);
                    String clsClass = cls.getClassName();
                    if (fieldClass.equals(clsClass)) {
                        enumConstNames.add(obj.getName());
                        super.visitField(obj);
                    }
                }
            }
        }
    }

    /**
     * implements the visitor to look for static initializers to find enum generation
     *
     * @param obj
     *            the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        if (Values.STATIC_INITIALIZER.equals(getMethod().getName())) {
            state = State.SAW_NOTHING;
            super.visitCode(obj);
        }
    }

    /**
     * implements the visitor to find allocations of TypesafeEnum constants
     *
     * @param seen
     *            the currently parsed opcode
     */
    @Override
    public void sawOpcode(int seen) {
        if (state == State.SAW_NOTHING) {
            if (seen == INVOKESPECIAL) {
                state = State.SAW_INVOKESPECIAL;
            }
        } else if (state == State.SAW_INVOKESPECIAL) {
            handleInvokeSpecialState(seen);
        }
    }

    private void handleInvokeSpecialState(int seen) {
        if (seen == PUTSTATIC) {
            String fieldName = getNameConstantOperand();
            if (enumConstNames.contains(fieldName)) {
                if (enumCount == 0) {
                    firstEnumPC = getPC();
                }
                enumCount++;
                if (enumCount >= 2) {
                    bugReporter.reportBug(new BugInstance(this, BugType.DTEP_DEPRECATED_TYPESAFE_ENUM_PATTERN.name(), NORMAL_PRIORITY).addClass(this)
                            .addMethod(this).addSourceLine(this, firstEnumPC));
                    state = State.SAW_BUG;
                }
            }
        }
        if (state != State.SAW_BUG) {
            state = State.SAW_NOTHING;
        }
    }

}
