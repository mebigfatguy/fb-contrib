/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2013 Dave Brosius
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

import java.util.HashSet;
import java.util.Set;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantInteger;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

public class ArrayIndexOutOfBounds extends BytecodeScanningDetector {

    private BugReporter bugReporter;
    private OpcodeStack stack;
    private Set<Integer> initializedRegs;
    
    /**
     * constructs an AIOB detector given the reporter to report bugs on

     * @param bugReporter the sync of bug reports
     */ 
    public ArrayIndexOutOfBounds(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }
    
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            initializedRegs = new HashSet<Integer>(10);
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            initializedRegs = null;
        }
    }
    
    public void visitCode(Code obj) {
        Method m = getMethod();
        stack.resetForMethodEntry(this);
        initializedRegs.clear();
        Type[] argTypes = m.getArgumentTypes();
        int arg = ((m.getAccessFlags() & Constants.ACC_STATIC) != 0) ? 0 : 1;
        for (Type argType : argTypes) {
            String argSig = argType.getSignature();
            initializedRegs.add(Integer.valueOf(arg));
            arg += ("J".equals(argSig) || "D".equals(argSig)) ? 2 : 1;
        }
        super.visitCode(obj);
        
        initializedRegs.clear();
    }
    
    public void sawOpcode(int seen) {
        Integer size = null;
        try {
            switch (seen) {
            case ICONST_0:
            case ICONST_1:
            case ICONST_2:
            case ICONST_3:
            case ICONST_4:
            case ICONST_5:
                size = Integer.valueOf(seen - ICONST_0);
            break;
            
            case BIPUSH:
            case SIPUSH:
                size = getIntConstant();
            break;
                
            case LDC:
                Constant c = getConstantRefOperand();
                if (c instanceof ConstantInteger) {
                    size = Integer.valueOf(((ConstantInteger) c).getBytes());
                }
            break;
            
            case NEWARRAY:
            case ANEWARRAY:
                if (stack.getStackDepth() >= 1) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    size = (Integer) item.getUserValue();
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
                    Integer index = (Integer) indexItem.getConstant();
                    if (index != null) {
                        OpcodeStack.Item arrayItem = stack.getStackItem(2);
                        Integer sz = (Integer) arrayItem.getUserValue();
                        if (sz != null) {
                            if (index.intValue() >= sz.intValue()) {
                                bugReporter.reportBug(new BugInstance(this, "AIOB_ARRAY_INDEX_OUT_OF_BOUNDS", HIGH_PRIORITY)
                                            .addClass(this)
                                            .addMethod(this)
                                            .addSourceLine(this));
                            }
                        }
                        
                        int reg = arrayItem.getRegisterNumber();
                        if ((reg >= 0) && !initializedRegs.contains(Integer.valueOf(reg))) {
                            bugReporter.reportBug(new BugInstance(this, "AIOB_ARRAY_STORE_TO_NULL_REFERENCE", HIGH_PRIORITY)
                            .addClass(this)
                            .addMethod(this)
                            .addSourceLine(this));
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
                                bugReporter.reportBug(new BugInstance(this, "AIOB_ARRAY_INDEX_OUT_OF_BOUNDS", HIGH_PRIORITY)
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
                        initializedRegs.add(Integer.valueOf(getRegisterOperand()));
                } else {
                    initializedRegs.add(Integer.valueOf(getRegisterOperand()));
                } 
                break;
            }
             
        } finally {
            stack.sawOpcode(this, seen);
            if (size != null) {
                if (stack.getStackDepth() >= 1) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    item.setUserValue(size);
                }
            }
        }
    }
}
