/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2018 Dave Brosius
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
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.CodeException;
import org.apache.bcel.classfile.LocalVariable;
import org.apache.bcel.classfile.LocalVariableTable;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.collect.MethodInfo;
import com.mebigfatguy.fbcontrib.collect.Statistics;
import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XField;

/**
 * looks for variable assignments at a scope larger than its use. In this case, the assignment can be pushed down into the smaller scope to reduce the
 * performance impact of that assignment.
 */
@CustomUserValue
public class BloatedAssignmentScope extends BytecodeScanningDetector {
    private static final Set<String> dangerousAssignmentClassSources = UnmodifiableSet.create(
    //@formatter:off
        "java/io/BufferedInputStream",
        "java/io/DataInput",
        "java/io/DataInputStream",
        "java/io/InputStream",
        "java/io/ObjectInputStream",
        "java/io/BufferedReader",
        "java/io/FileReader",
        "java/io/Reader",
        "javax/nio/channels/Channel",
        "io/netty/channel/Channel"
        //@formatter:on
    );

    private static final Set<FQMethod> dangerousAssignmentMethodSources = UnmodifiableSet.create(
    //@formatter:off
        new FQMethod("java/lang/System", "currentTimeMillis", SignatureBuilder.SIG_VOID_TO_LONG),
        new FQMethod("java/lang/System", "nanoTime", SignatureBuilder.SIG_VOID_TO_LONG),
        new FQMethod("java/util/Calendar", "get", SignatureBuilder.SIG_INT_TO_INT),
        new FQMethod("java/util/GregorianCalendar", "get", SignatureBuilder.SIG_INT_TO_INT),
        new FQMethod("java/util/Iterator", "next", SignatureBuilder.SIG_VOID_TO_OBJECT),
        new FQMethod("java/util/regex/Matcher", "start", SignatureBuilder.SIG_VOID_TO_INT),
        new FQMethod("java/util/concurrent/TimeUnit", "toMillis", SignatureBuilder.SIG_LONG_TO_LONG)
        //@formatter:on
    );

    private static final Set<Pattern> dangerousAssignmentMethodPatterns = UnmodifiableSet.create(
    //@formatter:off
            Pattern.compile(".*serial.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*\\.read[^.]*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*\\.create[^.]*", Pattern.CASE_INSENSITIVE)
        //@formatter:on
    );

    private static final Set<String> dangerousStoreClassSigs = UnmodifiableSet.create("Ljava/util/concurrent/Future;");

    BugReporter bugReporter;
    private OpcodeStack stack;
    private BitSet ignoreRegs;
    private ScopeBlock rootScopeBlock;
    private BitSet tryBlocks;
    private BitSet catchHandlers;
    private BitSet switchTargets;
    private List<Integer> monitorSyncPCs;
    private boolean dontReport;
    private boolean sawDup;
    private boolean sawNull;

    /**
     * constructs a BAS detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public BloatedAssignmentScope(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to create and the clear the register to location map
     *
     * @param classContext
     *            the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            ignoreRegs = new BitSet();
            tryBlocks = new BitSet();
            catchHandlers = new BitSet();
            switchTargets = new BitSet();
            monitorSyncPCs = new ArrayList<>(5);
            stack = new OpcodeStack();
            super.visitClassContext(classContext);
        } finally {
            ignoreRegs = null;
            tryBlocks = null;
            catchHandlers = null;
            switchTargets = null;
            monitorSyncPCs = null;
            stack = null;
        }
    }

    /**
     * implements the visitor to reset the register to location map
     *
     * @param obj
     *            the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        try {

            ignoreRegs.clear();
            Method method = getMethod();
            if (!method.isStatic()) {
                ignoreRegs.set(0);
            }

            int[] parmRegs = RegisterUtils.getParameterRegisters(method);
            for (int parm : parmRegs) {
                ignoreRegs.set(parm);
            }

            rootScopeBlock = new ScopeBlock(0, obj.getLength());
            tryBlocks.clear();
            catchHandlers.clear();
            CodeException[] exceptions = obj.getExceptionTable();
            if (exceptions != null) {
                for (CodeException ex : exceptions) {
                    tryBlocks.set(ex.getStartPC());
                    catchHandlers.set(ex.getHandlerPC());
                }
            }

            switchTargets.clear();
            stack.resetForMethodEntry(this);
            dontReport = false;
            sawDup = false;
            sawNull = false;
            super.visitCode(obj);

            if (!dontReport) {
                rootScopeBlock.findBugs(new HashSet<Integer>());
            }

        } finally {
            rootScopeBlock = null;
        }
    }

    /**
     * implements the visitor to look for variables assigned below the scope in which they are used.
     *
     * @param seen
     *            the opcode of the currently parsed instruction
     */
    @Override
    public void sawOpcode(int seen) {
        UserObject uo = null;
        try {
            stack.precomputation(this);

            int pc = getPC();
            if (tryBlocks.get(pc)) {
                ScopeBlock sb = new ScopeBlock(pc, findCatchHandlerFor(pc));
                sb.setTry();
                rootScopeBlock.addChild(sb);
            }

            if (OpcodeUtils.isStore(seen)) {
                sawStore(seen, pc);
            } else if (OpcodeUtils.isLoad(seen)) {
                sawLoad(seen, pc);
            } else if ((seen == INVOKEVIRTUAL) || (seen == INVOKEINTERFACE)) {
                uo = sawInstanceCall(pc);
            } else if ((seen == INVOKESTATIC) || (seen == INVOKESPECIAL)) {
                uo = sawStaticCall();
            } else if (((seen >= IFEQ) && (seen <= GOTO)) || (seen == IFNULL) || (seen == IFNONNULL) || (seen == GOTO_W)) {
                sawBranch(seen, pc);
            } else if (seen == GETFIELD) {
                uo = sawGetField();
            } else if (seen == PUTFIELD) {
                sawPutField(pc);
            } else if (seen == IINC) {
                sawIINC(pc);
            } else if ((seen == TABLESWITCH) || (seen == LOOKUPSWITCH)) {
                sawSwitch(pc);
            } else if (seen == MONITORENTER) {
                sawMonitorEnter(pc);
            } else if (seen == MONITOREXIT) {
                sawMonitorExit(pc);
            }

            sawDup = seen == DUP;
            sawNull = seen == ACONST_NULL;
        } finally {
            TernaryPatcher.pre(stack, seen);
            stack.sawOpcode(this, seen);
            TernaryPatcher.post(stack, seen);
            if ((uo != null) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item item = stack.getStackItem(0);
                item.setUserValue(uo);
            }
        }
    }

    /**
     * processes a register store by updating the appropriate scope block to mark this register as being stored in the block
     *
     * @param seen
     *            the currently parsed opcode
     * @param pc
     *            the current program counter
     */
    private void sawStore(int seen, int pc) {
        int reg = RegisterUtils.getStoreReg(this, seen);

        if (catchHandlers.get(pc)) {
            ignoreRegs.set(reg);
            ScopeBlock catchSB = findScopeBlock(rootScopeBlock, pc + 1);
            if ((catchSB != null) && (catchSB.getStart() < pc)) {
                ScopeBlock sb = new ScopeBlock(pc, catchSB.getFinish());
                catchSB.setFinish(getPC() - 1);
                rootScopeBlock.addChild(sb);
            }
        } else if (!monitorSyncPCs.isEmpty()) {
            ignoreRegs.set(reg);
        } else if (sawNull) {
            ignoreRegs.set(reg);
        } else if (isRiskyStoreClass(reg)) {
            ignoreRegs.set(reg);
        }

        if (!ignoreRegs.get(reg)) {
            ScopeBlock sb = findScopeBlock(rootScopeBlock, pc);
            if (sb != null) {
                UserObject assoc = null;
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item srcItm = stack.getStackItem(0);
                    assoc = (UserObject) srcItm.getUserValue();
                    if (assoc == null) {
                        if (srcItm.getRegisterNumber() >= 0) {
                            assoc = new UserObject(srcItm.getRegisterNumber());
                        }
                    }
                }

                if ((assoc != null) && assoc.isRisky) {
                    ignoreRegs.set(reg);
                } else {
                    sb.addStore(reg, pc, assoc);
                    if (sawDup) {
                        sb.addLoad(reg, pc);
                    }
                }
            } else {
                ignoreRegs.set(reg);
            }
        }

        ScopeBlock sb = findScopeBlock(rootScopeBlock, pc);
        if (sb != null) {
            sb.markFieldAssociatedWrites(reg);
        }
    }

    /**
     * processes a register IINC by updating the appropriate scope block to mark this register as being stored in the block
     *
     * @param pc
     *            the current program counter
     */
    private void sawIINC(int pc) {
        int reg = getRegisterOperand();
        if (!ignoreRegs.get(reg)) {
            ScopeBlock sb = findScopeBlock(rootScopeBlock, pc);
            if (sb != null) {
                sb.addLoad(reg, pc);
            } else {
                ignoreRegs.set(reg);
            }
        }
        if (catchHandlers.get(pc)) {
            ignoreRegs.set(reg);
        } else if (!monitorSyncPCs.isEmpty()) {
            ignoreRegs.set(reg);
        } else if (sawNull) {
            ignoreRegs.set(reg);
        }

        if (!ignoreRegs.get(reg)) {
            ScopeBlock sb = findScopeBlock(rootScopeBlock, pc);
            if (sb != null) {
                sb.addStore(reg, pc, null);
                if (sawDup) {
                    sb.addLoad(reg, pc);
                }
            } else {
                ignoreRegs.set(reg);
            }
        }
    }

    /**
     * processes a register store by updating the appropriate scope block to mark this register as being read in the block
     *
     * @param seen
     *            the currently parsed opcode
     * @param pc
     *            the current program counter
     */
    private void sawLoad(int seen, int pc) {
        int reg = RegisterUtils.getLoadReg(this, seen);
        if (!ignoreRegs.get(reg)) {
            ScopeBlock sb = findScopeBlock(rootScopeBlock, pc);
            if (sb != null) {
                sb.addLoad(reg, pc);
            } else {
                ignoreRegs.set(reg);
            }
        }
    }

    /**
     * creates a scope block to describe this branch location.
     *
     * @param seen
     *            the currently parsed opcode
     * @param pc
     *            the current program counter
     */
    private void sawBranch(int seen, int pc) {
        int target = getBranchTarget();
        if (target > pc) {
            if ((seen == GOTO) || (seen == GOTO_W)) {
                int nextPC = getNextPC();
                if (!switchTargets.get(nextPC)) {
                    ScopeBlock sb = findScopeBlockWithTarget(rootScopeBlock, pc, nextPC);
                    if (sb == null) {
                        sb = new ScopeBlock(pc, target);
                        sb.setLoop();
                        sb.setGoto();
                        rootScopeBlock.addChild(sb);
                    } else {
                        sb = new ScopeBlock(nextPC, target);
                        sb.setGoto();
                        rootScopeBlock.addChild(sb);
                    }
                }
            } else {
                ScopeBlock sb = findScopeBlockWithTarget(rootScopeBlock, pc, target);
                if ((sb != null) && !sb.isLoop() && !sb.isCase() && !sb.hasChildren()) {
                    if (sb.isGoto()) {
                        ScopeBlock parent = sb.getParent();
                        sb.pushUpLoadStores();
                        if (parent != null) {
                            parent.removeChild(sb);
                        }
                        sb = new ScopeBlock(pc, target);
                        rootScopeBlock.addChild(sb);
                    } else {
                        sb.pushUpLoadStores();
                        sb.setStart(pc);
                    }
                } else {
                    sb = new ScopeBlock(pc, target);
                    rootScopeBlock.addChild(sb);
                }
            }
        } else {
            ScopeBlock sb = findScopeBlock(rootScopeBlock, pc);
            if (sb != null) {
                ScopeBlock parentSB = sb.getParent();
                while (parentSB != null) {
                    if (parentSB.getStart() >= target) {
                        sb = parentSB;
                        parentSB = parentSB.getParent();
                    } else {
                        break;
                    }
                }

                if (sb.getStart() > target) {
                    ScopeBlock previous = findPreviousSiblingScopeBlock(sb);
                    if ((previous != null) && (previous.getStart() >= target)) {
                        sb = previous;
                    }
                }
                sb.setLoop();
            }
        }
    }

    /**
     * creates a new scope block for each case statement
     *
     * @param pc
     *            the current program counter
     */
    private void sawSwitch(int pc) {
        int[] offsets = getSwitchOffsets();
        List<Integer> targets = new ArrayList<>(offsets.length);
        for (int offset : offsets) {
            targets.add(Integer.valueOf(offset + pc));
        }
        Integer defOffset = Integer.valueOf(getDefaultSwitchOffset() + pc);
        if (!targets.contains(defOffset)) {
            targets.add(defOffset);
        }
        Collections.sort(targets);

        Integer lastTarget = targets.get(0);
        for (int i = 1; i < targets.size(); i++) {
            Integer nextTarget = targets.get(i);
            ScopeBlock sb = new ScopeBlock(lastTarget.intValue(), nextTarget.intValue());
            sb.setCase();
            rootScopeBlock.addChild(sb);
            lastTarget = nextTarget;
        }
        for (Integer target : targets) {
            switchTargets.set(target.intValue());
        }
    }

    /**
     * processes a instance method call to see if that call is modifies state or is otherwise'risky', if so mark the variable(s) associated with the caller as
     * not reportable
     *
     * @param pc
     *            the current program counter
     *
     * @return a user object to place on the return value's OpcodeStack item
     */
    @Nullable
    private UserObject sawInstanceCall(int pc) {
        String signature = getSigConstantOperand();
        String name = getNameConstantOperand();

        // this is kind of a wart. there should be a more seamless way to check this
        if ("wasNull".equals(getNameConstantOperand()) && SignatureBuilder.SIG_VOID_TO_BOOLEAN.equals(signature)) {
            dontReport = true;
        }

        if (signature.endsWith(Values.SIG_VOID)) {
            return null;
        }

        MethodInfo mi = Statistics.getStatistics().getMethodStatistics(getClassConstantOperand(), name, signature);

        UserObject uo = new UserObject(getCallingObject(), mi.getModifiesState() || isRiskyMethodCall());

        if (uo.caller != null) {
            ScopeBlock sb = findScopeBlock(rootScopeBlock, pc);
            if (sb != null) {
                sb.removeByAssoc(uo.caller);
            }
        }

        return uo;
    }

    /**
     * processes a static call or initializer by checking to see if the call is risky, and returning a OpcodeStack item user value saying so.
     *
     * @return the user object to place on the OpcodeStack
     */
    @Nullable
    private UserObject sawStaticCall() {

        if (getSigConstantOperand().endsWith(Values.SIG_VOID)) {
            return null;
        }

        return new UserObject(isRiskyMethodCall());
    }

    @Nullable
    private UserObject sawGetField() {
        if (stack.getStackDepth() > 0) {
            OpcodeStack.Item itm = stack.getStackItem(0);
            int reg = itm.getRegisterNumber();

            if (reg >= 0) {
                return new UserObject(reg);
            }
        }

        return null;
    }

    private void sawPutField(int pc) {
        if (stack.getStackDepth() > 1) {
            OpcodeStack.Item itm = stack.getStackItem(1);
            int reg = itm.getRegisterNumber();

            if (reg >= 0) {

                ScopeBlock sb = findScopeBlock(rootScopeBlock, pc);
                if (sb != null) {
                    sb.markFieldAssociatedWrites(reg);
                }
            }
        }
    }

    /**
     * processes a monitor enter call to create a scope block
     *
     * @param pc
     *            the current program counter
     */
    private void sawMonitorEnter(int pc) {
        monitorSyncPCs.add(Integer.valueOf(pc));

        ScopeBlock sb = new ScopeBlock(pc, Integer.MAX_VALUE);
        sb.setSync();
        rootScopeBlock.addChild(sb);
    }

    /**
     * processes a monitor exit to set the end of the already created scope block
     *
     * @param pc
     *            the current program counter
     */
    private void sawMonitorExit(int pc) {
        if (!monitorSyncPCs.isEmpty()) {
            ScopeBlock sb = findSynchronizedScopeBlock(rootScopeBlock, monitorSyncPCs.get(0).intValue());
            if (sb != null) {
                sb.setFinish(pc);
            }
            monitorSyncPCs.remove(monitorSyncPCs.size() - 1);
        }
    }

    /**
     * returns either a register number of a field reference of the object that a method is being called on, or null, if it can't be determined.
     *
     * @return either an Integer for a register, or a String for the field name, or null
     */
    @Nullable
    private Comparable<?> getCallingObject() {
        String sig = getSigConstantOperand();
        if (Values.SIG_VOID.equals(SignatureUtils.getReturnSignature(sig))) {
            return null;
        }

        int numParameters = SignatureUtils.getNumParameters(sig);

        if (stack.getStackDepth() <= numParameters) {
            return null;
        }

        OpcodeStack.Item caller = stack.getStackItem(numParameters);
        UserObject uo = (UserObject) caller.getUserValue();
        if ((uo != null) && (uo.caller != null)) {
            return uo.caller;
        }

        int reg = caller.getRegisterNumber();
        if (reg >= 0) {
            return Integer.valueOf(reg);
        }

        /*
         * We ignore the possibility of two fields with the same name in different classes
         */
        XField f = caller.getXField();
        if (f != null) {
            return f.getName();
        }
        return null;
    }

    /**
     * returns the scope block in which this register was assigned, by traversing the scope block tree
     *
     * @param sb
     *            the scope block to start searching in
     * @param pc
     *            the current program counter
     * @return the scope block or null if not found
     */
    @Nullable
    private ScopeBlock findScopeBlock(ScopeBlock sb, int pc) {

        if ((pc <= sb.getStart()) || (pc >= sb.getFinish())) {
            return null;
        }

        if (sb.children != null) {
            for (ScopeBlock child : sb.children) {
                ScopeBlock foundSb = findScopeBlock(child, pc);
                if (foundSb != null) {
                    return foundSb;
                }
            }
        }
        return sb;
    }

    /**
     * returns an existing scope block that has the same target as the one looked for
     *
     * @param sb
     *            the scope block to start with
     * @param start
     *            the current pc
     * @param target
     *            the target to look for
     *
     * @return the scope block found or null
     */
    private ScopeBlock findScopeBlockWithTarget(ScopeBlock sb, int start, int target) {
        ScopeBlock parentBlock = null;
        if ((sb.startLocation < start) && (sb.finishLocation >= start) && ((sb.finishLocation <= target) || (sb.isGoto() && !sb.isLoop()))) {
            parentBlock = sb;
        }

        if (sb.children != null) {
            for (ScopeBlock child : sb.children) {
                ScopeBlock targetBlock = findScopeBlockWithTarget(child, start, target);
                if (targetBlock != null) {
                    return targetBlock;
                }
            }
        }

        return parentBlock;
    }

    /**
     * looks for the ScopeBlock has the same parent as this given one, but precedes it in the list.
     *
     * @param sb
     *            the scope block to look for the previous scope block
     * @return the previous sibling scope block, or null if doesn't exist
     */
    @Nullable
    private ScopeBlock findPreviousSiblingScopeBlock(ScopeBlock sb) {
        ScopeBlock parent = sb.getParent();
        if (parent == null) {
            return null;
        }

        List<ScopeBlock> children = parent.getChildren();
        if (children == null) {
            return null;
        }

        ScopeBlock lastSibling = null;
        for (ScopeBlock sibling : children) {
            if (sibling.equals(sb)) {
                return lastSibling;
            }
            lastSibling = sibling;
        }

        return null;
    }

    /**
     * finds the scope block that is the active synchronized block
     *
     * @param sb
     *            the parent scope block to start with
     * @param monitorEnterPC
     *            the pc where the current synchronized block starts
     * @return the scope block
     */
    private ScopeBlock findSynchronizedScopeBlock(ScopeBlock sb, int monitorEnterPC) {

        ScopeBlock monitorBlock = sb;

        if (sb.hasChildren()) {
            for (ScopeBlock child : sb.getChildren()) {
                if (child.isSync() && (child.getStart() > monitorBlock.getStart())) {
                    monitorBlock = child;
                    monitorBlock = findSynchronizedScopeBlock(monitorBlock, monitorEnterPC);
                }
            }
        }

        return monitorBlock;
    }

    /**
     * returns the catch handler for a given try block
     *
     * @param pc
     *            the current instruction
     * @return the pc of the handler for this pc if it's the start of a try block, or -1
     *
     */
    private int findCatchHandlerFor(int pc) {
        CodeException[] exceptions = getMethod().getCode().getExceptionTable();
        if (exceptions != null) {
            for (CodeException ex : exceptions) {
                if (ex.getStartPC() == pc) {
                    return ex.getHandlerPC();
                }
            }
        }

        return -1;
    }

    /**
     * holds the description of a scope { } block, be it a for, if, while block
     */
    private class ScopeBlock {
        private ScopeBlock parent;
        private int startLocation;
        private int finishLocation;
        private boolean isLoop;
        private boolean isGoto;
        private boolean isSync;
        private boolean isTry;
        private boolean isCase;
        private Map<Integer, Integer> loads;
        private Map<Integer, Integer> stores;
        private Map<UserObject, Integer> assocs;
        private List<ScopeBlock> children;

        /**
         * constructs a new scope block
         *
         * @param start
         *            the beginning of the block
         * @param finish
         *            the end of the block
         */
        public ScopeBlock(int start, int finish) {
            parent = null;
            startLocation = start;
            finishLocation = finish;
            isLoop = false;
            isGoto = false;
            isSync = false;
            isTry = false;
            isCase = false;
            loads = null;
            stores = null;
            assocs = null;
            children = null;
        }

        /**
         * returns a string representation of the scope block
         *
         * @return a string representation
         */
        @Override
        public String toString() {
            return ToString.build(this, "parent");
        }

        /**
         * returns the scope blocks parent
         *
         * @return the parent of this scope block
         */
        public ScopeBlock getParent() {
            return parent;
        }

        /**
         * returns the children of this scope block
         *
         * @return the scope blocks children
         */
        public List<ScopeBlock> getChildren() {
            return children;
        }

        /**
         * returns the start of the block
         *
         * @return the start of the block
         */
        public int getStart() {
            return startLocation;
        }

        /**
         * returns the end of the block
         *
         * @return the end of the block
         */
        public int getFinish() {
            return finishLocation;
        }

        /**
         * sets the start pc of the block
         *
         * @param start
         *            the start pc
         */
        public void setStart(int start) {
            startLocation = start;
        }

        /**
         * sets the finish pc of the block
         *
         * @param finish
         *            the finish pc
         */
        public void setFinish(int finish) {
            finishLocation = finish;
        }

        public boolean hasChildren() {
            return children != null;
        }

        /**
         * sets that this block is a loop
         */
        public void setLoop() {
            isLoop = true;
        }

        /**
         * returns whether this scope block is a loop
         *
         * @return whether this block is a loop
         */
        public boolean isLoop() {
            return isLoop;
        }

        /**
         * sets that this block was caused from a goto, (an if block exit)
         */
        public void setGoto() {
            isGoto = true;
        }

        /**
         * returns whether this block was caused from a goto
         *
         * @return whether this block was caused by a goto
         */
        public boolean isGoto() {
            return isGoto;
        }

        /**
         * sets that this block was caused from a synchronized block
         */
        public void setSync() {
            isSync = true;
        }

        /**
         * returns whether this block was caused from a synchronized block
         *
         * @return whether this block was caused by a synchronized block
         */
        public boolean isSync() {
            return isSync;
        }

        /**
         * sets that this block was caused from a try block
         */
        public void setTry() {
            isTry = true;
        }

        /**
         * returns whether this block was caused from a try block
         *
         * @return whether this block was caused by a try block
         */
        public boolean isTry() {
            return isTry;
        }

        /**
         * sets that this block was caused from a case block
         */
        public void setCase() {
            isCase = true;
        }

        /**
         * returns whether this block was caused from a case block
         *
         * @return whether this block was caused by a case block
         */
        public boolean isCase() {
            return isCase;
        }

        /**
         * adds the register as a store in this scope block
         *
         * @param reg
         *            the register that was stored
         * @param pc
         *            the instruction that did the store
         * @param assocObject
         *            the the object that is associated with this store, usually the field from which this came
         */
        public void addStore(int reg, int pc, UserObject assocObject) {
            if (stores == null) {
                stores = new HashMap<>(6);
            }

            stores.put(Integer.valueOf(reg), Integer.valueOf(pc));

            if (assocObject != null) {
                if (assocs == null) {
                    assocs = new HashMap<>(6);
                }
                assocs.put(assocObject, Integer.valueOf(reg));
            }
        }

        /**
         * removes stores to registers that where retrieved from method calls on assocObject
         *
         * @param assocObject
         *            the object that a method call was just performed on
         */
        public void removeByAssoc(Object assocObject) {
            if (assocs != null) {
                Integer reg = assocs.remove(assocObject);
                if (reg != null) {
                    if (loads != null) {
                        loads.remove(reg);
                    }
                    if (stores != null) {
                        stores.remove(reg);
                    }
                }
            }
        }

        /**
         * adds the register as a load in this scope block
         *
         * @param reg
         *            the register that was loaded
         * @param pc
         *            the instruction that did the load
         */
        public void addLoad(int reg, int pc) {
            if (loads == null) {
                loads = new HashMap<>(10);
            }

            loads.put(Integer.valueOf(reg), Integer.valueOf(pc));
        }

        /**
         * adds a scope block to this subtree by finding the correct place in the hierarchy to store it
         *
         * @param newChild
         *            the scope block to add to the tree
         */
        public void addChild(ScopeBlock newChild) {
            newChild.parent = this;

            if (children != null) {
                for (ScopeBlock child : children) {
                    if ((newChild.startLocation > child.startLocation) && (newChild.startLocation < child.finishLocation)) {
                        if (newChild.finishLocation > child.finishLocation) {
                            newChild.finishLocation = child.finishLocation;
                        }
                        child.addChild(newChild);
                        return;
                    }
                }
                int pos = 0;
                for (ScopeBlock child : children) {
                    if (newChild.startLocation < child.startLocation) {
                        children.add(pos, newChild);
                        return;
                    }
                    pos++;
                }
                children.add(newChild);
                return;
            }
            children = new ArrayList<>();
            children.add(newChild);
        }

        /**
         * removes a child from this node
         *
         * @param child
         *            the child to remove
         */
        public void removeChild(ScopeBlock child) {
            if (children != null) {
                children.remove(child);
            }
        }

        public void markFieldAssociatedWrites(int sourceReg) {
            if (assocs != null) {
                for (Map.Entry<UserObject, Integer> entry : assocs.entrySet()) {
                    UserObject uo = entry.getKey();
                    if ((uo.registerSource == sourceReg) || ((uo.caller instanceof Integer) && (((Integer) uo.caller).intValue() == sourceReg))) {
                        Integer preWrittenFromField = entry.getValue();
                        if ((preWrittenFromField != null) && (stores != null)) {
                            stores.remove(preWrittenFromField);
                        }
                    }
                }
            }
        }

        /**
         * report stores that occur at scopes higher than associated loads that are not involved with loops
         *
         * @param parentUsedRegs
         *            the set of registers that where used by the parent scope block
         */
        public void findBugs(Set<Integer> parentUsedRegs) {
            if (isLoop) {
                return;
            }

            Set<Integer> usedRegs = new HashSet<>(parentUsedRegs);
            if (stores != null) {
                usedRegs.addAll(stores.keySet());
            }
            if (loads != null) {
                usedRegs.addAll(loads.keySet());
            }

            if (stores != null) {
                if (loads != null) {
                    stores.keySet().removeAll(loads.keySet());
                }
                stores.keySet().removeAll(parentUsedRegs);
                for (int r = ignoreRegs.nextSetBit(0); r >= 0; r = ignoreRegs.nextSetBit(r + 1)) {
                    stores.remove(Integer.valueOf(r));
                }

                if ((children != null) && !stores.isEmpty()) {
                    for (Map.Entry<Integer, Integer> entry : stores.entrySet()) {
                        int childUseCount = 0;
                        boolean inIgnoreSB = false;
                        Integer reg = entry.getKey();
                        for (ScopeBlock child : children) {
                            if (child.usesReg(reg)) {
                                if (child.isLoop || child.isSync() || child.isTry()) {
                                    inIgnoreSB = true;
                                    break;
                                }
                                childUseCount++;
                            }
                        }
                        if (!inIgnoreSB && (childUseCount == 1)) {
                            if (appearsToBeUserRegister(reg)) {
                                bugReporter.reportBug(new BugInstance(BloatedAssignmentScope.this, BugType.BAS_BLOATED_ASSIGNMENT_SCOPE.name(), NORMAL_PRIORITY)
                                        .addClass(BloatedAssignmentScope.this).addMethod(BloatedAssignmentScope.this)
                                        .addSourceLine(BloatedAssignmentScope.this, entry.getValue().intValue()));
                            }
                        }
                    }
                }
            }

            if (children != null) {
                for (ScopeBlock child : children) {
                    child.findBugs(usedRegs);
                }
            }
        }

        /**
         * in some cases the java compiler synthesizes variable for its own purposes. Hopefully when it does this these, can not be found in the localvariable
         * table. If we find this to be the case, don't report them
         *
         * @param reg
         *            the register to check
         *
         * @return if reg variable appears in the local variable table
         */
        private boolean appearsToBeUserRegister(int reg) {
            LocalVariableTable lvt = getMethod().getLocalVariableTable();
            if (lvt == null) {
                return false;
            }

            LocalVariable lv = lvt.getLocalVariable(reg);
            return lv != null;
        }

        /**
         * returns whether this block either loads or stores into the register in question
         *
         * @param reg
         *            the register to look for loads or stores
         *
         * @return whether the block uses the register
         */
        public boolean usesReg(Integer reg) {
            if ((loads != null) && loads.containsKey(reg)) {
                return true;
            }
            if ((stores != null) && stores.containsKey(reg)) {
                return true;
            }

            if (children != null) {
                for (ScopeBlock child : children) {
                    if (child.usesReg(reg)) {
                        return true;
                    }
                }
            }

            return false;
        }

        /**
         * push all loads and stores to this block up to the parent
         */
        public void pushUpLoadStores() {
            if (parent != null) {
                if (loads != null) {
                    if (parent.loads != null) {
                        parent.loads.putAll(loads);
                    } else {
                        parent.loads = loads;
                    }
                }
                if (stores != null) {
                    if (parent.stores != null) {
                        parent.stores.putAll(stores);
                    } else {
                        parent.stores = stores;
                    }
                }
                loads = null;
                stores = null;
            }
        }
    }

    public boolean isRiskyMethodCall() {

        String clsName = getClassConstantOperand();

        if (dangerousAssignmentClassSources.contains(clsName)) {
            return true;
        }

        FQMethod key = new FQMethod(clsName, getNameConstantOperand(), getSigConstantOperand());
        if (dangerousAssignmentMethodSources.contains(key)) {
            return true;
        }

        String sig = key.toFQMethodSignature();
        for (Pattern p : dangerousAssignmentMethodPatterns) {
            Matcher m = p.matcher(sig);
            if (m.matches()) {
                return true;
            }
        }

        return false;
    }

    public boolean isRiskyStoreClass(int reg) {
        LocalVariableTable lvt = getMethod().getLocalVariableTable();
        if (lvt != null) {
            LocalVariable lv = lvt.getLocalVariable(reg, getNextPC());
            if ((lv != null) && dangerousStoreClassSigs.contains(lv.getSignature())) {
                return true;
            }
        }

        return false;
    }

    /**
     * represents the source of an assignment to a variable, which could be a method call or a field
     */
    static class UserObject {
        Comparable<?> caller;
        boolean isRisky;
        int registerSource = -1;

        public UserObject(Comparable<?> callerObj, boolean risky) {
            caller = callerObj;
            isRisky = risky;
        }

        public UserObject(boolean risky) {
            isRisky = risky;
        }

        public UserObject(int reg) {
            registerSource = reg;
        }

        @Override
        public int hashCode() {
            return ((caller == null) ? 0 : caller.hashCode()) | (isRisky ? 1 : 0) | registerSource;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof UserObject)) {
                return false;
            }

            UserObject that = (UserObject) o;

            if (caller == null) {
                if (that.caller != null) {
                    return false;
                }
            } else {
                boolean eq = caller.equals(that.caller);
                if (!eq) {
                    return false;
                }
            }

            return (isRisky == that.isRisky) && (registerSource == that.registerSource);
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }
}
