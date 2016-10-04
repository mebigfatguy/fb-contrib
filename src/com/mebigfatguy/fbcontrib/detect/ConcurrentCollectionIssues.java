/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2016 Bhaskar Maddala
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

import java.util.HashMap;
import java.util.Map;

import org.apache.bcel.classfile.Code;

import com.mebigfatguy.fbcontrib.utils.BugType;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

@CustomUserValue
public class ConcurrentCollectionIssues extends BytecodeScanningDetector {

    private final BugReporter bugReporter;
    private OpcodeStack stack;
    private Map<String, CCIUserValue> fieldUserValues;

    private enum CCIUserValue {
        CONCURRENT_HASHMAP, CONCURRENT_HASHMAP_VALUE;
    };

    /**
     * constructs a CCI detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public ConcurrentCollectionIssues(final BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * overrides the visitor to initialize the opcode stack
     *
     * @param classContext
     *            the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            fieldUserValues = new HashMap<>();
            classContext.getJavaClass().accept(this);
        } finally {
            fieldUserValues = null;
            stack = null;
        }
    }

    /**
     * implements the visitor to see if reset the opcode stack
     *
     * @param obj
     *            the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        super.visitCode(obj);
    }

    /**
     * implements the visitor to look for concurrent collection issue
     */
    @Override
    public void sawOpcode(int seen) {
        CCIUserValue userValue = null;
        try {
            stack.precomputation(this);

            switch (seen) {
                case INVOKESPECIAL:
                    if ("java/util/concurrent/ConcurrentHashMap".equals(getClassConstantOperand()) && "<init>".equals(getNameConstantOperand())) {
                        userValue = CCIUserValue.CONCURRENT_HASHMAP;
                    }
                break;

                case INVOKEINTERFACE:
                case INVOKEVIRTUAL:
                    if ("get".equals(getNameConstantOperand())) {
                        if (stack.getStackDepth() >= 2) {
                            OpcodeStack.Item itm = stack.getStackItem(1);
                            if (itm.getUserValue() == CCIUserValue.CONCURRENT_HASHMAP) {
                                userValue = CCIUserValue.CONCURRENT_HASHMAP_VALUE;
                            }
                        }
                    } else if ("put".equals(getNameConstantOperand())) {
                        if (stack.getStackDepth() >= 3) {
                            OpcodeStack.Item mapItem = stack.getStackItem(2);
                            OpcodeStack.Item valueItem = stack.getStackItem(0);

                            if (mapItem.getUserValue() == CCIUserValue.CONCURRENT_HASHMAP) {
                                bugReporter.reportBug(new BugInstance(this, BugType.CCI_CONCURRENT_COLLECTION_ISSUES_USE_PUT_IS_RACY.name(), NORMAL_PRIORITY)
                                        .addClass(this).addMethod(this).addSourceLine(this));
                            }
                        }
                    }
                break;

                case PUTFIELD:
                case PUTSTATIC:
                    if (stack.getStackDepth() >= 1) {
                        OpcodeStack.Item itm = stack.getStackItem(0);
                        CCIUserValue uv = (CCIUserValue) itm.getUserValue();
                        fieldUserValues.put(getNameConstantOperand(), uv);
                    }
                break;

                case GETFIELD:
                case GETSTATIC:
                    userValue = fieldUserValues.get(getNameConstantOperand());
                break;
            }

        } finally {
            stack.sawOpcode(this, seen);
            if ((userValue != null) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item itm = stack.getStackItem(0);
                itm.setUserValue(userValue);
            }
        }
    }
}
