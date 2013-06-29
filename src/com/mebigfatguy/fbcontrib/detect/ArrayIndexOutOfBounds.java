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

import java.util.BitSet;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Method;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

public class ArrayIndexOutOfBounds extends BytecodeScanningDetector {

    private BugReporter bugReporter;
    private OpcodeStack stack;
    
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
            super.visitClassContext(classContext);
        } finally {
            stack = null;
        }
    }
    
    public void visitCode(Code obj) {
        if (prescreen(getMethod())) {
            stack.resetForMethodEntry(this);
            super.visitCode(obj);
        }
    }
    
    public void sawOpcode(int seen) {
        Integer size = null;
        try {
            switch (seen) {
            case NEWARRAY:
            case ANEWARRAY:
                if (stack.getStackDepth() >= 1) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    size = (Integer) item.getConstant();
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
    
    
    /**
     * looks for methods that contain a NEWARRAY or ANEWARRAY opcodes
     * 
     * @param method the context object of the current method
     * @return if the class uses synchronization
     */
    public boolean prescreen(Method method) {
        BitSet bytecodeSet = getClassContext().getBytecodeSet(method);
        return (bytecodeSet != null) && (bytecodeSet.get(Constants.NEWARRAY) || bytecodeSet.get(Constants.ANEWARRAY));
    }
}
