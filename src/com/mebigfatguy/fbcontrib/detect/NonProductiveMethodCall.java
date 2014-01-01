/*
 * fb-contrib - Auxiliary detectors for Java programs
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

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.bcel.classfile.Code;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for common methods that are believed to be non mutating, where the value
 * is discarded. Since the method makes no changes to the object, calling this method
 * is useless. The method call can be removed.
 */
@CustomUserValue
public class NonProductiveMethodCall extends BytecodeScanningDetector {

    private static final Set<Pattern> IMMUTABLE_METHODS = new HashSet<Pattern>();

    static {
        IMMUTABLE_METHODS.add(Pattern.compile(".*@toString\\(\\)Ljava/lang/String;"));
        IMMUTABLE_METHODS.add(Pattern.compile("java/lang/.+@.+Value\\(\\)[BCDFIJSZ]"));
        IMMUTABLE_METHODS.add(Pattern.compile(".*@equals\\(Ljava/lang/Object;\\)Z"));
        IMMUTABLE_METHODS.add(Pattern.compile(".*@hashCode\\(\\)I"));
        IMMUTABLE_METHODS.add(Pattern.compile(".*@clone\\(\\).+"));
        IMMUTABLE_METHODS.add(Pattern.compile("java/util/.+@toArray\\(\\)\\[.+"));
    }

    private BugReporter bugReporter;
    private OpcodeStack stack;

    /**
     * constructs a NPMC detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
     */
    public NonProductiveMethodCall(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to set and clear the stack
     */
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
        }
    }

    /**
     * implements the visitor to reset the opcode stack
     *
     * @param obj the context object of the currently parsed code block
     */
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        super.visitCode(obj);
    }

    /**
     * implements the visitor to look for return values of common immutable method
     * calls, that are thrown away.
     *
     * @param seen the opcode of the currently parsed instruction
     */
    public void sawOpcode(int seen) {
        String methodInfo = null;
        try {
            stack.precomputation(this);
            
            switch (seen) {
            case INVOKEVIRTUAL:
            case INVOKEINTERFACE:
            case INVOKESTATIC:
                String sig = getSigConstantOperand();
                if (!sig.endsWith("V")) {
                    methodInfo = getClassConstantOperand() + "@" + getNameConstantOperand() + getSigConstantOperand();
                }
                break;

            case POP:
            case POP2:
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    String mInfo = (String) item.getUserValue();
                    if (mInfo != null) {
                        for (Pattern p : IMMUTABLE_METHODS) {
                            Matcher m = p.matcher(mInfo);
                            
                            if (m.matches()) {
                                bugReporter.reportBug(new BugInstance(this, "NPMC_NON_PRODUCTIVE_METHOD_CALL", NORMAL_PRIORITY)
                                            .addClass(this)
                                            .addMethod(this)
                                            .addSourceLine(this)
                                            .addString(mInfo));
                                break;
                            }
                        }
                    }
                }
                break;
            }


        } finally {
            stack.sawOpcode(this, seen);
            if (methodInfo != null) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    item.setUserValue(methodInfo);
                }
            }
        }
    }
}
