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

import org.apache.bcel.classfile.Code;
import org.objectweb.asm.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for methods that conflate the use of resources and files. Converting
 * URLs retrieved from potentially non file resources, into files objects.
 */
@CustomUserValue
public class ConflatingResourcesAndFiles extends BytecodeScanningDetector {

    private BugReporter bugReporter;
    private OpcodeStack stack;

    /**
     * constructs a CRF detector given the reporter to report bugs on
     * 
     * @param bugReporter
     *            the sync of bug reports
     */
    public ConflatingResourcesAndFiles(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * overrides the visitor to reset the stack
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
     * overrides the visitor to resets the stack for this method.
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
     * overrides the visitor to look conflated use of resources and files
     */
    @Override
    public void sawOpcode(int seen) {
        boolean sawResource = false;
        try {
            stack.precomputation(this);

            if (seen == INVOKEVIRTUAL) {
                String clsName = getClassConstantOperand();

                if ("java/lang/Class".equals(clsName)) {
                    String methodName = getNameConstantOperand();
                    if ("getResource".equals(methodName)) {
                        sawResource = true;
                    }
                } else if ("java/net/URL".equals(clsName)) {
                    String methodName = getNameConstantOperand();
                    if ("toURI".equals(methodName) || ("getFile".equals(methodName))) {
                        if (stack.getStackDepth() > 0) {
                            if (stack.getStackItem(0).getUserValue() != null) {
                                sawResource = true;
                            }
                        }
                    }
                }
            } else if (seen == INVOKESPECIAL) {
                String clsName = getClassConstantOperand();

                if ("java/io/File".equals(clsName)) {
                    String methodName = getNameConstantOperand();
                    String sig = getSigConstantOperand();
                    if (Values.CONSTRUCTOR.equals(methodName) && Type.getArgumentTypes(sig).length == 1) {
                        if (stack.getStackDepth() > 0) {
                            OpcodeStack.Item item = stack.getStackItem(0);
                            if (item.getUserValue() != null) {
                                bugReporter.reportBug(new BugInstance(this, BugType.CRF_CONFLATING_RESOURCES_AND_FILES.name(), NORMAL_PRIORITY).addClass(this)
                                        .addMethod(this).addSourceLine(this));
                            }
                        }
                    }
                } else if ("java/net/URI".equals(clsName) || "java/net/URL".equals(clsName)) {
                    String methodName = getNameConstantOperand();
                    String sig = getSigConstantOperand();
                    if (Values.CONSTRUCTOR.equals(methodName) && "(Ljava/lang/String;)V".equals(sig)) {
                        if (stack.getStackDepth() > 0) {
                            OpcodeStack.Item item = stack.getStackItem(0);
                            String cons = (String) item.getConstant();
                            if ((cons != null) && !cons.startsWith("file:/")) {
                                sawResource = true;
                            }
                        }
                    }
                }
            }

        } finally {
            stack.sawOpcode(this, seen);
            if (sawResource) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    item.setUserValue(Boolean.TRUE);
                }
            }
        }
    }
}
