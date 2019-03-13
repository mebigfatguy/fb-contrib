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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.Const;
import javax.annotation.Nullable;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.CodeException;
import org.apache.bcel.classfile.JavaClass;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.QMethod;
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
            new FQMethod("java/util/Enumeration", "hasMoreElements", SignatureBuilder.SIG_VOID_TO_BOOLEAN),
            new FQMethod("java/util/StringTokenizer", "hasMoreElements", SignatureBuilder.SIG_VOID_TO_BOOLEAN),
            new FQMethod("java/util/StringTokenizer", "hasMoreTokens", SignatureBuilder.SIG_VOID_TO_BOOLEAN),
            new FQMethod("java/util/regex/Matcher", "find", SignatureBuilder.SIG_VOID_TO_BOOLEAN),
            new FQMethod("java/util/regex/Matcher", "find", SignatureBuilder.SIG_INT_TO_BOOLEAN)
    // @formatter:on
    );

    private static final FQMethod ITERATOR_HASNEXT = new FQMethod("java/util/Iterator", "hasNext", SignatureBuilder.SIG_VOID_TO_BOOLEAN);

    private static final QMethod ITERATOR_METHOD = new QMethod("iterator", "()Ljava/util/Iterator;");

    private static final FQMethod HASHMAP_SIZED_CTOR = new FQMethod("java/util/HashMap", "<init>", SignatureBuilder.SIG_INT_TO_VOID);
    private static final FQMethod HASHSET_SIZED_CTOR = new FQMethod("java/util/HashSet", "<init>", SignatureBuilder.SIG_INT_TO_VOID);

    private BugReporter bugReporter;
    private JavaClass collectionClass;
    private boolean guavaOnPath;
    private OpcodeStack stack;
    private int nextAllocNumber;
    private Map<Comparable<?>, PSCUserValue> storeToUserValue;
    private Map<Integer, Integer> allocLocation;
    private Map<Integer, List<Integer>> allocToAddPCs;
    private List<CodeRange> optionalRanges;

    public PresizeCollections(BugReporter bugReporter) {
        this.bugReporter = bugReporter;

        try {
            collectionClass = Repository.lookupClass("java/util/Collection");
        } catch (ClassNotFoundException e) {
            bugReporter.reportMissingClass(e);
        }

        try {
            Repository.lookupClass("com/google/common/collect/Maps");
            guavaOnPath = true;
        } catch (ClassNotFoundException e) {
            // don't report. it's ok if the user doesn't use guava :)
            guavaOnPath = false;
        }
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
            storeToUserValue = new HashMap<>();
            allocLocation = new HashMap<>();
            allocToAddPCs = new HashMap<>();
            optionalRanges = new ArrayList<>();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            storeToUserValue = null;
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
        storeToUserValue.clear();
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

        PSCUserValue userValue = null;
        boolean sawAlloc = false;

        try {
            stack.precomputation(this);

            switch (seen) {
                case Const.INVOKESPECIAL:
                    String clsName = getClassConstantOperand();
                    if (PRESIZEABLE_COLLECTIONS.contains(clsName)) {
                        String methodName = getNameConstantOperand();
                        if (Values.CONSTRUCTOR.equals(methodName)) {
                            String signature = getSigConstantOperand();
                            if (SignatureBuilder.SIG_VOID_TO_VOID.equals(signature)) {
                                userValue = new PSCUserValue(Integer.valueOf(nextAllocNumber++));
                                sawAlloc = true;
                            } else if (guavaOnPath && (stack.getStackDepth() > 0)) {
                                FQMethod fqMethod = new FQMethod(clsName, methodName, signature);
                                if (HASHMAP_SIZED_CTOR.equals(fqMethod) || HASHSET_SIZED_CTOR.equals(fqMethod)) {
                                    OpcodeStack.Item itm = stack.getStackItem(0);
                                    XMethod xm = itm.getReturnValueOf();
                                    if ((xm != null) && "size".equals(xm.getMethodDescriptor().getName())) {
                                        bugReporter.reportBug(new BugInstance(this, BugType.PSC_SUBOPTIMAL_COLLECTION_SIZING.name(), NORMAL_PRIORITY)
                                                .addClass(this).addMethod(this).addSourceLine(this));
                                    }
                                }
                            }
                        }
                    }
                break;

                case Const.INVOKEINTERFACE:
                    String methodName = getNameConstantOperand();

                    if (ITERATOR_METHOD.equals(new QMethod(methodName, getSigConstantOperand()))) {
                        if (stack.getStackDepth() > 0) {
                            OpcodeStack.Item itm = stack.getStackItem(0);
                            userValue = isSizedSource(itm);
                        }
                    } else if (ITERATOR_HASNEXT.equals(new FQMethod(getClassConstantOperand(), methodName, getSigConstantOperand()))) {
                        if (stack.getStackDepth() > 0) {
                            OpcodeStack.Item itm = stack.getStackItem(0);
                            userValue = (PSCUserValue) itm.getUserValue();
                        }
                    } else if ("add".equals(methodName) || "addAll".equals(methodName)) {
                        String signature = getSigConstantOperand();
                        int numArguments = SignatureUtils.getNumParameters(signature);
                        if ((numArguments == 1) && (stack.getStackDepth() > 1)) {
                            OpcodeStack.Item item = stack.getStackItem(1);
                            PSCUserValue uv = (PSCUserValue) item.getUserValue();
                            if (uv != null) {
                                Integer allocNum = uv.getAllocationNumber();
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
                        }
                    } else if ("put".equals(methodName) || "putAll".equals(methodName)) {
                        String signature = getSigConstantOperand();
                        int numArguments = SignatureUtils.getNumParameters(signature);
                        if ((numArguments == 2) && (stack.getStackDepth() > 2)) {
                            OpcodeStack.Item item = stack.getStackItem(2);
                            PSCUserValue uv = (PSCUserValue) item.getUserValue();
                            if (uv != null) {
                                Integer allocNum = uv.getAllocationNumber();
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
                    }
                break;

                case Const.INVOKESTATIC:
                    FQMethod fqm = new FQMethod(getClassConstantOperand(), getNameConstantOperand(), getSigConstantOperand());
                    if (STATIC_COLLECTION_FACTORIES.contains(fqm)) {
                        userValue = new PSCUserValue(Integer.valueOf(nextAllocNumber++));
                        sawAlloc = true;
                    }
                break;

                case Const.LOOKUPSWITCH:
                case Const.TABLESWITCH:
                    int[] offsets = getSwitchOffsets();
                    if (offsets.length >= 2) {
                        int pc = getPC();
                        int thisOffset = pc + offsets[0];
                        for (int o = 0; o < (offsets.length - 1); o++) {
                            int nextOffset = offsets[o + 1] + pc;
                            CodeRange db = new CodeRange(thisOffset, nextOffset, false);
                            optionalRanges.add(db);
                            thisOffset = nextOffset;
                        }
                    }
                break;

                case Const.IFEQ:
                case Const.IFNE:
                case Const.IFLT:
                case Const.IFLE:
                case Const.IF_ICMPEQ:
                case Const.IF_ICMPNE:
                case Const.IF_ICMPLT:
                case Const.IF_ICMPGE:
                case Const.IF_ICMPGT:
                case Const.IF_ICMPLE:
                case Const.IF_ACMPEQ:
                case Const.IF_ACMPNE:
                case Const.GOTO:
                case Const.GOTO_W:
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
                                        if (hasSinglePossiblySizedBranch(allocLoc.intValue(), pc)) {
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
                        CodeRange db = new CodeRange(getPC(), getBranchTarget(), !branchBasedOnUnsizedObject(seen));
                        optionalRanges.add(db);
                    }
                break;

                case Const.IFNULL:
                case Const.IFNONNULL:
                case Const.IFGE:
                case Const.IFGT:
                    // null check and >, >= branches are hard to presize
                    if (getBranchOffset() > 0) {
                        CodeRange db = new CodeRange(getPC(), getBranchTarget(), false);
                        optionalRanges.add(db);
                    }
                break;

                case Const.ASTORE:
                case Const.ASTORE_0:
                case Const.ASTORE_1:
                case Const.ASTORE_2:
                case Const.ASTORE_3: {
                    if (stack.getStackDepth() > 0) {
                        PSCUserValue uv = (PSCUserValue) stack.getStackItem(0).getUserValue();
                        if (uv != null) {
                            storeToUserValue.put(getRegisterOperand(), uv);
                        }
                    }
                }
                break;

                case Const.ALOAD:
                case Const.ALOAD_0:
                case Const.ALOAD_1:
                case Const.ALOAD_2:
                case Const.ALOAD_3: {
                    userValue = storeToUserValue.get(getRegisterOperand());
                }
                break;

                case Const.PUTFIELD: {
                    if (stack.getStackDepth() > 0) {
                        PSCUserValue uv = (PSCUserValue) stack.getStackItem(0).getUserValue();
                        if (uv != null) {
                            storeToUserValue.put(getNameConstantOperand(), uv);
                        }
                    }
                }
                break;

                case Const.GETFIELD: {
                    userValue = storeToUserValue.get(getNameConstantOperand());
                }
                break;

            }
        } finally {
            stack.sawOpcode(this, seen);
            if ((userValue != null) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item item = stack.getStackItem(0);
                item.setUserValue(userValue);
                if (sawAlloc) {
                    allocLocation.put(userValue.getAllocationNumber(), Integer.valueOf(getPC()));
                }
            }
        }
    }

    private boolean hasSinglePossiblySizedBranch(int allocationPos, int addPC) {
        int numDownBranches = 0;
        for (CodeRange db : optionalRanges) {
            if ((db.fromPC > allocationPos) && (db.fromPC < addPC) && (db.toPC > addPC)) {
                numDownBranches++;

                if (numDownBranches > 1) {
                    return false;
                }

                if (!db.isPossiblySized) {
                    return false;
                }
            }
        }

        return numDownBranches == 1;
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
                CodeRange range = new CodeRange(ce.getStartPC(), ce.getEndPC(), false);
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
                optionalRanges.add(new CodeRange(handlers.get(h), handlers.get(h + 1), false));
            }
        }
    }

    /**
     * returns if the conditional is based on a method call from an object that has no sizing to determine what presize should be. it's possible the correct
     * implementation should just return true, if <code>if ((seen != IFNE) || (stack.getStackDepth() == 0))</code>
     *
     * @param seen
     *            the current visited opcode
     * @return whether this conditional is based on a unsized object
     */
    private boolean branchBasedOnUnsizedObject(int seen) {
        if ((seen == Const.IF_ACMPEQ) || (seen == Const.IF_ACMPNE)) {
            return true;
        }

        if (((seen != Const.IFNE) && (seen != Const.IFEQ)) || (stack.getStackDepth() == 0)) {
            return false;
        }

        OpcodeStack.Item itm = stack.getStackItem(0);
        XMethod xm = itm.getReturnValueOf();
        if (xm == null) {
            return false;
        }

        FQMethod fqm = new FQMethod(xm.getClassName().replace('.', '/'), xm.getName(), xm.getSignature());

        if (ITERATOR_HASNEXT.equals(fqm)) {
            PSCUserValue uv = (PSCUserValue) itm.getUserValue();
            if (uv == null) {
                return true;
            }

            return !uv.hasSizedSource();
        }

        return UNSIZED_SOURCES.contains(fqm);
    }

    @Nullable
    private PSCUserValue isSizedSource(OpcodeStack.Item itm) {
        try {
            String sig = itm.getSignature();
            JavaClass cls = Repository.lookupClass(sig.substring(1, sig.length() - 1));
            if (cls.instanceOf(collectionClass)) {
                return new PSCUserValue(true);
            }

        } catch (ClassNotFoundException e) {
            bugReporter.reportMissingClass(e);
        }

        return null;
    }

    static class CodeRange {
        public int fromPC;
        public int toPC;
        public boolean isPossiblySized;

        CodeRange(int from, int to, boolean isSized) {
            fromPC = from;
            toPC = to;
            isPossiblySized = isSized;
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

    static class PSCUserValue {

        private Integer allocationNumber;
        private boolean hasSizedSource;

        public PSCUserValue(Integer allocNumber) {
            allocationNumber = allocNumber;
        }

        public PSCUserValue(boolean sizedSource) {
            hasSizedSource = sizedSource;
        }

        public Integer getAllocationNumber() {
            return allocationNumber;
        }

        public boolean hasSizedSource() {
            return hasSizedSource;
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }
}
