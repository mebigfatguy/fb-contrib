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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.ToString;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for methods that return a parameter after making what looks like
 * modifications to that parameter. This leads to confusion for the user of this
 * method as it isn't obvious that the 'original' object is modified. If the
 * point of this method is to modify the parameter, it is probably better just
 * to have the method be a void method, to avoid confusion.
 */
public class ConfusingFunctionSemantics extends BytecodeScanningDetector {
    private static final Set<String> knownImmutables;

    static {
        Set<String> ki = new HashSet<String>();
        ki.add("Ljava/lang/String;");
        ki.add("Ljava/lang/Byte;");
        ki.add("Ljava/lang/Character;");
        ki.add("Ljava/lang/Short;");
        ki.add("Ljava/lang/Integer;");
        ki.add("Ljava/lang/Long;");
        ki.add("Ljava/lang/Float;");
        ki.add("Ljava/lang/Double;");
        ki.add("Ljava/lang/Boolean;");
        ki.add("Ljava/lang/Class;");
        knownImmutables = Collections.unmodifiableSet(ki);
    }

    private final BugReporter bugReporter;
    private Map<Integer, ParmUsage> possibleParmRegs;
    private OpcodeStack stack;

    /**
     * constructs a CFS detector given the reporter to report bugs on
     * 
     * @param bugReporter
     *            the sync of bug reports
     */
    public ConfusingFunctionSemantics(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to initialize/destroy the possible parameter
     * registers and opcode stack
     * 
     * @param classContext
     *            the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            possibleParmRegs = new HashMap<Integer, ParmUsage>(10);
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            possibleParmRegs = null;
        }
    }

    /**
     * implements the visitor to look for any non-immutable typed parameters are
     * assignable to the return type. If found, the method is parsed.
     * 
     * @param obj
     *            the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        try {
            possibleParmRegs.clear();
            Method m = getMethod();
            String methodSignature = m.getSignature();
            String retSignature = Type.getReturnType(methodSignature).getSignature();
            JavaClass returnClass = null;
            int[] parmRegs = null;

            if ((retSignature.charAt(0) == 'L') && !knownImmutables.contains(retSignature)) {
                Type[] parmTypes = Type.getArgumentTypes(methodSignature);
                for (int p = 0; p < parmTypes.length; p++) {
                    String parmSignature = parmTypes[p].getSignature();
                    if ((parmSignature.charAt(0) == 'L') && !knownImmutables.contains(parmSignature)) {
                        if (returnClass == null) {
                            returnClass = Repository.lookupClass(retSignature.substring(1, retSignature.length() - 1));
                            parmRegs = RegisterUtils.getParameterRegisters(m);
                        }

                        JavaClass parmClass = Repository.lookupClass(SignatureUtils.stripSignature(parmSignature));
                        if ((parmRegs != null) && parmClass.instanceOf(returnClass)) {
                            possibleParmRegs.put(Integer.valueOf(parmRegs[p]), new ParmUsage());
                        }
                    }
                }

                if (possibleParmRegs.size() > 0) {
                    stack.resetForMethodEntry(this);
                    super.visitCode(obj);
                    for (ParmUsage pu : possibleParmRegs.values()) {
                        if ((pu.returnPC >= 0) && (pu.alteredPC >= 0)) {
                            bugReporter.reportBug(new BugInstance(this, BugType.CFS_CONFUSING_FUNCTION_SEMANTICS.name(), NORMAL_PRIORITY).addClass(this)
                                    .addMethod(this).addSourceLine(this, pu.returnPC).addSourceLine(this, pu.alteredPC));
                        }
                    }
                }
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        }
    }

    @Override
    public void sawOpcode(int seen) {
        if (possibleParmRegs.isEmpty())
            return;

        try {
            stack.precomputation(this);

            if (seen == ARETURN) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    int reg = item.getRegisterNumber();
                    ParmUsage pu = possibleParmRegs.get(Integer.valueOf(reg));
                    if (pu != null)
                        pu.setReturnPC(getPC());
                }
            } else if (seen == PUTFIELD) {
                if (stack.getStackDepth() > 1) {
                    OpcodeStack.Item item = stack.getStackItem(1);
                    int reg = item.getRegisterNumber();
                    ParmUsage pu = possibleParmRegs.get(Integer.valueOf(reg));
                    if (pu != null)
                        pu.setAlteredPC(getPC());
                }
            } else if (OpcodeUtils.isAStore(seen)) {
                int reg = RegisterUtils.getAStoreReg(this, seen);
                possibleParmRegs.remove(Integer.valueOf(reg));
            } else if ((seen == INVOKEVIRTUAL) || (seen == INVOKEINTERFACE)) {
                String calledSig = getSigConstantOperand();
                String calledRet = Type.getReturnType(calledSig).getSignature();
                if ("V".equals(calledRet)) {
                    int calledObjOffset = Type.getArgumentTypes(calledSig).length;
                    if (stack.getStackDepth() > calledObjOffset) {
                        OpcodeStack.Item item = stack.getStackItem(calledObjOffset);
                        int reg = item.getRegisterNumber();
                        ParmUsage pu = possibleParmRegs.get(Integer.valueOf(reg));
                        if (pu != null)
                            pu.setAlteredPC(getPC());
                    }
                }
            }

        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    static class ParmUsage {
        int returnPC = -1;
        int alteredPC = -1;

        void setReturnPC(int pc) {
            returnPC = pc;
        }

        void setAlteredPC(int pc) {
            if (alteredPC < 0)
                alteredPC = pc;
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }
}
