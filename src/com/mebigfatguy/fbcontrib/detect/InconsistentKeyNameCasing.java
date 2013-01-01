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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.generic.Type;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.SourceLineAnnotation;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for calls to HttpRequest.getParameter with parameters of the same
 * name with different cases like 'id' and 'Id'.
 */
public class InconsistentKeyNameCasing extends BytecodeScanningDetector
{	
    private static final String HTTP_SESSION = "javax/servlet/http/HttpSession";
    private static final String HTTP_SERVLET_REQUEST = "javax/servlet/http/HttpServletRequest";
    private static final String GET_ATTRIBUTE = "getAttribute";
    private static final String SET_ATTRIBUTE = "setAttribute";
    private static final String GET_PARAMETER = "getParameter";
    private static final String GET_ATTRIBUTE_SIG = "(Ljava/lang/String;)Ljava/lang/Object;";
    private static final String SET_ATTRIBUTE_SIG = "(Ljava/lang/String;Ljava/lang/Object;)V";
    private static final String GET_PARAMETER_SIG = "(Ljava/lang/String;)Ljava/lang/String;";
	
    enum KeyType { 
    	ATTRIBUTE("IKNC_INCONSISTENT_HTTP_ATTRIBUTE_CASING"), 
    	PARAMETER("IKNC_INCONSISTENT_HTTP_PARAM_CASING");
    	
    	private String key;
    	
    	KeyType(String descriptionKey) {
    		key = descriptionKey;
    	}
    	
    	public String getDescription() {
    		return key;
    	}
    }
    
    BugReporter bugReporter;
    OpcodeStack stack;
    Map<KeyType, Map<String, Map<String, List<SourceInfo>>>> parmInfo;
    
    /**
     * constructs a IKNC detector given the reporter to report bugs on
     * @param reporter the sync of bug reports
     */
    public InconsistentKeyNameCasing(BugReporter reporter) {
        bugReporter = reporter;
        parmInfo = new EnumMap<KeyType, Map<String, Map<String, List<SourceInfo>>>>(KeyType.class);
        parmInfo.put(KeyType.ATTRIBUTE, new HashMap<String, Map<String, List<SourceInfo>>>());
        parmInfo.put(KeyType.PARAMETER, new HashMap<String, Map<String, List<SourceInfo>>>());
    }
    
    /**
     * implements the visitor to create the opcode stack
     * @param classContext the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
        }
    }
    
    /**
     * implements the visitor to reset the opcode stack for a new method
     * @param obj the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        super.visitCode(obj);
    }
    
    /**
     * implements the visitor to look for calls to HttpServletRequest.getParameter
     * and collect what the name of the key is.
     * @param seen the opcode of the currently parsed instruction
     */
    @Override
    public void sawOpcode(int seen) {
        try {
            if (seen == INVOKEINTERFACE) {
            	KeyType type = isKeyAccessMethod(seen);
            	if (type != null) {
            		int numParms = Type.getArgumentTypes(getSigConstantOperand()).length;
                    if (stack.getStackDepth() >= numParms) {
                        OpcodeStack.Item item = stack.getStackItem(numParms - 1);
                        String parmName = (String)item.getConstant();
                        if (parmName != null)
                        {
                            String upperParmName = parmName.toUpperCase(Locale.getDefault());
                            Map<String, Map<String, List<SourceInfo>>> typeMap = parmInfo.get(KeyType.PARAMETER);
                            Map<String, List<SourceInfo>> parmCaseInfo = typeMap.get(upperParmName);
                            if (parmCaseInfo == null) {
                                parmCaseInfo = new HashMap<String, List<SourceInfo>>();
                                typeMap.put(upperParmName, parmCaseInfo);
                            }
                            
                            List<SourceInfo> annotations = parmCaseInfo.get(parmName);
                            if (annotations == null) {
                                annotations = new ArrayList<SourceInfo>();
                                parmCaseInfo.put(parmName, annotations);
                            }
                            
                            annotations.add(new SourceInfo(getClassName(), getMethodName(), getMethodSig(), getMethod().isStatic(), SourceLineAnnotation.fromVisitedInstruction(getClassContext(), this, getPC())));
                        }
                    }
            	}
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }
    
    /**
     * implements the visitor to look for the collected parm names, and look for duplicates that are 
     * different in casing only.
     */
    @Override
    public void report() {
    	for (Map.Entry<KeyType, Map<String, Map<String, List<SourceInfo>>>> entry : parmInfo.entrySet()) {
    		KeyType type = entry.getKey();
    		Map<String, Map<String, List<SourceInfo>>> typeMap = entry.getValue();

	        for (Map<String, List<SourceInfo>> parmCaseInfo : typeMap.values()) {
	            if (parmCaseInfo.size() > 1) {
	               BugInstance bi = new BugInstance(this, type.getDescription(), NORMAL_PRIORITY);
	                
	               for (Map.Entry<String, List<SourceInfo>> sourceInfos :parmCaseInfo.entrySet()) {
	                   for (SourceInfo sourceInfo : sourceInfos.getValue()) {
	                       bi.addClass(sourceInfo.clsName);
	                       bi.addMethod(sourceInfo.clsName, sourceInfo.methodName, sourceInfo.signature, sourceInfo.isStatic);
	                       bi.addSourceLine(sourceInfo.srcLine);
	                   	   bi.addString(sourceInfos.getKey());
	                   }
	               }
	               
	               bugReporter.reportBug(bi);
	            }
	        }
    	}
        parmInfo.clear();
    }
    
    private KeyType isKeyAccessMethod(int seen) {
    	if (seen == INVOKEINTERFACE) {
            String clsName = getClassConstantOperand();
            if (HTTP_SESSION.equals(clsName)) {
                String methodName = getNameConstantOperand();
                if (GET_ATTRIBUTE.equals(methodName)) {
                    String signature = getSigConstantOperand();
                    return (GET_ATTRIBUTE_SIG.equals(signature)) ? KeyType.ATTRIBUTE : null;
                } else if (SET_ATTRIBUTE.equals(methodName)) {
                    String signature = getSigConstantOperand();
                    return (SET_ATTRIBUTE_SIG.equals(signature)) ? KeyType.ATTRIBUTE : null;
                }
            } else if (HTTP_SERVLET_REQUEST.equals(clsName)) {
                String methodName = getNameConstantOperand();
                if (GET_PARAMETER.equals(methodName)) {
                    String signature = getSigConstantOperand();
                    return (GET_PARAMETER_SIG.equals(signature)) ? KeyType.PARAMETER : null;
                }
            }
    	}
    	
    	return null;
    }
    /**
     * a holder for location information of a getParameter call
     */
    static class SourceInfo
    {
        String clsName;
        String methodName;
        String signature;
        boolean isStatic;
        SourceLineAnnotation srcLine;
        
        public SourceInfo(String cls, String method, String sig, boolean mStatic, SourceLineAnnotation annotation) {
            clsName = cls;
            methodName = method;
            signature = sig;
            isStatic = mStatic;
            srcLine = annotation;
        }
    }
}
