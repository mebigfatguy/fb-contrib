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
