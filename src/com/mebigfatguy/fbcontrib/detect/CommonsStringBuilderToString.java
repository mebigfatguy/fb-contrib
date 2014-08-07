/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2014 Bhaskar Maddala
 * Copyright (C) 2005-2014 Dave Brosius
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
import java.util.Stack;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.LocalVariable;
import org.apache.bcel.classfile.LocalVariableTable;

import com.mebigfatguy.fbcontrib.utils.RegisterUtils;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.OpcodeStack.Item;
import edu.umd.cs.findbugs.bcel.OpcodeStackDetector;

/**
 * Find usage of ToStringBuilder from Apache commons, where the code invokes
 * toString() on the constructed object without invoking append().
 *
 * Usage without invoking append is equivalent of using the Object.toString()
 * method
 *
 * <pre>
 * new ToStringBuilder(this).toString();
 * </pre>
 */
public class CommonsStringBuilderToString extends OpcodeStackDetector {

    private static final Set<String> TOSTRINGBUILDER_CTOR_SIGS = new HashSet<String>();
    static {
        TOSTRINGBUILDER_CTOR_SIGS.add("(Ljava/lang/Object;)V");
        TOSTRINGBUILDER_CTOR_SIGS.add("(Ljava/lang/Object;Lorg/apache/commons/lang/builder/ToStringStyle;)V");
        TOSTRINGBUILDER_CTOR_SIGS.add("(Ljava/lang/Object;Lorg/apache/commons/lang3/builder/ToStringStyle;)V");
    }
    
    private final BugReporter bugReporter;
    private final Stack<Pair> stackTracker = new Stack<Pair>();
    private final Map<Integer, Boolean> registerTracker = new HashMap<Integer, Boolean>(10);

    /**
     * constructs a CSBTS detector given the reporter to report bugs on.
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public CommonsStringBuilderToString(final BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visit(Code obj) {
        registerTracker.clear();
        stackTracker.clear();
        super.visit(obj);
    }

    @Override
    public boolean shouldVisitCode(Code obj) {
        LocalVariableTable lvt = getMethod().getLocalVariableTable();
        return lvt != null;
    }

    @Override
    public void sawOpcode(int seen) {
        switch (seen) {
        case ALOAD:
        case ALOAD_0:
        case ALOAD_1:
        case ALOAD_2:
        case ALOAD_3:
            LocalVariable lv = getMethod().getLocalVariableTable()
                    .getLocalVariable(RegisterUtils.getALoadReg(this, seen),
                            getNextPC());
            if (lv != null) {
                String signature = lv.getSignature();
                if (isToStringBuilder(signature)) {
                    Integer loadReg = Integer.valueOf(getRegisterOperand());
                    Boolean appendInvoked = registerTracker.get(loadReg);
                    if (appendInvoked != null) {
                        stackTracker.add(new Pair(loadReg.intValue(),
                                appendInvoked.booleanValue()));
                    }
                }
            }
            break;
        case ASTORE:
        case ASTORE_0:
        case ASTORE_1:
        case ASTORE_2:
        case ASTORE_3:
            Item si = stack.getStackItem(0);
            String signature = si.getSignature();
            if (isToStringBuilder(signature)) {
                int storeReg = getRegisterOperand();
                Pair p = stackTracker.pop();
                registerTracker.put(
                        Integer.valueOf(storeReg),
                        p.register == -1 ? Boolean.FALSE : registerTracker
                                .get(Integer.valueOf(p.register)));
            }
            break;
        case POP:
            si = stack.getStackItem(0);
            signature = si.getSignature();
            if (isToStringBuilder(signature)) {
                if (!stackTracker.isEmpty()) {
                    Pair p = stackTracker.pop();
                    registerTracker.put(Integer.valueOf(p.register),
                            Boolean.valueOf(p.appendInvoked));
                }
            }
            break;
        case INVOKESPECIAL:
        case INVOKEVIRTUAL:
            String loadClassName = getClassConstantOperand();
            String calledMethodName = getNameConstantOperand();

            if ("org/apache/commons/lang3/builder/ToStringBuilder"
                    .equals(loadClassName)
                    || "org/apache/commons/lang/builder/ToStringBuilder"
                            .equals(loadClassName)) {
                String calledMethodSig = getSigConstantOperand();
                if ("<init>".equals(calledMethodName)
                        && TOSTRINGBUILDER_CTOR_SIGS.contains(calledMethodSig)) {
                    stackTracker.add(new Pair(-1, false));
                } else if ("append".equals(calledMethodName)) {
                    Pair p = stackTracker.pop();
                    stackTracker.add(new Pair(p.register, true));
                } else if ("toString".equals(calledMethodName)
                        && "()Ljava/lang/String;".equals(calledMethodSig)) {
                    Pair p = stackTracker.pop();
                    if (p.appendInvoked == false) {
                        bugReporter.reportBug(new BugInstance(this,
                                "CSBTS_COMMONS_STRING_BUILDER_TOSTRING",
                                HIGH_PRIORITY).addClass(this).addMethod(this)
                                .addSourceLine(this));
                    }
                }
            }
            default:
            	break;
        }
    }

    private static boolean isToStringBuilder(String signature) {
        return "Lorg/apache/commons/lang3/builder/ToStringBuilder;"
                .equals(signature)
                || "Lorg/apache/commons/lang/builder/ToStringBuilder;"
                        .equals(signature);
    }

    static final class Pair {
        public final int register;
        public final boolean appendInvoked;

        Pair(int register, boolean appendInvoked) {
            this.register = register;
            this.appendInvoked = appendInvoked;
        }
        
        @Override
        public String toString() {
            return "register: " + register + " appendInvoked: " + appendInvoked;
        }
    }
}
