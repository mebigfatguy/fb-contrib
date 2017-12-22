/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2017 Dave Brosius
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.CodeException;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XMethod;

/**
 * finds methods that create and populate collections, and while knowing the end size of those collections, does not pre allocate the collection to be big
 * enough. This just causes unneeded reallocations putting strain on the garbage collector.
 */
@CustomUserValue
public class PresizeCollections extends BytecodeScanningDetector {

    private static final Set<String> PRESIZEABLE_COLLECTIONS = UnmodifiableSet.create("java/util/ArrayBlockingQueue", "java/util/ArrayDeque",
            "java/util/ArrayList", "java/util/HashMap", "java/util/HashSet", "java/util/LinkedBlockingQueue", "java/util/LinkedHashMap",
            "java/util/LinkedHashSet", "java/util/PriorityBlockingQueue", "java/util/PriorityQueue", "java/util/Vector");

    private static final Set<FQMethod> STATIC_COLLECTION_FACTORIES = UnmodifiableSet.create(
    // @formatter:off
            new FQMethod("com/google/common/collect/Lists", "newArrayList", "()Ljava/util/ArrayList;"),
            new FQMethod("com/google/common/collect/Sets", "newHashSet", "()Ljava/util/HashSet;"),
            new FQMethod("com/google/common/collect/Maps", "newHashMap", "()Ljava/util/HashMap;")
    // @formatter:on
    );

    private static final Set<FQMethod> UNSIZED_SOURCES = UnmodifiableSet.create(
    // @formatter:off
            new FQMethod("java/util/Enumeration", "hasMoreElements", "()Z"),
            new FQMethod("java/util/StringTokenizer", "hasMoreElements", "()Z"),
            new FQMethod("java/util/StringTokenizer", "hasMoreTokens", "()Z")
    // @formatter:on
    );

    private BugReporter bugReporter;
    private OpcodeStack stack;
    private int nextAllocNumber;
    private Map<Comparable<?>, Integer> storeToAllocNumber;
    private Map<Integer, Integer> allocLocation;
    private Map<Integer, List<Integer>> allocToAddPCs;
    private List<CodeRange> optionalRanges;

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
            storeToAllocNumber = new HashMap<>();
            allocLocation = new HashMap<>();
            allocToAddPCs = new HashMap<>();
            optionalRanges = new ArrayList<>();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            storeToAllocNumber = null;
            allocLocation = null;
            allocToAddPCs = null;
            optionalRanges = null;
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
        nextAllocNumber = 1;
        storeToAllocNumber.clear();
        allocLocation.clear();
        allocToAddPCs.clear();
        optionalRanges.clear();

        addExceptionRanges(obj);

        super.visitCode(obj);

        for (List<Integer> pcs : allocToAddPCs.values()) {
            if (pcs.size() > 16) {
                bugReporter.reportBug(new BugInstance(this, BugType.PSC_PRESIZE_COLLECTIONS.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                        .addSourceLine(this, pcs.get(0).intValue()));
            }
        }
    }

    /**
     * implements the visitor to look for creation of collections that are then populated with a known number of elements usually based on another collection,
     * but the new collection is not presized.
     *
     * @param seen
     *            the opcode of the currently parsed instruction
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "CLI_CONSTANT_LIST_INDEX", justification = "Constrained by FindBugs API")
    @Override
    public void sawOpcode(int seen) {
        Integer allocationNumber = null;
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
                            if (SignatureBuilder.SIG_VOID_TO_VOID.equals(signature)) {
                                allocationNumber = Integer.valueOf(nextAllocNumber++);
                                sawAlloc = true;
                            }
                        }
                    }
                break;

                case INVOKEINTERFACE:
                    String methodName = getNameConstantOperand();
                    if ("add".equals(methodName) || "addAll".equals(methodName)) {
                        String signature = getSigConstantOperand();
                        int numArguments = SignatureUtils.getNumParameters(signature);
                        if ((numArguments == 1) && (stack.getStackDepth() > 1)) {
                            OpcodeStack.Item item = stack.getStackItem(1);
                            Integer allocNum = (Integer) item.getUserValue();
                            if (allocNum != null) {
                                if ("addAll".equals(methodName)) {
                                    allocToAddPCs.remove(allocNum);
                                } else {
                                    List<Integer> lines = allocToAddPCs.get(allocNum);
                                    if (lines == null) {
                                        lines = new ArrayList<>();
                                        allocToAddPCs.put(allocNum, lines);
                                    }
                                    lines.add(Integer.valueOf(getPC()));
                                }
                            }
                        }
                    } else if ("put".equals(methodName) || "putAll".equals(methodName)) {
                        String signature = getSigConstantOperand();
                        int numArguments = SignatureUtils.getNumParameters(signature);
                        if ((numArguments == 2) && (stack.getStackDepth() > 2)) {
                            OpcodeStack.Item item = stack.getStackItem(2);
                            Integer allocNum = (Integer) item.getUserValue();
                            if (allocNum != null) {
                                if ("putAll".equals(methodName)) {
                                    allocToAddPCs.remove(allocNum);
                                } else {
                                    List<Integer> lines = allocToAddPCs.get(allocNum);
                                    if (lines == null) {
                                        lines = new ArrayList<>();
                                        allocToAddPCs.put(allocNum, lines);
                                    }
                                    lines.add(Integer.valueOf(getPC()));
                                }
                            }
                        }
                    }
                break;

                case INVOKESTATIC:
                    FQMethod fqm = new FQMethod(getClassConstantOperand(), getNameConstantOperand(), getSigConstantOperand());
                    if (STATIC_COLLECTION_FACTORIES.contains(fqm)) {
                        allocationNumber = Integer.valueOf(nextAllocNumber++);
                        sawAlloc = true;
                    }
                break;

                case LOOKUPSWITCH:
                case TABLESWITCH:
                    int[] offsets = getSwitchOffsets();
                    if (offsets.length >= 2) {
                        int pc = getPC();
                        int thisOffset = pc + offsets[0];
                        for (int o = 0; o < (offsets.length - 1); o++) {
                            int nextOffset = offsets[o + 1] + pc;
                            CodeRange db = new CodeRange(thisOffset, nextOffset);
                            optionalRanges.add(db);
                            thisOffset = nextOffset;
                        }
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

                        if (branchBasedOnUnsizedObject(seen)) {
                            break;
                        }

                        int target = getBranchTarget();
                        Iterator<Map.Entry<Integer, List<Integer>>> it = allocToAddPCs.entrySet().iterator();
                        while (it.hasNext()) {
                            Map.Entry<Integer, List<Integer>> entry = it.next();
                            Integer allocLoc = allocLocation.get(entry.getKey());
                            if ((allocLoc != null) && (allocLoc.intValue() < target)) {
                                List<Integer> pcs = entry.getValue();
                                for (int pc : pcs) {
                                    if (pc > target) {
                                        int numDownBranches = countDownBranches(allocLoc.intValue(), pc);
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
                        CodeRange db = new CodeRange(getPC(), getBranchTarget());
                        optionalRanges.add(db);
                    }
                break;

                case IFNULL:
                case IFNONNULL:
                case IFGE:
                case IFGT:
                    // null check and >, >= branches are hard to presize
                    if (getBranchOffset() > 0) {
                        CodeRange db = new CodeRange(getPC(), getBranchTarget());
                        optionalRanges.add(db);
                    }
                break;

                case ASTORE:
                case ASTORE_0:
                case ASTORE_1:
                case ASTORE_2:
                case ASTORE_3: {
                    if (stack.getStackDepth() > 0) {
                        Integer alloc = (Integer) stack.getStackItem(0).getUserValue();
                        if (alloc != null) {
                            storeToAllocNumber.put(getRegisterOperand(), alloc);
                        }
                    }
                }
                break;

                case ALOAD:
                case ALOAD_0:
                case ALOAD_1:
                case ALOAD_2:
                case ALOAD_3: {
                    allocationNumber = storeToAllocNumber.get(getRegisterOperand());
                }
                break;

                case PUTFIELD: {
                    if (stack.getStackDepth() > 0) {
                        Integer alloc = (Integer) stack.getStackItem(0).getUserValue();
                        if (alloc != null) {
                            storeToAllocNumber.put(getNameConstantOperand(), alloc);
                        }
                    }
                }
                break;

                case GETFIELD: {
                    allocationNumber = storeToAllocNumber.get(getNameConstantOperand());
                }

            }
        } finally {
            stack.sawOpcode(this, seen);
            if ((allocationNumber != null) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item item = stack.getStackItem(0);
                item.setUserValue(allocationNumber);
                if (sawAlloc) {
                    allocLocation.put(allocationNumber, Integer.valueOf(getPC()));
                }
            }
        }
    }

    private int countDownBranches(int allocationPos, int addPC) {
        int numDownBranches = 0;
        for (CodeRange db : optionalRanges) {
            if ((db.fromPC > allocationPos) && (db.fromPC < addPC) && (db.toPC > addPC)) {
                numDownBranches++;
            }
        }

        return numDownBranches;
    }

    /**
     * adds optionalRanges for all try/catch blocks
     *
     * @param c
     *            the currently parsed code object
     */
    private void addExceptionRanges(Code c) {

        Map<CodeRange, List<Integer>> ranges = new HashMap<>();
        CodeException[] ces = c.getExceptionTable();
        if (ces != null) {
            for (CodeException ce : ces) {
                CodeRange range = new CodeRange(ce.getStartPC(), ce.getEndPC());
                List<Integer> handlers = ranges.get(range);
                if (handlers == null) {
                    handlers = new ArrayList<>(6);
                    ranges.put(range, handlers);
                }
                handlers.add(ce.getHandlerPC());
            }
        }

        for (Map.Entry<CodeRange, List<Integer>> entry : ranges.entrySet()) {
            optionalRanges.add(entry.getKey());
            List<Integer> handlers = entry.getValue();
            Collections.sort(handlers);
            for (int h = 0; h < (handlers.size() - 1); h++) {
                optionalRanges.add(new CodeRange(handlers.get(h), handlers.get(h + 1)));
            }
        }
    }

    /**
     * returns if the conditional is based on a method call from an object that has no sizing to determine what presize should be.
     *
     * @param seen
     *            the current visited opcode
     * @return whether this conditional is based on a unsized object
     */
    private boolean branchBasedOnUnsizedObject(int seen) {
        if ((seen != IFNE) || (stack.getStackDepth() == 0)) {
            return false;
        }

        OpcodeStack.Item itm = stack.getStackItem(0);
        XMethod xm = itm.getReturnValueOf();
        if (xm == null) {
            return false;
        }

        FQMethod fqm = new FQMethod(xm.getClassName().replace('.', '/'), xm.getName(), xm.getSignature());

        return UNSIZED_SOURCES.contains(fqm);
    }

    static class CodeRange {
        public int fromPC;
        public int toPC;

        CodeRange(int from, int to) {
            fromPC = from;
            toPC = to;
        }

        @Override
        public int hashCode() {
            return fromPC ^ toPC;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CodeRange)) {
                return false;
            }

            CodeRange that = (CodeRange) o;
            return (fromPC == that.fromPC) && (toPC == that.toPC);
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }
}
