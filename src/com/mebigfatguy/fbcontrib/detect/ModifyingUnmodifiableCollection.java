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

import java.util.HashSet;
import java.util.Set;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;

import com.mebigfatguy.fbcontrib.collect.ImmutabilityType;
import com.mebigfatguy.fbcontrib.collect.MethodInfo;
import com.mebigfatguy.fbcontrib.collect.Statistics;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

@CustomUserValue
public class ModifyingUnmodifiableCollection extends BytecodeScanningDetector {

    private static Set<String> MODIFYING_METHODS = null;
    private static JavaClass LIST_CLASS = null;
    private static JavaClass SET_CLASS = null;
    private static JavaClass MAP_CLASS = null;
    
    static {
        try {
            MODIFYING_METHODS = new HashSet<String>();
            MODIFYING_METHODS.add("add(Ljava/lang/Object;)Z");
            MODIFYING_METHODS.add("remove(Ljava/lang/Object;)Z");
            MODIFYING_METHODS.add("addAll(Ljava/util/Collection;)Z");
            MODIFYING_METHODS.add("retainAll(Ljava/util/Collection;)Z");
            MODIFYING_METHODS.add("removeAll(Ljava/util/Collection;)Z");
            MODIFYING_METHODS.add("put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
            MODIFYING_METHODS.add("remove(Ljava/lang/Object;)Ljava/lang/Object;");
            MODIFYING_METHODS.add("putAll(Ljava/util/Map;)V;");
            
            LIST_CLASS = Repository.lookupClass("java.util.List");
            SET_CLASS = Repository.lookupClass("java.util.Set");
            MAP_CLASS = Repository.lookupClass("java.util.Map");
        } catch (ClassNotFoundException cnfe) {
        }
    }
    
    private BugReporter bugReporter;
    private OpcodeStack stack;
    private ImmutabilityType reportedType;
    
    public ModifyingUnmodifiableCollection(BugReporter reporter) {
        bugReporter = reporter;
    }
    
    @Override
    public void visitClassContext(ClassContext context) {
        try {
            stack = new OpcodeStack();
            super.visitClassContext(context);
        } finally {
            stack = null;
        }
    }
    
    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        reportedType = ImmutabilityType.UNKNOWN;
        super.visitCode(obj);
    }
    
    public void sawOpcode(int seen) {
        
        if (reportedType == ImmutabilityType.IMMUTABLE) {
            return;
        }
        ImmutabilityType imType = null;

        try {
            stack.precomputation(this);
            
            switch (seen) {
                case INVOKESTATIC:
                case INVOKEINTERFACE:
                case INVOKESPECIAL:
                case INVOKEVIRTUAL: {
                    String className = getClassConstantOperand();
                    String methodName = getNameConstantOperand();
                    String signature = getSigConstantOperand();
                    
                    MethodInfo mi = Statistics.getStatistics().getMethodStatistics(className, methodName, signature);
                    imType = mi.getImmutabilityType();
                    
                    if ((seen == INVOKEINTERFACE) && MODIFYING_METHODS.contains(methodName + signature) && (isCollection(className))) {
                        if (stack.getStackDepth() > 0) {
                            OpcodeStack.Item item = stack.getStackItem(0);
                            ImmutabilityType type = (ImmutabilityType) item.getUserValue();
                            
                            if ((type == ImmutabilityType.IMMUTABLE) || ((type == ImmutabilityType.POSSIBLY_IMMUTABLE) && (reportedType != ImmutabilityType.POSSIBLY_IMMUTABLE))) {
                                bugReporter.reportBug(new BugInstance(this, "MUC_MODIFYING_UNMODIFIABLE_COLLECTION", (type == ImmutabilityType.IMMUTABLE) ? HIGH_PRIORITY : NORMAL_PRIORITY)
                                                          .addClass(this)
                                                          .addMethod(this)
                                                          .addSourceLine(this));
                                reportedType = type;
                            }
                        }
                    }
                }
                break;
            }

        } finally {
            stack.sawOpcode(this, seen);
            if (imType != null) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    item.setUserValue(imType);
                }
            }
            
        }
    }
    
    private boolean isCollection(String clsName) {
        
        try {
            JavaClass cls = Repository.lookupClass(clsName);
            return (cls.implementationOf(LIST_CLASS) || cls.implementationOf(SET_CLASS) || cls.implementationOf(MAP_CLASS));
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
            return false;
        }
    }
}
