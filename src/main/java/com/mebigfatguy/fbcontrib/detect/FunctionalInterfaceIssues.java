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
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.ConstantInvokeDynamic;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

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
    private Map<Method, List<FIInfo>> functionalInterfaceInfo;

    public FunctionalInterfaceIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            JavaClass cls = classContext.getJavaClass();
            if (cls.getMajor() >= Const.MAJOR_1_8) {
                stack = new OpcodeStack();
                functionalInterfaceInfo = new HashMap<>();
                super.visitClassContext(classContext);
            }
        } finally {
            functionalInterfaceInfo = null;
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
                    FIInfo fii = new FIInfo(getPC(), cid.getBootstrapMethodAttrIndex());
                    fiis.add(fii);
                break;

            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    @Override
    public void report() {
        for (Map.Entry<Method, List<FIInfo>> entry : functionalInterfaceInfo.entrySet()) {
            for (FIInfo fii : entry.getValue()) {

            }
        }
    }

    /**
     * looks for methods that contain a INVOKEDYNAMIC opcodes
     *
     * @param method
     *            the context object of the current method
     * @return if the class uses synchronization
     */
    public boolean prescreen(Method method) {
        BitSet bytecodeSet = getClassContext().getBytecodeSet(method);
        return (bytecodeSet != null) && (bytecodeSet.get(Const.INVOKEDYNAMIC));
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
