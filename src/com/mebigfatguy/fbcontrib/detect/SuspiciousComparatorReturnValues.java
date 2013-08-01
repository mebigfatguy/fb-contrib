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

import java.util.HashMap;
import java.util.Map;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.generic.Type;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for class that implement Comparator or Comparable, and whose compare or compareTo
 * methods return constant values only, but that don't represent the three possible choice
 * (a negative number, 0, and a positive number).
 */
public class SuspiciousComparatorReturnValues extends BytecodeScanningDetector 
{
	private static Map<JavaClass, String> compareClasses = new HashMap<JavaClass, String>();
	static {
		try {
			compareClasses.put(Repository.lookupClass("java/lang/Comparable"), "compareTo:1:I");
			compareClasses.put(Repository.lookupClass("java/util/Comparator"), "compare:2:I");
		} catch (ClassNotFoundException cnfe) {
		}
	}
	
	private OpcodeStack stack;
	private final BugReporter bugReporter;
	private String[] methodInfo;
	private boolean indeterminate;
	private boolean seenNegative;
	private boolean seenPositive;
	private boolean seenZero;
	
	
	/**
     * constructs a DRE detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
	 */
	public SuspiciousComparatorReturnValues(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}
	
	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			JavaClass cls = classContext.getJavaClass();
			for (Map.Entry<JavaClass, String> entry : compareClasses.entrySet()) {
				if (cls.implementationOf(entry.getKey())) {
					methodInfo = entry.getValue().split(":");
					stack = new OpcodeStack();
					super.visitClassContext(classContext);
					break;
				}
			}
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
		} finally {
			methodInfo = null;
			stack = null;
		}
	}
	
	@Override
	public void visitCode(Code obj) {
		String methodName = getMethodName();
		String methodSig = getMethodSig();
		if (methodName.equals(methodInfo[0])
		&&  methodSig.endsWith(methodInfo[2])
		&&  (Type.getArgumentTypes(methodSig).length == Integer.parseInt(methodInfo[1]))) {
			stack.resetForMethodEntry(this);
			indeterminate = false;
			seenNegative = false;
			seenPositive = false;
			seenZero = false;
			super.visitCode(obj);
			if (!indeterminate) {
				boolean seenAll = seenNegative & seenPositive & seenZero;
				if (!seenAll) {
					bugReporter.reportBug(new BugInstance(this, "SC_SUSPICIOUS_COMPARATOR_RETURN_VALUES", NORMAL_PRIORITY)
								.addClass(this)
								.addMethod(this)
								.addSourceLine(this, 0));
				}
			}
		}
	}
	
	@Override
	public void sawOpcode(int seen) {
		try {
			if (indeterminate)
				return;
			
			if (seen == IRETURN) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item item = stack.getStackItem(0);
					Integer returnValue = (Integer)item.getConstant();
					if (returnValue == null)
						indeterminate = true;
					else {
						int v = returnValue.intValue();
						if (v < 0)
							seenNegative = true;
						else if (v > 0)
							seenPositive = true;
						else
							seenZero = true;
					}
				} else
					indeterminate = true;
			} else if ((seen == GOTO) || (seen == GOTO_W)) {
				if (stack.getStackDepth() > 0)
					indeterminate = true;
			} else if (seen == ATHROW) {
			    if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    String exSig = item.getSignature();
                    if ("Ljava/lang/UnsupportedOperationException;".equals(exSig)) {
                        indeterminate = true;
                    }
			    }
			}
		} finally {
			stack.sawOpcode(this, seen);
		}
	}
}
