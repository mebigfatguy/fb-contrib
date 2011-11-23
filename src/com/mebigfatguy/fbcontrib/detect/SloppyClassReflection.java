/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2011 Dave Brosius
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

import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantString;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.LocalVariable;
import org.apache.bcel.classfile.LocalVariableTable;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for methods that use Class.forName("XXX") to load a class object
 * for a class that is already referenced by this class. It is simpler to just use
 * XXX.class, and doing so protects the integrity of this code from such transformations
 * as obfuscation. Use of Class.forName should only be used when the class in question
 * isn't already statically bound to this context.
 */
public class SloppyClassReflection extends BytecodeScanningDetector
{
	enum State {COLLECT, SEEN_NOTHING, SEEN_LDC}
	
	private final BugReporter bugReporter;
	private Set<String> refClasses;
	private State state;
	private String clsName;
	
	/**
     * constructs a SCR detector given the reporter to report bugs on

     * @param bugReporter the sync of bug reports
     */
	public SloppyClassReflection(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}

	/**
	 * overrides the visitor to collect all class references
	 * 
	 * @param classContext the class context of the currently visited class
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			refClasses = new HashSet<String>();
			refClasses.add(classContext.getJavaClass().getClassName());
			state = State.COLLECT;
			super.visitClassContext(classContext);
			state = State.SEEN_NOTHING;
			super.visitClassContext(classContext);
		} finally {
			refClasses = null;
		}
	}
	
	/**
	 * overrides the visitor reset the opcode stack
	 * 
	 * @param obj the method object of the currently parsed method
	 */	
	@Override
	public void visitMethod(Method obj) {
		if ("<clinit>".equals(obj.getName()))
			return;
		
		if (state == State.COLLECT) {
			Type[] argTypes = obj.getArgumentTypes();
			for (Type t : argTypes)
				addType(t);
			Type resultType = obj.getReturnType();
			addType(resultType);
			LocalVariableTable lvt = obj.getLocalVariableTable();
			if (lvt != null) {
				LocalVariable[] lvs = lvt.getLocalVariableTable();
				if (lvs != null) {
                    for (LocalVariable lv : lvs) {
						if (lv != null) {
							Type t = Type.getType(lv.getSignature());
							addType(t);
						}
					}
				}
			}
		} else
			state = State.SEEN_NOTHING;
		super.visitMethod(obj);
	}
	
	@Override
	public void visitField(Field obj) {
		if (state == State.COLLECT) {
			Type t = obj.getType();
			addType(t);
		}
	}
	
	/**
	 * overrides the visitor to find class loading that is non obfuscation proof
	 * 
	 * @param seen the opcode that is being visited
	 */	
	@Override
	public void sawOpcode(int seen) {
		switch (state) {
			case COLLECT:
				if ((seen == INVOKESTATIC) 
			    ||  (seen == INVOKEVIRTUAL)
			    ||  (seen == INVOKEINTERFACE)
			    ||  (seen == INVOKESPECIAL)) {
					refClasses.add(getClassConstantOperand());
					String signature = getSigConstantOperand();
					Type[] argTypes = Type.getArgumentTypes(signature);
					for (Type t : argTypes)
						addType(t);
					Type resultType = Type.getReturnType(signature);
					addType(resultType);
				}
			break;
			
			case SEEN_NOTHING:
				if ((seen == LDC) || (seen == LDC_W)) {
					Constant c = getConstantRefOperand();
					if (c instanceof ConstantString) {
						clsName = ((ConstantString) c).getBytes(getConstantPool());
						state = State.SEEN_LDC;
					}
				}
			break;
			
			case SEEN_LDC:
				if ((seen == INVOKESTATIC) 
				&&  ("forName".equals(getNameConstantOperand())) 
				&&  ("java/lang/Class".equals(getClassConstantOperand()))) {
					if (refClasses.contains(clsName)) {
						bugReporter.reportBug(new BugInstance(this, "SCR_SLOPPY_CLASS_REFLECTION", NORMAL_PRIORITY)
									.addClass(this)
									.addMethod(this)
									.addSourceLine(this));
					}
				}
				state = State.SEEN_NOTHING;
			break;
		}
	}
	
	/**
	 * add the type string represented by the type to the refClasses set if it is a reference
	 * 
	 * @param t the type to add
	 */
	private void addType(Type t) {
		String signature = t.getSignature();
		if (signature.charAt(0) == 'L')
			refClasses.add(signature.substring(1, signature.length() - 1));
	}
}
