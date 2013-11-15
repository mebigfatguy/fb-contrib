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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.classfile.Method;

import javax.swing.JOptionPane;

import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * Looks for jdk method calls where a parameter expects a constant value, because the api
 * was created before enums. Reports values that are not considered valid values, and may 
 * cause problems with use.
 */
public class InvalidConstantArgument extends BytecodeScanningDetector {

    private static final Map<String, ParameterInfo<?>> PATTERNS = new HashMap<String, ParameterInfo<?>>();
    static {
        PATTERNS.put("javax.swing.JOptionPane#(showMessageDialog(Ljava/awt/Component;Ljava/lang/Object;Ljava/lang/String;I)V", 
                     new ParameterInfo<Integer>(4, JOptionPane.ERROR_MESSAGE, JOptionPane.INFORMATION_MESSAGE, JOptionPane.PLAIN_MESSAGE, JOptionPane.WARNING_MESSAGE));
    }
    
    private BugReporter bugReporter;
    private OpcodeStack stack;

    /**
     * constructs a ICA detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
     */
    public InvalidConstantArgument(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }
    
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            super.visitClassContext(classContext); 
        } finally {
            stack = null;
        }
    }
    
    @Override
    public void visitMethod(Method obj) {
        stack.resetForMethodEntry(this);
        super.visitMethod(obj);
    }
    
    public void sawOpcode(int seen) {
        try {
            switch (seen) {
            case INVOKESTATIC:
            case INVOKEINTERFACE:
            case INVOKEVIRTUAL:
                
                break;
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }
    
    static class ParameterInfo<T> {
        int parameterIndex;
        Set<T> validValues;
        
        public ParameterInfo(int index, T...values) {
            parameterIndex = index;
            validValues = new HashSet<T>(Arrays.asList(values));
        }     
    }
}
