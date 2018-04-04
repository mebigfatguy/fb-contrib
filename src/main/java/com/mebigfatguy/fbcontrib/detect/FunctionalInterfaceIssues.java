/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2018 Dave Brosius
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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Attribute;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantInvokeDynamic;
import org.apache.bcel.classfile.ConstantMethodHandle;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.classfile.Unknown;

import com.mebigfatguy.fbcontrib.utils.CodeByteUtils;

import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for issues around use of @FunctionalInterface classes, especially in use with Streams..
 */
@CustomUserValue
public class FunctionalInterfaceIssues extends BytecodeScanningDetector {

    private BugReporter bugReporter;
    private OpcodeStack stack;
    private Attribute bootstrapAtt;
    private Map<Method, List<FIInfo>> functionalInterfaceInfo;

    public FunctionalInterfaceIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            JavaClass cls = classContext.getJavaClass();
            if (cls.getMajor() >= Const.MAJOR_1_8) {
                bootstrapAtt = getBootstrapAttribute(cls);
                if (bootstrapAtt != null) {
                    stack = new OpcodeStack();
                    functionalInterfaceInfo = new HashMap<>();
                    super.visitClassContext(classContext);

                    for (Map.Entry<Method, List<FIInfo>> entry : functionalInterfaceInfo.entrySet()) {
                        for (FIInfo fii : entry.getValue()) {

                        }
                    }
                }
            }
        } finally {
            functionalInterfaceInfo = null;
            bootstrapAtt = null;
            stack = null;
        }
    }

    @Override
    public void visitCode(Code obj) {

        if (prescreen(getMethod())) {
            stack.resetForMethodEntry(this);
            super.visitCode(obj);
        }
    }

    @Override
    public void sawOpcode(int seen) {

        try {
            switch (seen) {
                case Const.INVOKEDYNAMIC:
                    List<FIInfo> fiis = functionalInterfaceInfo.get(getMethod());
                    if (fiis == null) {
                        fiis = new ArrayList<>();
                        functionalInterfaceInfo.put(getMethod(), fiis);
                    }

                    ConstantInvokeDynamic cid = (ConstantInvokeDynamic) getConstantRefOperand();

                    ConstantMethodHandle cmh = getMethodHandle(cid.getBootstrapMethodAttrIndex());
                    FIInfo fii = new FIInfo(getPC(), cid.getBootstrapMethodAttrIndex());
                    fiis.add(fii);
                break;

            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    /**
     * looks for methods that contain a INVOKEDYNAMIC opcodes
     *
     * @param method
     *            the context object of the current method
     * @return if the class uses synchronization
     */
    private boolean prescreen(Method method) {
        BitSet bytecodeSet = getClassContext().getBytecodeSet(method);
        return (bytecodeSet != null) && (bytecodeSet.get(Const.INVOKEDYNAMIC));
    }

    private Attribute getBootstrapAttribute(JavaClass cls) {
        for (Attribute att : cls.getAttributes()) {
            if ("BootstrapMethods".equals(att.getName())) {
                return att;
            }
        }

        return null;
    }

    private ConstantMethodHandle getMethodHandle(int bootstrapIndex) {
        int offset = bootstrapIndex * 6;
        byte[] attBytes = ((Unknown) bootstrapAtt).getBytes();
        int methodRefIndex = CodeByteUtils.getshort(attBytes, offset += 2);
        int numArgs = CodeByteUtils.getshort(attBytes, offset += 2);
        for (int i = 0; i < numArgs; i++) {
            int arg = CodeByteUtils.getshort(attBytes, offset += 2);
            Constant c = getConstantPool().getConstant(arg);
            if (c instanceof ConstantMethodHandle) {
                return (ConstantMethodHandle) c;
            }
        }

        return null;
    }

    class FIInfo {
        private int pc;
        private int bootstrapId;

        public FIInfo(int pc, int bootstrapId) {
            this.pc = pc;
            this.bootstrapId = bootstrapId;
        }
    }
}
