/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2019 Dave Brosius
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

import javax.annotation.Nullable;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantString;
import org.apache.bcel.classfile.JavaClass;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.ch.Subtypes2;
import edu.umd.cs.findbugs.util.ClassName;

/**
 * Looks for following the append methods of interface java.lang.Appendable
 * <code>append(CharSeq csq)</code>
 * <code>append (CharSeq csq, int start, int end)</code> Reports if
 * <code>toString</code> is used on the <code>CharSeq</code>
 */
@CustomUserValue
public class InefficientAppendableAppend extends BytecodeScanningDetector {

	private enum AppendType {
		CLEAR, TOSTRING
	};

	private BugReporter bugReporter;
	private OpcodeStack stack;
	private boolean sawLDCEmpty;

	/**
	 * constructs a IAA detector given the reporter to report bugs on
	 *
	 * @param bugReporter the sync of bug reports
	 */
	public InefficientAppendableAppend(final BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}

	/**
	 * implements the visitor to create and clear the stack
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
	 * implements the visitor to create and clear the stack
	 *
	 * @param obj the context object of the currently parsed code block
	 */
	@Override
	public void visitCode(final Code obj) {
		if (obj.getCode() != null) {
			stack.resetForMethodEntry(this);
			sawLDCEmpty = false;
			super.visitCode(obj);
		}
	}

	@Override
	public void sawOpcode(final int seen) {
		AppendType userValue = null;

		try {
			stack.precomputation(this);

			if (seen == Const.INVOKEVIRTUAL || seen == Const.INVOKEINTERFACE) {
				userValue = sawInvokeVirtualOrInterface();
			} else if ((seen == Const.GOTO) || (seen == Const.GOTO_W)) {
				int depth = stack.getStackDepth();
				for (int i = 0; i < depth; i++) {
					OpcodeStack.Item itm = stack.getStackItem(i);
					itm.setUserValue(null);
				}
			} else if ((seen == Const.LDC) || (seen == Const.LDC_W)) {
				Constant c = getConstantRefOperand();
				if (c instanceof ConstantString) {
					String s = ((ConstantString) c).getBytes(getConstantPool());
					if (s.length() == 0) {
						sawLDCEmpty = true;
					}
				}
				// ((seen >= Const.ALOAD_0) && (seen <= Const.ALOAD_3))
			} else if (OpcodeUtils.isALoad(seen)) {
				userValue = AppendType.CLEAR;
			}
		} finally {
			handleOpcode(seen);
			if ((userValue != null) && (stack.getStackDepth() > 0)) {
				OpcodeStack.Item itm = stack.getStackItem(0);
				itm.setUserValue(userValue);
			}
		}
	}

	private void handleOpcode(final int seen) {
		TernaryPatcher.pre(stack, seen);
		stack.sawOpcode(this, seen);
		TernaryPatcher.post(stack, seen);
	}

	private AppendType sawInvokeVirtualOrInterface() {
		AppendType userValue = null;
		String calledClass = getClassConstantOperand();
		if (Subtypes2.instanceOf((ClassName.toDottedClassName(calledClass)), "java.lang.CharSequence")
				&& Values.TOSTRING.equals(getNameConstantOperand())) {
			userValue = AppendType.TOSTRING;
			return userValue;
		} else if (Subtypes2.instanceOf(ClassName.toDottedClassName(calledClass), "java.lang.Appendable")) {
			String methodName = getNameConstantOperand();
			if ("append".equals(methodName)) {
				int numArgs = getNumberArguments(getSigConstantOperand());
				OpcodeStack.Item itm = getAppendableItemAt(numArgs);
				if (itm != null) {
					userValue = (AppendType) itm.getUserValue();
				}

				if (stack.getStackDepth() > 0) {
					itm = stack.getStackItem(numArgs - 1);
					AppendType uv = (AppendType) itm.getUserValue();
					if (uv != null && uv.equals(AppendType.TOSTRING)) {
						bugReporter.reportBug(new BugInstance(this, BugType.IAA_INEFFICIENT_APPENDABLE_APPEND.name(),
								Values.TOSTRING.equals(getMethodName()) ? LOW_PRIORITY : NORMAL_PRIORITY).addClass(this)
										.addMethod(this).addSourceLine(this));
					}
				}
			}
		}
		return userValue;
	}

	/*
	 * @Nullable private OpcodeStack.Item getCharSequenceItemAt(int depth) { if
	 * (stack.getStackDepth() > depth) { OpcodeStack.Item itm =
	 * stack.getStackItem(depth); try { JavaClass cls = itm.getJavaClass(); if
	 * (Subtypes2.instanceOf(cls, "java.lang.CharSequence")) return itm; } catch
	 * (ClassNotFoundException e) { return null; }
	 * 
	 * }
	 * 
	 * return null; }
	 */

	@Nullable
	private OpcodeStack.Item getAppendableItemAt(int depth) {
		if (stack.getStackDepth() > depth) {
			OpcodeStack.Item itm = stack.getStackItem(depth);
			try {
				// This piece of code only required to avoid duplidate reporting by IAA and ISB
				// detectors
				/*
				 * String signature = itm.getSignature(); if
				 * (Values.SIG_JAVA_UTIL_STRINGBUFFER.equals(signature) ||
				 * Values.SIG_JAVA_UTIL_STRINGBUILDER.equals(signature)) { return null; }
				 */
				JavaClass cls = itm.getJavaClass();
				if (Subtypes2.instanceOf(cls, "java.lang.Appendable"))
					return itm;
			} catch (ClassNotFoundException e) {
				return null;
			}

		}

		return null;
	}
}
