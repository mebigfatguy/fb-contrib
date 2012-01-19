package com.mebigfatguy.fbcontrib.detect;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.LocalVariableTable;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;

/**
 * Find usage of HashCodeBuilder from Apache commons, where the code invokes
 * hashCode() on the constructed object rather than toHashCode()
 * 
 * <pre>
 * new HashCodeBuilder().append(this.name).hashCode();
 * </pre>
 */
public class CommonsHashcodeBuilderToHashcode extends BytecodeScanningDetector {

	private final OpcodeStack stack;
	private final BugReporter bugReporter;

	/**
	 * constructs a CHTH detector given the reporter to report bugs on.
	 * 
	 * @param bugReporter
	 *            the sync of bug reports
	 */
	public CommonsHashcodeBuilderToHashcode(final BugReporter bugReporter) {
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
				if ("hashCode".equals(methodName)
						&& "()I".equals(getSigConstantOperand())) {
					String calledClass = stack.getStackItem(0).getSignature();
					if ("Lorg/apache/commons/lang3/builder/HashCodeBuilder;"
							.equals(calledClass)
							|| "org/apache/commons/lang/builder/HashCodeBuilder"
									.equals(calledClass)) {
						bugReporter.reportBug(new BugInstance(this,
								"CHTH_COMMONS_HASHCODE_BUILDER_TOHASHCODE",
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
