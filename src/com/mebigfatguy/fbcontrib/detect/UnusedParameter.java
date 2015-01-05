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

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.LocalVariable;
import org.apache.bcel.classfile.LocalVariableTable;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for private or static methods that have parameters that aren't used. These parameters
 * can be removed.
 */
@CustomUserValue
public class UnusedParameter extends BytecodeScanningDetector {

    private static Set<String> IGNORE_METHODS = new HashSet<String>();
    static {
        IGNORE_METHODS.add(Values.CONSTRUCTOR);
        IGNORE_METHODS.add(Values.STATIC_INITIALIZER);
        IGNORE_METHODS.add("main");
        IGNORE_METHODS.add("premain");
        IGNORE_METHODS.add("agentmain");
        IGNORE_METHODS.add("writeObject");
        IGNORE_METHODS.add("readObject");
        IGNORE_METHODS.add("readObjectNoData");
        IGNORE_METHODS.add("writeReplace");
        IGNORE_METHODS.add("readResolve");
    }
    private BugReporter bugReporter;
    
    private BitSet unusedParms;
    private Map<Integer, Integer> regToParm;
    private OpcodeStack stack;
    /**
     * constructs a UP detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
     */
    public UnusedParameter(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }
    
    /**
     * implements the visitor to create parm bitset
     * 
     * @param classContext the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            unusedParms = new BitSet();
            regToParm = new HashMap<Integer, Integer>();
            stack = new OpcodeStack();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            regToParm = null;
            unusedParms.clear();
        }
    }
    
    /**
     * implements the visitor to clear the parm set, and check for potential methods
     * 
     * @param obj the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        unusedParms.clear();
        regToParm.clear();
        stack.resetForMethodEntry(this);
        Method m = getMethod();
        String methodName = m.getName();
        if (IGNORE_METHODS.contains(methodName))
            return;
        
        int accessFlags = m.getAccessFlags();
        if (((accessFlags & (Constants.ACC_STATIC|Constants.ACC_PRIVATE)) != 0) && ((accessFlags & Constants.ACC_SYNTHETIC) == 0)) {
            Type[] parmTypes = Type.getArgumentTypes(m.getSignature());
        
            if (parmTypes.length > 0) {
            
                int firstReg = 0;
                if ((accessFlags & Constants.ACC_STATIC) == 0)
                    ++firstReg;
                
                int reg = firstReg;
                for (int i = 0; i < parmTypes.length; ++i) {
                    unusedParms.set(reg);
                    regToParm.put(reg, Integer.valueOf(i+1));
                    String parmSig = parmTypes[i].getSignature();
                    reg += ("J".equals(parmSig) || "D".equals(parmSig)) ? 2 : 1;
                }
                
                super.visitCode(obj);
                
                if (!unusedParms.isEmpty()) {
                    LocalVariableTable lvt = m.getLocalVariableTable();

                    reg = unusedParms.nextSetBit(firstReg);
                    while (reg >= 0) {
                        LocalVariable lv = (lvt != null) ? lvt.getLocalVariable(reg, 0) : null;
                        if (lv != null) {
                            String parmName = lv.getName();
                            bugReporter.reportBug(new BugInstance(this, BugType.UP_UNUSED_PARAMETER.name(), NORMAL_PRIORITY)
                                            .addClass(this)
                                            .addMethod(this)
                                            .addString("Parameter " + regToParm.get(reg) + ": " + parmName));
                        }
                        reg = unusedParms.nextSetBit(reg+1);
                    }
                }
            }
        }
    }
    
    /**
     * implements the visitor to look for usage of parmeter registers.
     * 
     * @param seen the opcode of the currently parsed instruction
     */
    @Override
    public void sawOpcode(int seen) {
        if (unusedParms.isEmpty()) {
            return;
        }
        
        try {
            stack.precomputation(this);
        
            switch (seen) {
                case ASTORE:
                case ASTORE_0:
                case ASTORE_1:
                case ASTORE_2:
                case ASTORE_3:
                case ISTORE:
                case ISTORE_0:
                case ISTORE_1:
                case ISTORE_2:
                case ISTORE_3:
                case LSTORE:
                case LSTORE_1:
                case LSTORE_2: 
                case LSTORE_3: 
                case FSTORE:
                case FSTORE_0:
                case FSTORE_1:
                case FSTORE_2:
                case FSTORE_3:
                case DSTORE:
                case DSTORE_1:
                case DSTORE_2:
                case DSTORE_3:
                case ALOAD:
                case ALOAD_0:
                case ALOAD_1:
                case ALOAD_2:
                case ALOAD_3: 
                case ILOAD:
                case ILOAD_0:
                case ILOAD_1:
                case ILOAD_2:
                case ILOAD_3:
                case LLOAD:
                case LLOAD_0:
                case LLOAD_1:
                case LLOAD_2:
                case LLOAD_3:
                case FLOAD:
                case FLOAD_0:
                case FLOAD_1:
                case FLOAD_2:
                case FLOAD_3:
                case DLOAD:
                case DLOAD_0:
                case DLOAD_1:
                case DLOAD_2:
                case DLOAD_3: {
                    int reg = getRegisterOperand();
                    unusedParms.clear(reg);
                }
                break;
                
                case ARETURN:
                case IRETURN:
                case LRETURN:
                case FRETURN:
                case DRETURN:{
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item item = stack.getStackItem(0);
                        int reg = item.getRegisterNumber();
                        if (reg >= 0)
                            unusedParms.clear(reg);
                    }
                }
                break;
                default:
                	break;
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }
}
