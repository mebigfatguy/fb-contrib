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

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for methods that access objects in http sessions, that are complex
 * objects, modifies those objects, but does not call setAttribute to signify a
 * change so that cluster replication can happen.
 */
@CustomUserValue
public class SuspiciousClusteredSessionSupport extends BytecodeScanningDetector {

    private static final Pattern modifyingNames = Pattern.compile("(add|insert|put|remove|clear|set).*");

    private BugReporter bugReporter;
    private OpcodeStack stack;
    private Map<String, Integer> changedAttributes;
    private Map<Integer, String> savedAttributes;

    public SuspiciousClusteredSessionSupport(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to setup the opcode stack and attribute maps
     *
     * @param classContext the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            changedAttributes = new HashMap<>();
            savedAttributes = new HashMap<>();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            changedAttributes = null;
            savedAttributes = null;
        }
    }

    /**
     * implements the visitor to report on attributes that have changed, without a
     * setAttribute being called on them
     *
     * @param obj the currently parsed method
     */
    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        changedAttributes.clear();
        savedAttributes.clear();
        super.visitCode(obj);
        for (Integer pc : changedAttributes.values()) {
            bugReporter.reportBug(
                    new BugInstance(this, BugType.SCSS_SUSPICIOUS_CLUSTERED_SESSION_SUPPORT.name(), NORMAL_PRIORITY)
                            .addClass(this).addMethod(this).addSourceLine(this, pc.intValue()));
        }
    }

    /**
     * implements the visitor to collect calls to getAttribute/setAttribute and
     * stores to attributes to see what have been modified without recalling
     * setAttribute
     *
     * @param seen the currently parsed opcode
     */
    @Override
    public void sawOpcode(int seen) {
        String attributeName = null;
        boolean sawGetAttribute = false;
        try {
            stack.precomputation(this);

            if (seen == Const.INVOKEINTERFACE) {
                String clsName = getClassConstantOperand();
                if ("javax/servlet/http/HttpSession".equals(clsName)) {
                    String methodName = getNameConstantOperand();
                    if ("getAttribute".equals(methodName)) {
                        if (stack.getStackDepth() > 0) {
                            OpcodeStack.Item item = stack.getStackItem(0);
                            Object con = item.getConstant();
                            if (con instanceof String) {
                                attributeName = (String) con;
                                sawGetAttribute = true;
                            }
                        }
                    } else if ("setAttribute".equals(methodName) && (stack.getStackDepth() > 1)) {
                        OpcodeStack.Item item = stack.getStackItem(1);
                        Object con = item.getConstant();
                        if (con instanceof String) {
                            attributeName = (String) con;
                            changedAttributes.remove(attributeName);
                        }
                    }
                }
            } else if (OpcodeUtils.isALoad(seen)) {
                int reg = RegisterUtils.getALoadReg(this, seen);
                attributeName = savedAttributes.get(Integer.valueOf(reg));
                sawGetAttribute = attributeName != null;
            } else if ((((seen >= Const.ASTORE_0) && (seen <= Const.ASTORE_3)) || (seen == Const.ASTORE))
                    && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item item = stack.getStackItem(0);
                attributeName = (String) item.getUserValue();
                int reg = RegisterUtils.getAStoreReg(this, seen);
                savedAttributes.put(Integer.valueOf(reg), attributeName);
            }

            if ((seen == Const.INVOKEINTERFACE) || (seen == Const.INVOKEVIRTUAL)) {
                String methodName = getNameConstantOperand();
                Matcher m = modifyingNames.matcher(methodName);
                if (m.matches()) {
                    String signature = getSigConstantOperand();
                    int numArgs = SignatureUtils.getNumParameters(signature);
                    if (stack.getStackDepth() > numArgs) {
                        OpcodeStack.Item item = stack.getStackItem(numArgs);
                        attributeName = (String) item.getUserValue();
                        if (attributeName != null) {
                            changedAttributes.put(attributeName, Integer.valueOf(getPC()));
                        }
                    }
                }
            } else if ((seen >= Const.IASTORE) && (seen <= Const.SASTORE) && (stack.getStackDepth() > 2)) {
                OpcodeStack.Item item = stack.getStackItem(2);
                attributeName = (String) item.getUserValue();
                if (attributeName != null) {
                    changedAttributes.put(attributeName, Integer.valueOf(getPC()));
                }
            }
        } finally {
            TernaryPatcher.pre(stack, seen);
            stack.sawOpcode(this, seen);
            TernaryPatcher.post(stack, seen);
            if (sawGetAttribute && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item item = stack.getStackItem(0);
                item.setUserValue(attributeName);
            }
        }
    }
}
