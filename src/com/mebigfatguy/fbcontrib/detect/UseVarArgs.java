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

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.Detector;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.visitclass.PreorderVisitor;

public class UseVarArgs extends PreorderVisitor implements Detector 
{
	private final BugReporter bugReporter;
	private JavaClass javaClass;
	
	public UseVarArgs(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}
	
	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			javaClass = classContext.getJavaClass();
			if (javaClass.getMajor() >= Constants.MAJOR_1_5) {
				javaClass.accept(this);
			}
		} finally {
			javaClass = null;
		}
	}
	
	@Override
	public void visitMethod(Method obj) {
		try {
			if (obj.isSynthetic()) {
				return;
			}
			
			Type[] types = obj.getArgumentTypes();
			if ((types.length == 0) || (types.length > 2)) {
				return;
			}
			
			if ((obj.getAccessFlags() & Constants.ACC_VARARGS) != 0) {
				return;
			}
			
			String lastParmSig = types[types.length-1].getSignature();
			if (!lastParmSig.startsWith("[") || lastParmSig.startsWith("[[")) {
				return;
			}
			
			if (hasSimilarParms(types)) {
				return;
			}
			
			if (obj.isStatic() && "main".equals(obj.getName()) && "([Ljava/lang/String;)V".equals(obj.getSignature())) {
				return;
			}
	
			if (!obj.isPrivate() && !obj.isStatic() && isInherited(obj)) {
				return;
			}
			
			super.visitMethod(obj);
			bugReporter.reportBug(new BugInstance(this, "UVA_USE_VAR_ARGS", LOW_PRIORITY)
						.addClass(this)
						.addMethod(this));
			
			
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
		}
	}
	
	@Override
	public void report() {
	}
	
	private boolean hasSimilarParms(Type[] argTypes) {
		
		for (int i = 0; i < argTypes.length - 1; i++) {
			if (argTypes[i].getSignature().startsWith("[")) {
				return true;
			}
		}
		
		String baseType = argTypes[argTypes.length-1].getSignature();
		while (baseType.startsWith("[")) {
			baseType = baseType.substring(1);
		}
		
		for (int i = 0; i < argTypes.length - 1; i++) {
			if (argTypes[i].getSignature().equals(baseType)) {
				return true;
			}
		}
		
		return false;
	}
	
	private boolean isInherited(Method m) throws ClassNotFoundException {
		JavaClass[] infs = javaClass.getAllInterfaces();
		for (JavaClass inf : infs) {
			if (hasMethod(inf, m))
				return true;
		}
		
		JavaClass[] sups = javaClass.getSuperClasses();
		for (JavaClass sup : sups) {
			if (hasMethod(sup, m))
				return true;
		}
		
		return false;
	}
	
	private boolean hasMethod(JavaClass c, Method candidateMethod) {
		String name = candidateMethod.getName();
		String sig = candidateMethod.getSignature();
		
		for (Method method : c.getMethods()) {
			if (!method.isStatic() && method.getName().equals(name) && method.getSignature().equals(sig)) {
				return true;
			}
		}
		
		return false;
	}
}
