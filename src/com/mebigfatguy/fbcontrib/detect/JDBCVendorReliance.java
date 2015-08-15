/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2015 Dave Brosius
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
import java.util.Iterator;
import java.util.Map;

import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for uses of jdbc vendor specific classes and methods making the
 * database access code non portable.
 */
@CustomUserValue
public class JDBCVendorReliance extends BytecodeScanningDetector {
    private final BugReporter bugReporter;
    private OpcodeStack stack;
    private Map<Integer, Integer> jdbcLocals = new HashMap<Integer, Integer>();

    /**
     * constructs a JVR detector given the reporter to report bugs on
     * 
     * @param bugReporter
     *            the sync of bug reports
     */
    public JDBCVendorReliance(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to reset the stack and jdbc locals
     * 
     * @param classContext
     *            the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        stack = new OpcodeStack();
        jdbcLocals = new HashMap<Integer, Integer>();
        super.visitClassContext(classContext);
        stack = null;
        jdbcLocals = null;
    }

    /**
     * implement the visitor to reset the opcode stack and set of locals that
     * are jdbc objects
     * 
     * @param obj
     *            the context param of the currently parsed method
     */
    @Override
    public void visitMethod(Method obj) {
        stack.resetForMethodEntry(this);
        jdbcLocals.clear();

        int[] parmRegs = RegisterUtils.getParameterRegisters(obj);
        Type[] argTypes = obj.getArgumentTypes();

        for (int t = 0; t < argTypes.length; t++) {
            String sig = argTypes[t].getSignature();
            if (isJDBCClass(sig)) {
                jdbcLocals.put(Integer.valueOf(parmRegs[t]),
                        Integer.valueOf(RegisterUtils.getLocalVariableEndRange(obj.getLocalVariableTable(), parmRegs[t], 0)));
            }
        }
    }

    @Override
    public void sawOpcode(int seen) {
        boolean tosIsJDBC = false;
        try {
            stack.precomputation(this);

            int curPC = getPC();
            Iterator<Integer> it = jdbcLocals.values().iterator();
            while (it.hasNext()) {
                Integer endPCRange = it.next();
                if (endPCRange.intValue() < curPC) {
                    it.remove();
                }
            }

            if ((seen == INVOKEVIRTUAL) || (seen == INVOKEINTERFACE)) {
                String clsName = getClassConstantOperand();
                if (!"java/lang/Object".equals(clsName) && !isJDBCClass(clsName)) {
                    int parmCnt = Type.getArgumentTypes(getSigConstantOperand()).length;
                    if (stack.getStackDepth() > parmCnt) {
                        OpcodeStack.Item itm = stack.getStackItem(parmCnt);
                        if (itm.getUserValue() != null) {
                            bugReporter.reportBug(new BugInstance(this, BugType.JVR_JDBC_VENDOR_RELIANCE.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                                    .addSourceLine(this));
                            return;
                        }
                    }
                }
            }

            if (seen == INVOKEINTERFACE) {
                String infName = getClassConstantOperand();
                if (isJDBCClass(infName)) {
                    String sig = getSigConstantOperand();
                    Type retType = Type.getReturnType(sig);
                    infName = retType.getSignature();
                    if (isJDBCClass(infName))
                        tosIsJDBC = true;
                }
            } else if ((seen == ASTORE) || ((seen >= ASTORE_0) && (seen <= ASTORE_3))) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item itm = stack.getStackItem(0);
                    if (itm.getUserValue() != null) {
                        int reg = RegisterUtils.getAStoreReg(this, seen);
                        jdbcLocals.put(Integer.valueOf(reg),
                                Integer.valueOf(RegisterUtils.getLocalVariableEndRange(getMethod().getLocalVariableTable(), reg, getNextPC())));
                    }
                }

            } else if (OpcodeUtils.isALoad(seen)) {
                int reg = RegisterUtils.getALoadReg(this, seen);
                if (jdbcLocals.containsKey(Integer.valueOf(reg)))
                    tosIsJDBC = true;
            }
        } finally {
            TernaryPatcher.pre(stack, seen);
            stack.sawOpcode(this, seen);
            TernaryPatcher.post(stack, seen);
            if (tosIsJDBC) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item itm = stack.getStackItem(0);
                    itm.setUserValue(Boolean.TRUE);
                }
            }
        }
    }

    /**
     * returns whether the class is a jdbc class
     * 
     * @param clsName
     *            class name or signature of a class
     * 
     * @return if the class name is a jdbc one
     */
    private static boolean isJDBCClass(String clsName) {
        if (clsName.endsWith(";"))
            clsName = clsName.substring(1, clsName.length() - 1);
        clsName = clsName.replace('.', '/');

        if (!clsName.startsWith("java/sql/") && !clsName.startsWith("javax/sql/"))
            return false;

        return (!clsName.endsWith("Exception"));
    }
}
