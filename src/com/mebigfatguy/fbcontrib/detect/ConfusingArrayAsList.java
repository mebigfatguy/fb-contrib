/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2013 Dave Brosius
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

import org.apache.bcel.classfile.Code;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 *
 * looks for calls to Arrays.asList where the parameter is a primitive array.
 * This does not produce a list that holds the primitive boxed value, but a list of
 * one item, the array itself.
 *
 */
public class ConfusingArrayAsList extends BytecodeScanningDetector {

    private static Set<String> PRIMITIVE_ARRAYS = new HashSet<String>(8);
    static {
        PRIMITIVE_ARRAYS.add("[[B");
        PRIMITIVE_ARRAYS.add("[[C");
        PRIMITIVE_ARRAYS.add("[[S");
        PRIMITIVE_ARRAYS.add("[[I");
        PRIMITIVE_ARRAYS.add("[[J");
        PRIMITIVE_ARRAYS.add("[[F");
        PRIMITIVE_ARRAYS.add("[[D");
        PRIMITIVE_ARRAYS.add("[[Z");

    }
    private BugReporter bugReporter;
    private OpcodeStack stack;

    /**
     * constructs a CAAL detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
     */
    public ConfusingArrayAsList(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to create and teardown the opcode stack
     *
     * @param classContext the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
        }
    }

    /**
     * implements the visitor to clear the opcode stack
     *
     * @param obj the currently code block
     */
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        super.visitCode(obj);
    }

    /**
     * implements the visitor to find calls to Arrays.asList with a primitive array
     *
     * @param seen the currently visitor opcode
     */
    @Override
    public void sawOpcode(int seen) {
        try {
            if (seen == INVOKESTATIC) {
                String clsName = getClassConstantOperand();
                if ("java/util/Arrays".equals(clsName)) {
                    String methodName = getNameConstantOperand();
                    if ("asList".equals(methodName)) {
                        if (stack.getStackDepth() == 1) {
                            OpcodeStack.Item item = stack.getStackItem(0);
                            String sig = item.getSignature();
                            if (PRIMITIVE_ARRAYS.contains(sig)) {
                                bugReporter.reportBug(new BugInstance(this, "CAAL_CONFUSING_ARRAY_AS_LIST", NORMAL_PRIORITY)
                                            .addClass(this)
                                            .addMethod(this)
                                            .addSourceLine(this));
                            }
                        }
                    }
                }
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }
}
