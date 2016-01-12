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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * finds methods that create and populate collections, and while knowing the end
 * size of those collections, does not pre allocate the collection to be big
 * enough. This just causes unneeded reallocations putting strain on the garbage
 * collector.
 */
@CustomUserValue
public class PresizeCollections extends BytecodeScanningDetector {

    private static final Set<String> PRESIZEABLE_COLLECTIONS = UnmodifiableSet.create(
        "java/util/ArrayBlockingQueue",
        "java/util/ArrayDeque",
        "java/util/ArrayList",
        "java/util/HashMap",
        "java/util/HashSet",
        "java/util/LinkedBlockingQueue",
        "java/util/LinkedHashMap",
        "java/util/LinkedHashSet",
        "java/util/PriorityBlockingQueue",
        "java/util/PriorityQueue",
        "java/util/Vector"
    );

    private BugReporter bugReporter;
    private OpcodeStack stack;
    private int allocNumber;
    private Map<Integer, Integer> allocLocation;
    private Map<Integer, List<Integer>> allocToAddPCs;
    private List<DownBranch> downBranches;

    public PresizeCollections(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * overrides the visitor to initialize the opcode stack
     *
     * @param classContext
     *            the context object that holds the JavaClass being parsed
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            allocLocation = new HashMap<Integer, Integer>();
            allocToAddPCs = new HashMap<Integer, List<Integer>>();
            downBranches = new ArrayList<DownBranch>();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            allocLocation = null;
            allocToAddPCs = null;
            downBranches = null;
        }
    }

    /**
     * implements the visitor to reset the opcode stack
     *
     * @param obj
     *            the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        allocNumber = 0;
        allocLocation.clear();
        allocToAddPCs.clear();
        downBranches.clear();
        super.visitCode(obj);

        for (List<Integer> pcs : allocToAddPCs.values()) {
            if (pcs.size() > 16) {
                bugReporter.reportBug(new BugInstance(this, BugType.PSC_PRESIZE_COLLECTIONS.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                        .addSourceLine(this, pcs.get(0).intValue()));
            }
        }
    }

    /**
     * implements the visitor to look for creation of collections that are then
     * populated with a known number of elements usually based on another
     * collection, but the new collection is not presized.
     *
     * @param seen
     *            the opcode of the currently parsed instruction
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
        value = "CLI_CONSTANT_LIST_INDEX",
        justification = "Constrained by FindBugs API"
    )
    @Override
    public void sawOpcode(int seen) {
        boolean sawAlloc = false;
        try {
            stack.precomputation(this);

            switch (seen) {
            case INVOKESPECIAL:
                String clsName = getClassConstantOperand();
                if (PRESIZEABLE_COLLECTIONS.contains(clsName)) {
                    String methodName = getNameConstantOperand();
                    if (Values.CONSTRUCTOR.equals(methodName)) {
                        String signature = getSigConstantOperand();
                        if ("()V".equals(signature)) {
                            sawAlloc = true;
                        }
                    }
                }
                break;

            case INVOKEINTERFACE:
                String methodName = getNameConstantOperand();
                if ("add".equals(methodName) || "addAll".equals(methodName)) {
                    String signature = getSigConstantOperand();
                    Type[] argTypes = Type.getArgumentTypes(signature);
                    if ((argTypes.length == 1) && (stack.getStackDepth() > 1)) {
                        OpcodeStack.Item item = stack.getStackItem(1);
                        Integer allocNum = (Integer) item.getUserValue();
                        if (allocNum != null) {
                            if ("addAll".equals(methodName)) {
                                allocToAddPCs.remove(allocNum);
                            } else {
                                List<Integer> lines = allocToAddPCs.get(allocNum);
                                if (lines == null) {
                                    lines = new ArrayList<Integer>();
                                    allocToAddPCs.put(allocNum, lines);
                                }
                                lines.add(Integer.valueOf(getPC()));
                            }
                        }
                    }
                } else if ("put".equals(methodName) || "putAll".equals(methodName)) {
                    String signature = getSigConstantOperand();
                    Type[] argTypes = Type.getArgumentTypes(signature);
                    if ((argTypes.length == 2) && (stack.getStackDepth() > 2)) {
                        OpcodeStack.Item item = stack.getStackItem(2);
                        Integer allocNum = (Integer) item.getUserValue();
                        if (allocNum != null) {
                            if ("putAll".equals(methodName)) {
                                allocToAddPCs.remove(allocNum);
                            } else {
                                List<Integer> lines = allocToAddPCs.get(allocNum);
                                if (lines == null) {
                                    lines = new ArrayList<Integer>();
                                    allocToAddPCs.put(allocNum, lines);
                                }
                                lines.add(Integer.valueOf(getPC()));
                            }
                        }
                    }
                }
                break;

            case LOOKUPSWITCH:
            case TABLESWITCH:
                int[] offsets = getSwitchOffsets();
                if (offsets.length > 1) {
                    int secondCase = offsets[1] + getPC();
                    DownBranch db = new DownBranch(getPC(), secondCase);
                    downBranches.add(db);
                }
                break;

            case IFEQ:
            case IFNE:
            case IFLT:
            case IFLE:
            case IF_ICMPEQ:
            case IF_ICMPNE:
            case IF_ICMPLT:
            case IF_ICMPGE:
            case IF_ICMPGT:
            case IF_ICMPLE:
            case IF_ACMPEQ:
            case IF_ACMPNE:
            case GOTO:
            case GOTO_W:
                if (getBranchOffset() < 0) {
                    int target = getBranchTarget();
                    Iterator<Map.Entry<Integer, List<Integer>>> it = allocToAddPCs.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<Integer, List<Integer>> entry = it.next();
                        Integer allocLoc = allocLocation.get(entry.getKey());
                        if ((allocLoc != null) && (allocLoc.intValue() < target)) {
                            List<Integer> pcs = entry.getValue();
                            for (int pc : pcs) {
                                if (pc > target) {
                                    int numDownBranches = countDownBranches(target, pc);
                                    if (numDownBranches == 1) {
                                        bugReporter.reportBug(new BugInstance(this, BugType.PSC_PRESIZE_COLLECTIONS.name(), NORMAL_PRIORITY).addClass(this)
                                                .addMethod(this).addSourceLine(this, pc));
                                        it.remove();
                                    }
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    DownBranch db = new DownBranch(getPC(), getBranchTarget());
                    downBranches.add(db);
                }
                break;

            case IFNULL:
            case IFNONNULL:
            case IFGE:
            case IFGT:
                // null check and >, >= branches are hard to presize
                break;
            }
        } finally {
            stack.sawOpcode(this, seen);
            if (sawAlloc) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    ++allocNumber;
                    item.setUserValue(Integer.valueOf(allocNumber));
                    allocLocation.put(Integer.valueOf(allocNumber), Integer.valueOf(getPC()));
                }
            }
        }
    }

    private int countDownBranches(int loopTop, int addPC) {
        int numDownBranches = 0;
        for (DownBranch db : downBranches) {
            if ((db.fromPC > loopTop) && (db.fromPC < addPC) && (db.toPC > addPC)) {
                numDownBranches++;
            }
        }

        return numDownBranches;
    }

    static class DownBranch {
        public int fromPC;
        public int toPC;

        DownBranch(int from, int to) {
            fromPC = from;
            toPC = to;
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }
}
