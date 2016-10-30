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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.classfile.Code;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * Looks for methods that create DOM Nodes but do not add them to any DOM Document.
 */
public class OrphanedDOMNode extends BytecodeScanningDetector {
    private static final Set<String> domCreationMethods = UnmodifiableSet.create("createAttribute:(Ljava/lang/String;)Lorg/w3c/dom/Attr;",
            "createAttributeNS:(Ljava/lang/String;Ljava/lang/String;)Lorg/w3c/dom/Attr;", "createCDATASection:(Ljava/lang/String;)Lorg/w3c/dom/CDATASection;",
            "createComment:(Ljava/lang/String;)Lorg/w3c/dom/Comment;", "createElement:(Ljava/lang/String;)Lorg/w3c/dom/Element;",
            "createElementNS:(Ljava/lang/String;Ljava/lang/String;)Lorg/w3c/dom/Element;",
            "createProcessingInstruction:(Ljava/lang/String;Ljava/lang/String;)Lorg/w3c/dom/ProcessingInstruction;",
            "createTextNode:(Ljava/lang/String;)Lorg/w3c/dom/Text;");

    private BugReporter bugReporter;
    private OpcodeStack stack;
    private Map<OpcodeStack.Item, Integer> nodeCreations;
    private Map<Integer, Integer> nodeStores;

    /**
     * constructs a ODN detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public OrphanedDOMNode(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to create and clear the stack, node creations and store maps
     *
     * @param classContext
     *            the context object for the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            nodeCreations = new HashMap<>();
            nodeStores = new HashMap<>();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            nodeCreations = null;
            nodeStores = null;
        }
    }

    /**
     * implements the visitor to clear the opcode stack for the next code
     *
     * @param obj
     *            the context object for the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        nodeCreations.clear();
        nodeStores.clear();
        super.visitCode(obj);

        Set<Integer> reportedPCs = new HashSet<>();
        for (Integer pc : nodeCreations.values()) {
            if (!reportedPCs.contains(pc)) {
                bugReporter.reportBug(new BugInstance(this, BugType.ODN_ORPHANED_DOM_NODE.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                        .addSourceLine(this, pc.intValue()));
                reportedPCs.add(pc);
            }
        }
        for (Integer pc : nodeStores.values()) {
            if (!reportedPCs.contains(pc)) {
                bugReporter.reportBug(new BugInstance(this, BugType.ODN_ORPHANED_DOM_NODE.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                        .addSourceLine(this, pc.intValue()));
                reportedPCs.add(pc);
            }
        }
    }

    /**
     * implements the visitor to find DOM based nodes that are allocated but not appended to an existing node (or returned).
     *
     * @param seen
     *            the currently parsed opcode
     */
    @Override
    public void sawOpcode(int seen) {
        boolean sawCreate = false;
        Integer itemPC = null;

        try {
            stack.precomputation(this);

            if (seen == INVOKEINTERFACE) {
                String className = getClassConstantOperand();
                String methodInfo = getNameConstantOperand() + ':' + getSigConstantOperand();
                if ("org/w3c/dom/Document".equals(className) && domCreationMethods.contains(methodInfo)) {
                    sawCreate = true;
                    itemPC = Integer.valueOf(getPC());
                }
            } else if ((seen == ASTORE) || ((seen >= ASTORE_0) && (seen <= ASTORE_3))) {
                Integer pc = findDOMNodeCreationPoint(0);
                int reg = RegisterUtils.getAStoreReg(this, seen);
                if (pc != null) {
                    nodeStores.put(Integer.valueOf(reg), pc);
                } else {
                    nodeStores.remove(Integer.valueOf(reg));
                }
            } else if (seen == PUTFIELD) {
                // Stores to member variables are assumed ok
                findDOMNodeCreationPoint(0);
            } else if (OpcodeUtils.isALoad(seen)) {
                int reg = RegisterUtils.getALoadReg(this, seen);
                itemPC = nodeStores.get(Integer.valueOf(reg));
                if (itemPC != null) {
                    sawCreate = true;
                }
            } else if ((seen == ARETURN) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item itm = stack.getStackItem(0);
                int reg = itm.getRegisterNumber();
                nodeCreations.remove(itm);
                nodeStores.remove(Integer.valueOf(reg));
            }

            if (!sawCreate && ((seen == INVOKEINTERFACE) || (seen == INVOKEVIRTUAL) || (seen == INVOKESTATIC) || (seen == INVOKESPECIAL))) {
                String methodSig = getSigConstantOperand();
                int argCount = SignatureUtils.getNumParameters(methodSig);
                if (stack.getStackDepth() >= argCount) {
                    for (int a = 0; a < argCount; a++) {
                        OpcodeStack.Item itm = stack.getStackItem(a);
                        if (nodeCreations.containsKey(itm)) {
                            int reg = itm.getRegisterNumber();
                            nodeCreations.remove(itm);
                            nodeStores.remove(Integer.valueOf(reg));
                        }
                    }
                    if ((seen != INVOKESTATIC) && (stack.getStackDepth() > argCount)) {
                        nodeCreations.remove(stack.getStackItem(argCount));
                    }
                }
            }
        } finally {
            stack.sawOpcode(this, seen);
            if (sawCreate && (stack.getStackDepth() > 0)) {
                nodeCreations.put(stack.getStackItem(0), itemPC);
            }
        }
    }

    /**
     * returns the pc where this DOM Node was created, or null if this isn't a DOM node that was created
     *
     * @param index
     *            the index into the stack of the item to be checked
     *
     * @return the pc where this NODE was created, or null
     */
    private Integer findDOMNodeCreationPoint(int index) {
        if (stack.getStackDepth() <= index) {
            return null;
        }

        return nodeCreations.remove(stack.getStackItem(index));
    }
}
