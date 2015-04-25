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

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.CodeException;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;
import com.mebigfatguy.fbcontrib.utils.ToString;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XField;

/**
 * looks for variable assignments at a scope larger than its use. In this case,
 * the assignment can be pushed down into the smaller scope to reduce the
 * performance impact of that assignment.
 */
@CustomUserValue
public class BloatedAssignmentScope extends BytecodeScanningDetector {
    private static final Set<String> dangerousAssignmentClassSources = new HashSet<String>(7);
    private static final Set<String> dangerousAssignmentMethodSources = new HashSet<String>(4);
    private static final Set<Pattern> dangerousAssignmentMethodPatterns = new HashSet<Pattern>(1);

    static {
        dangerousAssignmentClassSources.add("java/io/BufferedInputStream");
        dangerousAssignmentClassSources.add("java/io/DataInputStream");
        dangerousAssignmentClassSources.add("java/io/InputStream");
        dangerousAssignmentClassSources.add("java/io/ObjectInputStream");
        dangerousAssignmentClassSources.add("java/io/BufferedReader");
        dangerousAssignmentClassSources.add("java/io/FileReader");
        dangerousAssignmentClassSources.add("java/io/Reader");
        dangerousAssignmentClassSources.add("javax/nio/channels/Channel");
        dangerousAssignmentClassSources.add("io/netty/channel/Channel");
        
        dangerousAssignmentMethodSources.add("java/lang/System.currentTimeMillis()J");
        dangerousAssignmentMethodSources.add("java/lang/System.nanoTime()J");
        dangerousAssignmentMethodSources.add("java/util/Calendar.get(I)I");
        dangerousAssignmentMethodSources.add("java/util/GregorianCalendar.get(I)I");
        dangerousAssignmentMethodSources.add("java/util/Iterator.next()Ljava/lang/Object;");
        dangerousAssignmentMethodSources.add("java/util/regex/Matcher.start()I");
        
        dangerousAssignmentMethodPatterns.add(Pattern.compile(".*serial.*", Pattern.CASE_INSENSITIVE));
    }

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
     * implements the visitor to create and the clear the register to location
     * map
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
            monitorSyncPCs = new ArrayList<Integer>(5);
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
     * implements the visitor to look for variables assigned below the scope in
     * which they are used.
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
                int reg = RegisterUtils.getStoreReg(this, seen);

                if (catchHandlers.get(pc)) {
                    ignoreRegs.set(reg);
                } else if (monitorSyncPCs.size() > 0) {
                    ignoreRegs.set(reg);
                } else if (sawNull) {
                    ignoreRegs.set(reg);
                }

                if (!ignoreRegs.get(reg)) {
                    ScopeBlock sb = findScopeBlock(rootScopeBlock, pc);
                    if (sb != null) {
                        UserObject assoc = null;
                        if (stack.getStackDepth() > 0) {
                            assoc = (UserObject) stack.getStackItem(0).getUserValue();
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
            } else if (seen == IINC) {
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
                } else if (monitorSyncPCs.size() > 0) {
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
            } else if (OpcodeUtils.isLoad(seen)) {
                int reg = RegisterUtils.getLoadReg(this, seen);
                if (!ignoreRegs.get(reg)) {
                    ScopeBlock sb = findScopeBlock(rootScopeBlock, pc);
                    if (sb != null) {
                        sb.addLoad(reg, pc);
                    } else {
                        ignoreRegs.set(reg);
                    }
                }
            } else if (((seen >= IFEQ) && (seen <= GOTO)) || (seen == IFNULL) || (seen == IFNONNULL) || (seen == GOTO_W)) {
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
                        if ((sb != null) && (!sb.isLoop()) && !sb.isCase() && !sb.hasChildren()) {
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
                                sb.setFinish(target);
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
                        sb.setLoop();
                    }
                }
            } else if ((seen == TABLESWITCH) || (seen == LOOKUPSWITCH)) {
                int[] offsets = getSwitchOffsets();
                List<Integer> targets = new ArrayList<Integer>(offsets.length);
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
            } else if ((seen == INVOKEVIRTUAL) || (seen == INVOKEINTERFACE)) {
                if ("wasNull".equals(getNameConstantOperand()) && "()Z".equals(getSigConstantOperand())) {
                    dontReport = true;
                }

                uo = new UserObject();
                uo.isRisky = isRiskyMethodCall();
                uo.caller = getCallingObject();

                if (uo.caller != null) {
                    ScopeBlock sb = findScopeBlock(rootScopeBlock, pc);
                    if (sb != null) {
                        sb.removeByAssoc(uo.caller);
                    }
                }
            } else if ((seen == INVOKESTATIC) || (seen == INVOKESPECIAL)) {
                uo = new UserObject();
                uo.isRisky = isRiskyMethodCall();
            } else if (seen == MONITORENTER) {
                monitorSyncPCs.add(Integer.valueOf(pc));

                ScopeBlock sb = new ScopeBlock(pc, Integer.MAX_VALUE);
                sb.setSync();
                rootScopeBlock.addChild(sb);
            } else if (seen == MONITOREXIT) {
                if (monitorSyncPCs.size() > 0) {
                    ScopeBlock sb = findSynchronizedScopeBlock(rootScopeBlock, monitorSyncPCs.get(0));
                    if (sb != null)
                        sb.setFinish(pc);
                    monitorSyncPCs.remove(monitorSyncPCs.size() - 1);
                }
            }

            sawDup = (seen == DUP);
            sawNull = (seen == ACONST_NULL);
        } finally {
            TernaryPatcher.pre(stack, seen);
            stack.sawOpcode(this, seen);
            TernaryPatcher.post(stack, seen);
            if (uo != null) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    item.setUserValue(uo);
                }
            }
        }
    }

    /**
     * returns either a register number of a field reference of the object that
     * a method is being called on, or null, if it can't be determined.
     * 
     * @return either an Integer for a register, or a String for the field name,
     *         or null
     */
    private Comparable<?> getCallingObject() {
        String sig = getSigConstantOperand();
        if ("V".equals(Type.getReturnType(sig).getSignature())) {
            return null;
        }

        Type[] types = Type.getArgumentTypes(sig);
        if (stack.getStackDepth() <= types.length) {
            return null;
        }

        OpcodeStack.Item caller = stack.getStackItem(types.length);
        int reg = caller.getRegisterNumber();
        if (reg >= 0) {
            return Integer.valueOf(reg);
        }

        /*
         * We ignore the possibility of two fields with the same name in
         * different classes
         */
        XField f = caller.getXField();
        if (f != null) {
            return f.getName();
        }
        return null;
    }

    /**
     * returns the scope block in which this register was assigned, by
     * traversing the scope block tree
     * 
     * @param sb
     *            the scope block to start searching in
     * @param pc
     *            the current program counter
     * @return the scope block or null if not found
     */
    private ScopeBlock findScopeBlock(ScopeBlock sb, int pc) {

        if ((pc > sb.getStart()) && (pc < sb.getFinish())) {
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
        return null;
    }

    /**
     * returns an existing scope block that has the same target as the one
     * looked for
     * 
     * @param sb
     *            the scope block to start with
     * @param target
     *            the target to look for
     * 
     * @return the scope block found or null
     */
    private ScopeBlock findScopeBlockWithTarget(ScopeBlock sb, int start, int target) {
        ScopeBlock parentBlock = null;
        if ((sb.startLocation < start) && (sb.finishLocation >= start)) {
            if ((sb.finishLocation <= target) || (sb.isGoto() && !sb.isLoop())) {
                parentBlock = sb;
            }
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
     * finds the scope block that is the active synchronized block
     * 
     * @return the scope block
     */
    private ScopeBlock findSynchronizedScopeBlock(ScopeBlock sb, int monitorEnterPC) {

        ScopeBlock monitorBlock = sb;

        if (sb.hasChildren()) {
            for (ScopeBlock child : sb.getChildren()) {
                if (child.isSync()) {
                    if (child.getStart() > monitorBlock.getStart()) {
                        monitorBlock = child;
                        monitorBlock = findSynchronizedScopeBlock(monitorBlock, monitorEnterPC);
                    }
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
     * @return the pc of the handler for this pc if it's the start of a try
     *         block, or -1
     * 
     */
    private int findCatchHandlerFor(int pc) {
        CodeException[] exceptions = getMethod().getCode().getExceptionTable();
        if (exceptions != null) {
            for (CodeException ex : exceptions) {
                if (ex.getStartPC() == pc)
                    return ex.getHandlerPC();
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
         * construts a new scope block
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
         * @returns a string representation
         */
        @Override
        public String toString() {
        	return ToString.build(this);
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
         * @returns whether this block is a loop
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
         * @returns whether this block was caused by a goto
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
         * @returns whether this block was caused by a synchronized block
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
         * @returns whether this block was caused by a try block
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
         * @returns whether this block was caused by a case block
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
         */
        public void addStore(int reg, int pc, UserObject assocObject) {
            if (stores == null) {
                stores = new HashMap<Integer, Integer>(6);
            }

            stores.put(Integer.valueOf(reg), Integer.valueOf(pc));
            if (assocs == null) {
                assocs = new HashMap<UserObject, Integer>(6);
            }
            assocs.put(assocObject, Integer.valueOf(reg));
        }

        /**
         * removes stores to registers that where retrieved from method calls on
         * assocObject
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
                loads = new HashMap<Integer, Integer>(10);
            }

            loads.put(Integer.valueOf(reg), Integer.valueOf(pc));
        }

        /**
         * adds a scope block to this subtree by finding the correct place in
         * the hierarchy to store it
         * 
         * @param newChild
         *            the scope block to add to the tree
         */
        public void addChild(ScopeBlock newChild) {
            newChild.parent = this;

            if (children != null) {
                for (ScopeBlock child : children) {
                    if ((newChild.startLocation > child.startLocation) && (newChild.startLocation < child.finishLocation)) {
                        if (newChild.finishLocation > child.finishLocation)
                            newChild.finishLocation = child.finishLocation;
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
            children = new ArrayList<ScopeBlock>();
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

        /**
         * report stores that occur at scopes higher than associated loads that
         * are not involved with loops
         */
        public void findBugs(Set<Integer> parentUsedRegs) {
            if (isLoop) {
                return;
            }

            Set<Integer> usedRegs = new HashSet<Integer>(parentUsedRegs);
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

                if ((stores.size() > 0) && (children != null)) {
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
                        if ((!inIgnoreSB) && (childUseCount == 1)) {
                            bugReporter.reportBug(new BugInstance(BloatedAssignmentScope.this, BugType.BAS_BLOATED_ASSIGNMENT_SCOPE.name(), NORMAL_PRIORITY)
                                    .addClass(BloatedAssignmentScope.this).addMethod(BloatedAssignmentScope.this)
                                    .addSourceLine(BloatedAssignmentScope.this, entry.getValue().intValue()));
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
         * returns whether this block either loads or stores into the register
         * in question
         * 
         * @param reg
         *            the register to look for loads or stores
         * 
         * @return whether the block uses the register
         */
        public boolean usesReg(Integer reg) {
            if ((loads != null) && (loads.containsKey(reg))) {
                return true;
            }
            if ((stores != null) && (stores.containsKey(reg))) {
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

        String key = clsName + "." + getNameConstantOperand() + getSigConstantOperand();
        if (dangerousAssignmentMethodSources.contains(key)) {
            return true;
        }
        
        for (Pattern p : dangerousAssignmentMethodPatterns) {
            Matcher m = p.matcher(key);
            if (m.matches()) {
                return true;
            }
        }
        
        
        return false;
    }

    static class UserObject {
        Comparable<?> caller;
        boolean isRisky;

        @Override
        public String toString() {
        	return ToString.build(this);
        }
    }
}
