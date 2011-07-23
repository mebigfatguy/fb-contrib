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

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for calls to the wait method on mutexes defined in the java.util.concurrent package
 * where it is likely that await was intended.
 */
public class SuspiciousWaitOnConcurrentObject extends BytecodeScanningDetector
{
	private static final Set<String> concurrentAwaitClasses = new HashSet<String>();
	static {
		concurrentAwaitClasses.add("java.util.concurrent.CountDownLatch");
		concurrentAwaitClasses.add("java.util.concurrent.CyclicBarrier");
	}
	
	private BugReporter bugReporter;
	private OpcodeStack stack;
	
	/**
     * constructs a SWCO detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
	 */
	public SuspiciousWaitOnConcurrentObject(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}
		
	/**
	 * implements the visitor to check for class file version 1.5 or better
	 * 
	 * @param classContext the context object of the currently parsed class
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			JavaClass cls = classContext.getJavaClass();
			int major = cls.getMajor();
			if (major >= Constants.MAJOR_1_5) {
				stack = new OpcodeStack();
				super.visitClassContext(classContext);
			}
		} finally {
			stack = null;
		}
	}
	
	/**
	 * implements the visitor to reset the opcode stack
	 * 
	 * @param obj the context object for the currently parsed method
	 */
	@Override
	public void visitMethod(Method obj) {
		stack.resetForMethodEntry(this);
	}
	
	/**
	 * implements the visitor to look for calls to wait, on java.util.concurrent
	 * classes that define await.
	 * 
	 * @param seen the opcode of the currently visited instruction
	 */
	@Override
	public void sawOpcode(int seen) {
		try {
			stack.mergeJumps(this);
			
			if (seen == INVOKEVIRTUAL) {
				String methodName = getNameConstantOperand();
				if ("wait".equals(methodName)) {
					if (stack.getStackDepth() > 0) {
						OpcodeStack.Item itm = stack.getStackItem(0);
						JavaClass cls = itm.getJavaClass();
						if (cls != null) {
							String clsName = cls.getClassName();
							if (concurrentAwaitClasses.contains(clsName)) {
								bugReporter.reportBug(new BugInstance(this, "SWCO_SUSPICIOUS_WAIT_ON_CONCURRENT_OBJECT", NORMAL_PRIORITY)
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
}
