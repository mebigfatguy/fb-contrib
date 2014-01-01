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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.SourceLineAnnotation;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for constructors of non final classes that make method calls to non final methods.
 * As these methods could be overridden, the overridden method will be accessing an object that
 * is only partially constructed, perhaps causing problems.
 */
public class PartiallyConstructedObjectAccess extends BytecodeScanningDetector
{
	private final BugReporter bugReporter;
	private OpcodeStack stack;
	private Map<Method, Map<Method, SourceLineAnnotation>> methodToCalledMethods;
	private boolean reportedCtor;
	private boolean isCtor;
	
	/**
     * constructs a PCOA detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
	 */
	public PartiallyConstructedObjectAccess(final BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}
	
	/**
	 * implements the visitor to set up the stack and methodToCalledmethods map
	 * reports calls to public non final methods from methods called from constructors.
	 * 
	 * @param classContext the context object of the currently parsed class
	 */
	@Override
	public void visitClassContext(final ClassContext classContext) {
		try {
			JavaClass cls = classContext.getJavaClass();
			if ((cls.getAccessFlags() & Constants.ACC_FINAL) == 0) {
				stack = new OpcodeStack();
				methodToCalledMethods = new HashMap<Method, Map<Method, SourceLineAnnotation>>();
				super.visitClassContext(classContext);
				
				if (methodToCalledMethods.size() > 0)
					reportChainedMethods();
			}
		} finally {
			stack = null;
			methodToCalledMethods = null;
		}
	}
	
	@Override
	public void visitCode(final Code obj) {
		stack.resetForMethodEntry(this);
		String methodName = getMethodName();
		isCtor = "<init>".equals(methodName);
		
		if (!"<clinit>".equals(methodName)) {
			Method m = getMethod();
			methodToCalledMethods.put(m, new HashMap<Method, SourceLineAnnotation>());
			reportedCtor = false;
			
			super.visitCode(obj);
			if (reportedCtor || (methodToCalledMethods.get(m).isEmpty()))
				methodToCalledMethods.remove(getMethod());
		}
	}
	
	@Override
	public void sawOpcode(int seen) {
		if (reportedCtor)
			return;
		
		try {
	        stack.precomputation(this);
			
			if ((seen == INVOKEVIRTUAL) || (seen == INVOKEINTERFACE) || (seen == INVOKESPECIAL)) {
				int parmCount = Type.getArgumentTypes(getSigConstantOperand()).length;
				if (stack.getStackDepth() > parmCount) {
					OpcodeStack.Item itm = stack.getStackItem(parmCount);
					if (itm.getRegisterNumber() == 0) {
						JavaClass cls = itm.getJavaClass();
						if (cls != null) {
							Method m = findMethod(cls, getNameConstantOperand(), getSigConstantOperand());
							if (m != null) {
								if ((m.getAccessFlags() & Constants.ACC_FINAL) == 0) {
									if (isCtor && (seen != INVOKESPECIAL)) {
										bugReporter.reportBug( new BugInstance(this, "PCOA_PARTIALLY_CONSTRUCTED_OBJECT_ACCESS", NORMAL_PRIORITY)
											.addClass(this)
											.addMethod(this)
											.addSourceLine(this, getPC()));
										reportedCtor = true;
									} else {
										if (!"<init>".equals(m.getName())) {
											Map<Method, SourceLineAnnotation> calledMethods = methodToCalledMethods.get(getMethod());
											calledMethods.put(m, SourceLineAnnotation.fromVisitedInstruction(this));
										}
									}
								}
							}
						}
					}
				}
			}
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
		} finally {
			stack.sawOpcode(this, seen);
		}
	}
	
	private Method findMethod(final JavaClass cls, final String methodName, final String methodSig) {
		Method[] methods = cls.getMethods();
		for (Method m : methods) {
			if (methodName.equals(m.getName()) && methodSig.equals(m.getSignature())) {
				return m;
			}
		}
		
		return null;
	}
	
	private void reportChainedMethods() {
		Set<Method> checkedMethods = new HashSet<Method>();
		
		JavaClass cls = getClassContext().getJavaClass();
		for (Map.Entry<Method, Map<Method, SourceLineAnnotation>> entry : methodToCalledMethods.entrySet()) {
			Method m = entry.getKey();
			if ("<init>".equals(m.getName())) {
				checkedMethods.clear();
				List<SourceLineAnnotation> slas = foundPrivateInChain(m, checkedMethods);
				if (slas != null) {
					BugInstance bi = new BugInstance(this, "PCOA_PARTIALLY_CONSTRUCTED_OBJECT_ACCESS", LOW_PRIORITY)
							.addClass(cls)
							.addMethod(cls, m);
				    for (SourceLineAnnotation sla : slas)
						bi.addSourceLine(sla);
				    bugReporter.reportBug(bi);
				}
			}
		}
	}
	
	private List<SourceLineAnnotation> foundPrivateInChain(Method m, Set<Method> checkedMethods) {
		Map<Method, SourceLineAnnotation> calledMethods = methodToCalledMethods.get(m);
		if (calledMethods != null) {
			for (Map.Entry<Method, SourceLineAnnotation> entry : calledMethods.entrySet()) {
				Method cm = entry.getKey();
				if (checkedMethods.contains(cm))
					continue;
				
				if (!cm.isPrivate() && (cm.getAccessFlags() & Constants.ACC_FINAL) == 0) {
					List<SourceLineAnnotation> slas = new LinkedList<SourceLineAnnotation>();
					slas.add(entry.getValue());
					return slas;
				}
				
				checkedMethods.add(cm);
				List<SourceLineAnnotation> slas = foundPrivateInChain(cm, checkedMethods);
				if (slas != null) {
					slas.add(0, entry.getValue());
					return slas;
				}
			}
		}
		
		return null;
	}
}
