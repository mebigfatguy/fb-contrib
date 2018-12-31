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

import org.apache.bcel.classfile.Code;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;

/**
 * looks for code that builds an array of values from a collection, by manually looping over the elements of the collection, and adding them to the array. It is
 * simpler and cleaner to use mycollection.toArray(new type[mycollection.size()].
 */
@CustomUserValue
public class UseToArray extends AbstractCollectionScanningDetector {

    private Map<Integer, Object> userValues;

    /**
     * constructs a UTA detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public UseToArray(BugReporter bugReporter) {
        super(bugReporter, Values.SLASHED_JAVA_UTIL_COLLECTION);
    }

    /**
     * implements the visitor to reset the uservalues
     *
     * @param obj
     *            the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        try {
            userValues = new HashMap<>();
            super.visitCode(obj);
        } finally {
            userValues = null;
        }
    }

    /**
     * implements the visitor to look for manual copying of collections to arrays
     *
     * @param seen
     *            the opcode of the currently parsed instruction
     */
    @Override
    public void sawOpcode(int seen) {
        int reg = -1;
        Object uValue = null;
        boolean sawAlias = false;
        boolean sawLoad = false;
        boolean sawNewArray = false;

        try {
            stack.precomputation(this);

            if (seen == INVOKEINTERFACE) {
                String methodName = getNameConstantOperand();
                String signature = getSigConstantOperand();
                if ("size".equals(methodName) && SignatureBuilder.SIG_VOID_TO_INT.equals(signature)) {
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item itm = stack.getStackItem(0);
                        reg = isLocalCollection(itm);
                        if (reg >= 0) {
                            sawAlias = true;
                        }
                    }
                } else if ("get".equals(methodName) && SignatureBuilder.SIG_INT_TO_OBJECT.equals(signature)) {
                    if (stack.getStackDepth() > 1) {
                        OpcodeStack.Item itm = stack.getStackItem(1);
                        reg = isLocalCollection(itm);
                        if (reg >= 0) {
                            sawAlias = true;
                        }
                    }
                } else if (("keySet".equals(methodName) || "values".equals(methodName) || "iterator".equals(methodName) || "next".equals(methodName))
                        && (stack.getStackDepth() > 0)) {
                    OpcodeStack.Item itm = stack.getStackItem(0);
                    reg = isLocalCollection(itm);
                    if (reg >= 0) {
                        sawAlias = true;
                    }
                }
            } else if (OpcodeUtils.isIStore(seen) || OpcodeUtils.isAStore(seen)) {
                if (stack.getStackDepth() > 0) {
                    uValue = stack.getStackItem(0).getUserValue();
                    userValues.put(Integer.valueOf(RegisterUtils.getStoreReg(this, seen)), uValue);
                }
            } else if (OpcodeUtils.isILoad(seen) || OpcodeUtils.isALoad(seen)) {
                sawLoad = true;
            } else if (seen == ANEWARRAY) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item itm = stack.getStackItem(0);
                    uValue = itm.getUserValue();
                    sawNewArray = true;
                }
            } else if (seen == IF_ICMPGE) {
                if (stack.getStackDepth() > 1) {
                    OpcodeStack.Item itm1 = stack.getStackItem(1);
                    OpcodeStack.Item itm2 = stack.getStackItem(0);
                    reg = itm1.getRegisterNumber();
                    if ((reg >= 0) && (itm1.couldBeZero())) {
                        uValue = itm2.getUserValue();
                        if (uValue != null) {
                            userValues.put(Integer.valueOf(reg), uValue);
                        }
                    }
                }
            } else if ((seen >= IASTORE) && (seen <= SASTORE)) {
                if (stack.getStackDepth() > 2) {
                    OpcodeStack.Item arItem = stack.getStackItem(2);
                    OpcodeStack.Item idxItem = stack.getStackItem(1);
                    OpcodeStack.Item valueItem = stack.getStackItem(0);
                    reg = isLocalCollection(arItem);
                    if ((reg >= 0) && (idxItem.getUserValue() != null) && (valueItem.getUserValue() != null)) {
                        bugReporter.reportBug(
                                new BugInstance(this, BugType.UTA_USE_TO_ARRAY.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
                    }
                }
            } else if ((seen == CHECKCAST) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item itm = stack.getStackItem(0);
                uValue = itm.getUserValue();
                if (uValue instanceof Integer) {
                    reg = ((Integer) uValue).intValue();
                    sawAlias = true;
                }
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        } finally {
            TernaryPatcher.pre(stack, seen);
            stack.sawOpcode(this, seen);
            TernaryPatcher.post(stack, seen);
            if (sawAlias) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item itm = stack.getStackItem(0);
                    itm.setUserValue(Integer.valueOf(reg));
                }
            } else if (sawLoad) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item itm = stack.getStackItem(0);
                    reg = itm.getRegisterNumber();
                    if (reg >= 0) {
                        uValue = userValues.get(Integer.valueOf(reg));
                        itm.setUserValue(uValue);
                    }
                }
            } else if (sawNewArray && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item itm = stack.getStackItem(0);
                itm.setUserValue(uValue);
            }
        }
    }

}
