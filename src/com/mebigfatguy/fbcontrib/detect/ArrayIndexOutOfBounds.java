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
import java.util.Iterator;
import java.util.Map;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantInteger;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for usage of arrays with statically known indices where it can be determined
 * that the index is out of bounds based on how the array was allocated. This 
 * delector is obviously limited to a small subset of out of bounds exceptions that
 * can be statically determined, and not the large family of problems that can 
 * occur at runtime.
 */
@CustomUserValue
public class ArrayIndexOutOfBounds extends BytecodeScanningDetector {

    private BugReporter bugReporter;
    private OpcodeStack stack;
    private BitSet initializedRegs;
    private BitSet modifyRegs;
    private Map<Integer, Integer> nullStoreToLocation;
    
    /**
     * constructs an AIOB detector given the reporter to report bugs on

     * @param bugReporter the sync of bug reports
     */ 
    public ArrayIndexOutOfBounds(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }
    
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            initializedRegs = new BitSet();
            modifyRegs = new BitSet();
            nullStoreToLocation = new HashMap<Integer, Integer>();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            initializedRegs = null;
            modifyRegs = null;
            nullStoreToLocation = null;
        }
    }
    
    @Override
    public void visitCode(Code obj) {
        Method m = getMethod();
        stack.resetForMethodEntry(this);
        initializedRegs.clear();
        modifyRegs.clear();
        Type[] argTypes = m.getArgumentTypes();
        int arg = ((m.getAccessFlags() & Constants.ACC_STATIC) != 0) ? 0 : 1;
        for (Type argType : argTypes) {
            String argSig = argType.getSignature();
            initializedRegs.set(arg);
            arg += ("J".equals(argSig) || "D".equals(argSig)) ? 2 : 1;
        }
        nullStoreToLocation.clear();
        super.visitCode(obj);
        
        for (Integer pc : nullStoreToLocation.values()) {
            bugReporter.reportBug(new BugInstance(this, BugType.AIOB_ARRAY_STORE_TO_NULL_REFERENCE.name(), HIGH_PRIORITY)
            .addClass(this)
            .addMethod(this)
            .addSourceLine(this, pc.intValue()));
        }
    }
    
    @Override
    public void sawOpcode(int seen) {
        Integer size = null;
        boolean sizeSet = false;
        try {
            stack.precomputation(this);
            
            switch (seen) {
            case ICONST_0:
            case ICONST_1:
            case ICONST_2:
            case ICONST_3:
            case ICONST_4:
            case ICONST_5:
                size = Integer.valueOf(seen - ICONST_0);
                sizeSet = true;
            break;
            
            case ILOAD:
            case ILOAD_0:
            case ILOAD_1:
            case ILOAD_2:
            case ILOAD_3: {
                int reg = RegisterUtils.getLoadReg(this,  seen);
                if (modifyRegs.get(reg)) {
                    modifyRegs.clear(reg);
                    sizeSet = true;
                }
            }
            break;
                
            
            case BIPUSH:
            case SIPUSH:
                size = Integer.valueOf(getIntConstant());
                sizeSet = true;
            break;
            
            case IINC:
                modifyRegs.set(getRegisterOperand());
            break;
            
            case IADD:
            case ISUB:
            case IMUL:
            case IDIV:
            case F2I:
            case D2I:
            case L2I:
                sizeSet = true;
            break;
            
            case ISTORE:
            case ISTORE_0:
            case ISTORE_1:
            case ISTORE_2:
            case ISTORE_3:
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    if (item.getUserValue() == null) {
                        modifyRegs.set(getRegisterOperand());
                    }
                }
            break;
                
            case LDC:
                Constant c = getConstantRefOperand();
                if (c instanceof ConstantInteger) {
                    size = Integer.valueOf(((ConstantInteger) c).getBytes());
                    sizeSet = true;
                }
            break;
            
            case NEWARRAY:
            case ANEWARRAY:
                if (stack.getStackDepth() >= 1) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    size = (Integer) item.getUserValue();
                    sizeSet = true;
                }
                break;
            
            case IASTORE:
            case LASTORE:
            case FASTORE:
            case DASTORE:
            case AASTORE:
            case BASTORE:
            case CASTORE:
            case SASTORE:
                if (stack.getStackDepth() >= 3) {
                    OpcodeStack.Item indexItem = stack.getStackItem(1);
                    Number index = (Number) indexItem.getConstant();
                    if (index != null) {
                        OpcodeStack.Item arrayItem = stack.getStackItem(2);
                        Integer sz = (Integer) arrayItem.getUserValue();
                        if (sz != null) {
                            if (index.intValue() >= sz.intValue()) {
                                bugReporter.reportBug(new BugInstance(this, BugType.AIOB_ARRAY_INDEX_OUT_OF_BOUNDS.name(), HIGH_PRIORITY)
                                            .addClass(this)
                                            .addMethod(this)
                                            .addSourceLine(this));
                            }
                        }
                        
                        int reg = arrayItem.getRegisterNumber();
                        if ((reg >= 0) && !initializedRegs.get(reg)) {
                            nullStoreToLocation.put(Integer.valueOf(reg), Integer.valueOf(getPC()));
                        }
                    }
                }
                break;
                
            case IALOAD:
            case LALOAD:
            case FALOAD:
            case DALOAD:
            case AALOAD:
            case BALOAD:
            case CALOAD:
            case SALOAD:
                if (stack.getStackDepth() >= 2) {
                    OpcodeStack.Item indexItem = stack.getStackItem(0);
                    Integer index = (Integer) indexItem.getConstant();
                    if (index != null) {
                        OpcodeStack.Item arrayItem = stack.getStackItem(1);
                        Integer sz = (Integer) arrayItem.getUserValue();
                        if (sz != null) {
                            if (index.intValue() >= sz.intValue()) {
                                bugReporter.reportBug(new BugInstance(this, BugType.AIOB_ARRAY_INDEX_OUT_OF_BOUNDS.name(), HIGH_PRIORITY)
                                            .addClass(this)
                                            .addMethod(this)
                                            .addSourceLine(this));
                            }
                        }
                    }
                }
                break;
                
            case ASTORE_0:
            case ASTORE_1:
            case ASTORE_2:
            case ASTORE_3:
            case ASTORE:
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item value = stack.getStackItem(0);
                    if (!value.isNull())
                        initializedRegs.set(getRegisterOperand());
                } else {
                    initializedRegs.set(getRegisterOperand());
                } 
                break;
            
            case IFEQ:
            case IFNE:
            case IFLT:
            case IFGE:
            case IFGT:
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
                int branchTarget = getBranchTarget();
                Iterator<Map.Entry<Integer, Integer>> it = nullStoreToLocation.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<Integer, Integer> entry = it.next();
                    int pc = entry.getValue().intValue();
                    if ((branchTarget < pc) && (initializedRegs.get(entry.getKey().intValue())))
                        it.remove();
                }
            }
  
        } finally {
            stack.sawOpcode(this, seen);
            if (sizeSet) {
                if (stack.getStackDepth() >= 1) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    item.setUserValue(size);
                }
            }
        }
    }
}
