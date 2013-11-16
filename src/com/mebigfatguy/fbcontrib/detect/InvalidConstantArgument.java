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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import javax.swing.JOptionPane;
import javax.swing.border.BevelBorder;
import javax.swing.border.EtchedBorder;

import edu.umd.cs.findbugs.BugInstance;
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

    private static final Map<Pattern, ParameterInfo<?>> PATTERNS = new HashMap<Pattern, ParameterInfo<?>>();
    static {
        PATTERNS.put(Pattern.compile("javax/swing/JOptionPane#showMessageDialog\\(Ljava/awt/Component;Ljava/lang/Object;Ljava/lang/String;I\\)V"), 
                     new ParameterInfo<Integer>(0, false, JOptionPane.ERROR_MESSAGE, JOptionPane.INFORMATION_MESSAGE, JOptionPane.PLAIN_MESSAGE, JOptionPane.WARNING_MESSAGE));
        PATTERNS.put(Pattern.compile("javax/swing/BorderFactory#createBevelBorder\\(I.*\\)Ljavax/swing/border/Border;"), 
                new ParameterInfo<Integer>(0, true, BevelBorder.LOWERED, BevelBorder.RAISED));
        PATTERNS.put(Pattern.compile("javax/swing/BorderFactory#createEtchedBorder\\(I.*\\)Ljavax/swing/border/Border;"), 
                new ParameterInfo<Integer>(0, true, EtchedBorder.LOWERED, EtchedBorder.RAISED));

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
                String sig = getSigConstantOperand();
                String mInfo = getClassConstantOperand() + "#" + getNameConstantOperand() + sig;
                for (Map.Entry<Pattern, ParameterInfo<?>> entry : PATTERNS.entrySet()) {
                   Matcher m = entry.getKey().matcher(mInfo);
                   if (m.matches()) {
                       ParameterInfo<?> info = entry.getValue();
                       OpcodeStack.Item item = stack.getStackItem(info.fromStart ? Type.getArgumentTypes(sig).length - info.parameterOffset - 1: info.parameterOffset);
                       
                       Object cons = item.getConstant();
                       if (!info.validValues.contains(cons)) {
                           int badParm = 1 + (info.fromStart ? info.parameterOffset: Type.getArgumentTypes(sig).length - info.parameterOffset - 1);
                           bugReporter.reportBug(new BugInstance(this, "ICA_INVALID_CONSTANT_ARGUMENT", NORMAL_PRIORITY)
                                                       .addClass(this)
                                                       .addMethod(this)
                                                       .addSourceLine(this)
                                                       .addString("Parameter " + badParm));
                           break;
                       }
                   }
                }
                break;
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }
    
    static class ParameterInfo<T> {
        int parameterOffset;
        boolean fromStart;
        Set<T> validValues;
        
        public ParameterInfo(int offset, boolean start, T...values) {
            parameterOffset = offset;
            fromStart = start;
            validValues = new HashSet<T>(Arrays.asList(values));
        }     
    }
}
