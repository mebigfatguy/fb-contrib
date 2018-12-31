/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2019 Dave Brosius
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

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.LocalVariable;
import org.apache.bcel.classfile.LocalVariableTable;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.FieldAnnotation;
import edu.umd.cs.findbugs.internalAnnotations.SlashedClassName;

/**
 * looks for manual casts of objects that are more specific then needed as the value is assigned to a class or interface higher up in the inheritance chain. You
 * only need to cast to that class or interface.
 */
public class OverzealousCasting extends BytecodeScanningDetector {
    enum State {
        SAW_NOTHING, SAW_NEXT, SAW_CHECKCAST
    }

    private BugReporter bugReporter;
    private State state;
    private LocalVariableTable lvt;
    private @SlashedClassName String castClass;

    /**
     * constructs a OC detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public OverzealousCasting(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to set the state on entry of the code block to SAW_NOTHING, and to see if there is a local variable table
     *
     * @param obj
     *            the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        state = State.SAW_NOTHING;
        lvt = obj.getLocalVariableTable();
        if ((lvt != null) && prescreen(getMethod())) {
            super.visitCode(obj);
        }
    }

    /**
     * looks for methods that contain a checkcast instruction
     *
     * @param method
     *            the context object of the current method
     * @return if the class does checkcast instructions
     */
    private boolean prescreen(Method method) {
        BitSet bytecodeSet = getClassContext().getBytecodeSet(method);
        return (bytecodeSet != null) && (bytecodeSet.get(Constants.CHECKCAST));
    }

    /**
     * implements the visitor to look for a checkcast followed by a astore, where the types of the objects are different.
     *
     * @param seen
     *            the opcode of the currently parsed instruction
     */
    @Override
    public void sawOpcode(int seen) {
        switch (state) {
            case SAW_NOTHING:
                if (seen == CHECKCAST) {
                    castClass = getClassConstantOperand();
                    state = State.SAW_CHECKCAST;
                } else if (seen == INVOKEINTERFACE) {
                    // enhanced for loops add an incorrect checkcast instruction, so
                    // ignore checkcasts after iterator.next()
                    String clsName = getClassConstantOperand();
                    String methodName = getNameConstantOperand();
                    if ("java/util/Iterator".equals(clsName) && "next".equals(methodName)) {
                        state = State.SAW_NEXT;
                    }
                }
            break;

            case SAW_CHECKCAST:
                if (OpcodeUtils.isAStore(seen)) {
                    int reg = RegisterUtils.getAStoreReg(this, seen);
                    LocalVariable lv = lvt.getLocalVariable(reg, getNextPC());
                    if (lv != null) {
                        String sig = lv.getSignature();
                        if (sig.startsWith(Values.SIG_QUALIFIED_CLASS_PREFIX)) {
                            sig = SignatureUtils.trimSignature(sig);
                        }
                        if (!sig.equals(castClass)) {
                            bugReporter.reportBug(new BugInstance(this, BugType.OC_OVERZEALOUS_CASTING.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                                    .addSourceLine(this));
                        }
                    }
                } else if (seen == PUTFIELD) {
                    FieldAnnotation f = FieldAnnotation.fromReferencedField(this);
                    String sig = f.getFieldSignature();
                    if (sig.startsWith(Values.SIG_QUALIFIED_CLASS_PREFIX)) {
                        sig = SignatureUtils.trimSignature(sig);
                    }
                    // if the signature is Object, the field might be genericized, so ignore
                    if (!Values.SLASHED_JAVA_LANG_OBJECT.equals(sig) && !sig.equals(castClass)) {
                        bugReporter.reportBug(new BugInstance(this, BugType.OC_OVERZEALOUS_CASTING.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                                .addSourceLine(this));
                    }
                }
                state = State.SAW_NOTHING;
            break;

            case SAW_NEXT:
            default:
                state = State.SAW_NOTHING;
            break;
        }
    }
}
