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

import org.apache.bcel.classfile.Code;

import com.mebigfatguy.fbcontrib.utils.BugType;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for java.util.Properties use where values other than String are placed
 * in the properties object. As the Properties object was intended to be a
 * String to String only collection, putting other types in the Properties
 * object is incorrect, and takes advantage of a poor design decision by the
 * original Properties class designers to derive from Hashtable, rather than
 * using aggregation.
 */
public class ImproperPropertiesUse extends BytecodeScanningDetector {

    private final BugReporter bugReporter;
    private OpcodeStack stack;

    /**
     * constructs a IPU detector given the reporter to report bugs on
     * 
     * @param bugReporter
     *            the sync of bug reports
     */
    public ImproperPropertiesUse(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the listener to set up and tear down the opcode stack
     * 
     * @param classContext
     *            the context object of the currently parsed class
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
     * implements the visitor to reset the opcode stack
     * 
     * @param obj
     *            the context object for the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        super.visitCode(obj);
    }

    /**
     * implements the visitor to look for calls to java.utils.Properties.put,
     * where the value is a non String. Reports both cases, where if it is a
     * string, at a lower lever.
     * 
     * @param seen
     *            the currently parsed op code
     */

    @Override
    public void sawOpcode(int seen) {
        try {
            stack.precomputation(this);

            if (seen == INVOKEVIRTUAL) {
                String clsName = getClassConstantOperand();
                if ("java/util/Properties".equals(clsName)) {
                    String methodName = getNameConstantOperand();
                    if ("put".equals(methodName)) {
                        String sig = getSigConstantOperand();
                        if ("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(sig)) {
                            if (stack.getStackDepth() >= 3) {
                                OpcodeStack.Item valueItem = stack.getStackItem(0);
                                String valueSig = valueItem.getSignature();
                                if ("Ljava/lang/String;".equals(valueSig)) {
                                    bugReporter.reportBug(new BugInstance(this, BugType.IPU_IMPROPER_PROPERTIES_USE_SETPROPERTY.name(), LOW_PRIORITY)
                                            .addClass(this).addMethod(this).addSourceLine(this));
                                } else if (!"Ljava/lang/Object;".equals(valueSig)) {
                                    bugReporter.reportBug(new BugInstance(this, BugType.IPU_IMPROPER_PROPERTIES_USE.name(), NORMAL_PRIORITY).addClass(this)
                                            .addMethod(this).addSourceLine(this));
                                } else {
                                    bugReporter.reportBug(new BugInstance(this, BugType.IPU_IMPROPER_PROPERTIES_USE_SETPROPERTY.name(), NORMAL_PRIORITY)
                                            .addClass(this).addMethod(this).addSourceLine(this));
                                }
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
