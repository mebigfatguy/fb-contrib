/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2014 Dave Brosius
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
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.SourceLineAnnotation;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for allocations of synchronized collections that are stored in local variables, and 
 * never stored in fields or returned from methods. As local variables are by definition
 * thread safe, using synchronized collections in this context makes no sense.
 */
@CustomUserValue
public class LocalSynchronizedCollection extends BytecodeScanningDetector
{
    private static Map<String, Integer> syncCtors = new HashMap<String, Integer>();
    static {
        syncCtors.put("java/util/Vector", Integer.valueOf(Constants.MAJOR_1_1));
        syncCtors.put("java/util/Hashtable", Integer.valueOf(Constants.MAJOR_1_1));
        syncCtors.put("java/lang/StringBuffer", Integer.valueOf(Constants.MAJOR_1_5));
    }
    private static Set<String> syncMethods = new HashSet<String>();
    static {
        syncMethods.add("synchronizedCollection");
        syncMethods.add("synchronizedList");
        syncMethods.add("synchronizedMap");
        syncMethods.add("synchronizedSet");
        syncMethods.add("synchronizedSortedMap");
        syncMethods.add("synchronizedSortedSet");
    }
    
    private BugReporter bugReporter;
    private OpcodeStack stack;
    private Map<Integer, CollectionRegInfo> syncRegs;
    private int classVersion;
    
    /**
     * constructs a LSYC detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
     */
    public LocalSynchronizedCollection(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }
    
    /**
     * implements the visitor to create and clear the stack and syncRegs
     * 
     * @param classContext the context object of the currently parsed class 
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            syncRegs = new HashMap<Integer, CollectionRegInfo>();
            classVersion = classContext.getJavaClass().getMajor();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            syncRegs = null;
        }
    }
    /**
     * implements the visitor to collect parameter registers
     * 
     * @param obj the context object of the currently parsed method
     */
    @Override
    public void visitMethod(Method obj) {
        syncRegs.clear();
        int[] parmRegs = RegisterUtils.getParameterRegisters(obj);
        for (int pr : parmRegs) {
        	syncRegs.put(Integer.valueOf(pr), 
        				 new CollectionRegInfo(RegisterUtils.getLocalVariableEndRange(obj.getLocalVariableTable(), pr, 0)));
        }
    }
    
    /**
     * implements the visitor to reset the stack
     * 
     * @param obj the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        super.visitCode(obj);
        
        for (Map.Entry<Integer, CollectionRegInfo> entry : syncRegs.entrySet()) {
            CollectionRegInfo cri = entry.getValue();
            if (!cri.getIgnore()) {
                bugReporter.reportBug(new BugInstance(this, "LSYC_LOCAL_SYNCHRONIZED_COLLECTION", cri.getPriority())
                            .addClass(this)
                            .addMethod(this)
                            .addSourceLine(cri.getSourceLineAnnotation()));
            }
                
        }
    }
    
    /**
     * implements the visitor to find stores to locals of synchronized collections
     * 
     * @param seen the opcode of the currently parsed instruction
     */
    @Override
    public void sawOpcode(int seen) {
        Integer tosIsSyncColReg = null;
        try {
            stack.precomputation(this);
            
            if (seen == INVOKESPECIAL) {
                if ("<init>".equals(getNameConstantOperand())) {
                	Integer minVersion = syncCtors.get(getClassConstantOperand());
                	if ((minVersion != null) && (classVersion >= minVersion.intValue())) {
                        tosIsSyncColReg = Integer.valueOf(-1);
                    }
                }
            } else if (seen == INVOKESTATIC) {
                if ("java/util/Collections".equals(getClassConstantOperand())) {
                    if (syncMethods.contains(getNameConstantOperand())) {
                        tosIsSyncColReg = Integer.valueOf(-1);
                    }
                }
            } else if ((seen == ASTORE) || ((seen >= ASTORE_0) && (seen <= ASTORE_3))) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    int reg = RegisterUtils.getAStoreReg(this, seen);
                    if (item.getUserValue() != null) {
                        if (!syncRegs.containsKey(Integer.valueOf(reg))) {
                            CollectionRegInfo cri = new CollectionRegInfo(SourceLineAnnotation.fromVisitedInstruction(this), RegisterUtils.getLocalVariableEndRange(getMethod().getLocalVariableTable(), reg, getNextPC()));
                            syncRegs.put(Integer.valueOf(reg), cri);
                    
                        }
                    } else {
                        CollectionRegInfo cri = syncRegs.get(Integer.valueOf(reg));
                        if (cri == null) {
                            cri = new CollectionRegInfo(RegisterUtils.getLocalVariableEndRange(getMethod().getLocalVariableTable(), reg, getNextPC()));
                            syncRegs.put(Integer.valueOf(reg), cri);
                        }
                        cri.setIgnore();
                    }
                }
            } else if ((seen == ALOAD) || ((seen >= ALOAD_0) && (seen <= ALOAD_3))) {
                int reg = RegisterUtils.getALoadReg(this, seen);
                CollectionRegInfo cri = syncRegs.get(Integer.valueOf(reg));
                if ((cri != null) && !cri.getIgnore())
                    tosIsSyncColReg = Integer.valueOf(reg);
            } else if ((seen == PUTFIELD) || (seen == ARETURN)) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    syncRegs.remove(item.getUserValue());
                }               
            }       
            
            if (syncRegs.size() > 0) {
                if ((seen == INVOKESPECIAL)
                ||  (seen == INVOKEINTERFACE)
                ||  (seen == INVOKEVIRTUAL)
                ||  (seen == INVOKESTATIC)) {
                    String sig = getSigConstantOperand();
                    int argCount = Type.getArgumentTypes(sig).length;
                    if (stack.getStackDepth() >= argCount) {
                        for (int i = 0; i < argCount; i++) {
                            OpcodeStack.Item item = stack.getStackItem(i);
                            CollectionRegInfo cri = syncRegs.get(item.getUserValue());
                            if (cri != null)
                                cri.setPriority(LOW_PRIORITY);
                        }
                    }
                } else if (seen == MONITORENTER) {
                    //Assume if synchronized blocks are used then something tricky is going on.
                    //There is really no valid reason for this, other than folks who use
                    //synchronized blocks tend to know what's going on.
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item item = stack.getStackItem(0);
                        syncRegs.remove(item.getUserValue());
                    }
                } else if (seen == AASTORE) {
                	if (stack.getStackDepth() > 0) {
                		OpcodeStack.Item item = stack.getStackItem(0);
                		syncRegs.remove(item.getUserValue());
                	}
                }
            }
            
            int curPC = getPC();
            Iterator<CollectionRegInfo> it = syncRegs.values().iterator();
            while (it.hasNext()) {
                CollectionRegInfo cri = it.next();
                if (cri.getEndPCRange() < curPC) {
                    if (!cri.getIgnore()) {
                        bugReporter.reportBug(new BugInstance(this, "LSYC_LOCAL_SYNCHRONIZED_COLLECTION", cri.getPriority())
                                .addClass(this)
                                .addMethod(this)
                                .addSourceLine(cri.getSourceLineAnnotation()));                 
                    }
                    it.remove();
                }
            }
        } finally {
			TernaryPatcher.pre(stack, seen);
			stack.sawOpcode(this, seen);
			TernaryPatcher.post(stack, seen);
            if (tosIsSyncColReg != null) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    item.setUserValue(tosIsSyncColReg);
                }
            }
        }
    }
        
    static class CollectionRegInfo
    {
        private SourceLineAnnotation slAnnotation;
        private int priority = HIGH_PRIORITY;
        private int endPCRange = Integer.MAX_VALUE;
        
        public CollectionRegInfo(SourceLineAnnotation sla, int endPC) {
            slAnnotation = sla;
            endPCRange = endPC;
        }
        
        public CollectionRegInfo(int endPC) {
            slAnnotation = null;
            endPCRange = endPC;
        }
        
        public SourceLineAnnotation getSourceLineAnnotation() {
            return slAnnotation;
        }
        
        public void setEndPCRange(int pc) {
            endPCRange = pc;
        }
        
        public int getEndPCRange() {
            return endPCRange;
        }
        
        public void setIgnore() {
            slAnnotation = null;
        }
        
        public boolean getIgnore() {
            return slAnnotation == null;
        }
        
        public void setPriority(int newPriority) {
            if (newPriority > priority)
                priority = newPriority;
        }
        
        public int getPriority() {
            return priority;
        }
    }
}
