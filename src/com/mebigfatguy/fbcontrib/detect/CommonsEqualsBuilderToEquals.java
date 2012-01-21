/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2012 Bhaskar Maddala
 * Copyright (C) 2005-2012 Dave Brosius
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

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.LocalVariableTable;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;

/**
 * Find usage of EqualsBuilder from Apache commons, where the code invoke
 * equals() on the constructed object rather than isEquals()
 * 
 * <pre>
 * new EqualsBuilder().append(this.name, other.name).equals(other);
 * </pre>
 */
public class CommonsEqualsBuilderToEquals extends BytecodeScanningDetector {

	private final OpcodeStack stack;
	private final BugReporter bugReporter;

	/**
	 * constructs a CEBE detector given the reporter to report bugs on.
	 * 
	 * @param bugReporter
	 *            the sync of bug reports
	 */
	public CommonsEqualsBuilderToEquals(final BugReporter bugReporter) {
		stack = new OpcodeStack();
		this.bugReporter = bugReporter;
	}

	/**
	 * implements the visitor to pass through constructors and static
	 * initializers to the byte code scanning code. These methods are not
	 * reported, but are used to build SourceLineAnnotations for fields, if
	 * accessed.
	 * 
	 * @param obj
	 *            the context object of the currently parsed code attribute
	 */
	@Override
	public void visitCode(Code obj) {
		stack.resetForMethodEntry(this);
		LocalVariableTable lvt = getMethod().getLocalVariableTable();
		if (lvt != null) {
			super.visitCode(obj);
		}
	}

	@Override
	public void sawOpcode(int seen) {
		try {
			switch (seen) {
			case INVOKEVIRTUAL:
				String methodName = getNameConstantOperand();
				if ("equals".equals(methodName)
						&& "(Ljava/lang/Object;)Z"
								.equals(getSigConstantOperand())) {
					String calledClass = stack.getStackItem(1).getSignature();
					if ("Lorg/apache/commons/lang3/builder/EqualsBuilder;"
							.equals(calledClass)
							|| "org/apache/commons/lang/builder/EqualsBuilder"
									.equals(calledClass)) {
						bugReporter.reportBug(new BugInstance(this,
								"CEBE_COMMONS_EQUALS_BUILDER_ISEQUALS",
								HIGH_PRIORITY).addClass(this).addMethod(this)
								.addSourceLine(this));
					}
				}
			}
		} finally {
			super.sawOpcode(seen);
			stack.sawOpcode(this, seen);
		}
	}
}
