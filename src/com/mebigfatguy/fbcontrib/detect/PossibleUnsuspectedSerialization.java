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

import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for serialization of non-static inner classes. As this serializes
 * the enclosing class, it may unintentially bring in more to the serialization
 * than is wanted
 */
public class PossibleUnsuspectedSerialization extends BytecodeScanningDetector {

	private final BugReporter bugReporter;
	private OpcodeStack stack;
	
	/**
     * constructs a PUS detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
	 */
	public PossibleUnsuspectedSerialization(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}
	
	/**
	 * implements the visitor to setup and tear down the opcode stack
	 * 
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
	 * implements the visitor to reset the opcode stack
	 * 
	 * @param obj the context object of the currently parsed method
	 */
	@Override
	public void visitMethod(Method obj) {
		stack.resetForMethodEntry(this);
		super.visitMethod(obj);
	}
	
	/**
	 * implements the visitor to look for serialization of an object that is
	 * an non-static inner class.
	 * 
	 * @param seen the context object of the currently parsed instruction
	 */
	@Override
	public void sawOpcode(int seen) {
		try {
	        stack.precomputation(this);
	        
			if (seen == INVOKEVIRTUAL) {
				String clsName = getClassConstantOperand();
				if ("java/io/ObjectOutputStream".equals(clsName)) {
					String name = getNameConstantOperand();
					if ("writeObject".equals(name)) {
						if (stack.getStackDepth() > 0) {
							OpcodeStack.Item item = stack.getStackItem(0);
							JavaClass cls = item.getJavaClass();
							
							if ((cls != null) && cls.getClassName().contains("$") && hasOuterClassSyntheticReference(cls)) {
								bugReporter.reportBug(new BugInstance(this, "PUS_POSSIBLE_UNSUSPECTED_SERIALIZATION", NORMAL_PRIORITY)
																.addClass(this)
																.addMethod(this)
																.addSourceLine(this));
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
	
	private boolean hasOuterClassSyntheticReference(JavaClass cls) {
		Field[] fields = cls.getFields();
		for (Field f : fields) {
			if (f.isSynthetic()) {
				String sig = f.getSignature();
				if (sig.startsWith("L")) {
					sig = sig.substring(1, sig.length() - 1);
					if (cls.getClassName().startsWith(sig)) {
						return true;
					}
				}
			}
		}
		
		return false;
	}
	
}
