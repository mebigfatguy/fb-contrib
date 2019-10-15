/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2019 Bhaskar Maddala
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

import org.apache.bcel.Const;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for various issues with concurrent collections including
 * <ul>
 * <li>calls to checking and inserting a collection into a key on null, instead
 * of using putIfAbsent</li>
 * </ul>
 */
@CustomUserValue
public class ConcurrentCollectionIssues extends BytecodeScanningDetector {

    private final BugReporter bugReporter;
    private JavaClass collectionClass;
    private JavaClass mapClass;
    private OpcodeStack stack;
    private Map<String, CCIUserValue> fieldUserValues;
    private int endNullCheckPC;

    private enum CCIUserValue {
        CONCURRENT_HASHMAP, CONCURRENT_HASHMAP_VALUE;
    };

    /**
     * constructs a CCI detector given the reporter to report bugs on
     *
     * @param bugReporter the sync of bug reports
     */
    public ConcurrentCollectionIssues(final BugReporter bugReporter) {
        this.bugReporter = bugReporter;

        try {
            collectionClass = Repository.lookupClass(Values.SLASHED_JAVA_UTIL_COLLECTION);
            mapClass = Repository.lookupClass(Values.SLASHED_JAVA_UTIL_MAP);
        } catch (ClassNotFoundException e) {
            bugReporter.reportMissingClass(e);
        }
    }

    /**
     * overrides the visitor to initialize the opcode stack
     *
     * @param classContext the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {

        try {
            if ((collectionClass == null) || (mapClass == null)) {
                return;
            }
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
     * @param obj the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        endNullCheckPC = -1;

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
            case Const.INVOKESPECIAL:
                if ("java/util/concurrent/ConcurrentHashMap".equals(getClassConstantOperand())
                        && Values.CONSTRUCTOR.equals(getNameConstantOperand())) {
                    userValue = CCIUserValue.CONCURRENT_HASHMAP;
                }
                break;

            case Const.INVOKEINTERFACE:
            case Const.INVOKEVIRTUAL:
                if ("get".equals(getNameConstantOperand())) {
                    if (stack.getStackDepth() >= 2) {
                        OpcodeStack.Item itm = stack.getStackItem(1);
                        if (itm.getUserValue() == CCIUserValue.CONCURRENT_HASHMAP) {
                            userValue = CCIUserValue.CONCURRENT_HASHMAP_VALUE;
                        }
                    }
                } else if ("put".equals(getNameConstantOperand()) && (endNullCheckPC > getPC())
                        && (stack.getStackDepth() >= 3)) {
                    OpcodeStack.Item mapItem = stack.getStackItem(2);

                    if (mapItem.getUserValue() == CCIUserValue.CONCURRENT_HASHMAP) {
                        OpcodeStack.Item valueItem = stack.getStackItem(0);
                        JavaClass valueClass = valueItem.getJavaClass();
                        if ((valueClass != null)
                                && (valueClass.instanceOf(collectionClass) || valueClass.instanceOf(mapClass))) {

                            bugReporter.reportBug(new BugInstance(this,
                                    BugType.CCI_CONCURRENT_COLLECTION_ISSUES_USE_PUT_IS_RACY.name(), NORMAL_PRIORITY)
                                            .addClass(this).addMethod(this).addSourceLine(this));
                        }
                    }
                }
                break;

            case Const.PUTFIELD:
            case Const.PUTSTATIC:
                if (stack.getStackDepth() >= 1) {
                    OpcodeStack.Item itm = stack.getStackItem(0);
                    CCIUserValue uv = (CCIUserValue) itm.getUserValue();
                    if (uv != null) {
                        fieldUserValues.put(getNameConstantOperand(), uv);
                    } else {
                        fieldUserValues.remove(getNameConstantOperand());
                    }
                }
                break;

            case Const.GETFIELD:
            case Const.GETSTATIC:
                userValue = fieldUserValues.get(getNameConstantOperand());
                break;

            case Const.IFNONNULL:
                if ((getBranchOffset() > 0) && (stack.getStackDepth() > 0)) {
                    OpcodeStack.Item itm = stack.getStackItem(0);
                    if (itm.getUserValue() == CCIUserValue.CONCURRENT_HASHMAP_VALUE) {
                        endNullCheckPC = getBranchTarget();
                    }
                }
                break;
            }

        } catch (ClassNotFoundException e) {
            bugReporter.reportMissingClass(e);
        } finally {
            stack.sawOpcode(this, seen);
            if ((userValue != null) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item itm = stack.getStackItem(0);
                itm.setUserValue(userValue);
            }

            if (getPC() >= endNullCheckPC) {
                endNullCheckPC = -1;
            }
        }
    }
}
