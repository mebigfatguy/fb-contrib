/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2018 Dave Brosius
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
import java.util.Set;

import javax.annotation.Nullable;
import org.apache.bcel.Const;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.SourceLineAnnotation;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for various issues around input/output/streaming library use
 */
@CustomUserValue
public class IOIssues extends BytecodeScanningDetector {

	enum IOIUserValue {
		BUFFER, READER, ZLIB
	};

	private static final String ANY_PARMS = "(*)";
	private static Set<FQMethod> COPY_METHODS = UnmodifiableSet.create(
			// @formatter:off
			new FQMethod("java/nio/file/Files", "copy", ANY_PARMS),
			new FQMethod("org/apache/commons/io/IOUtils", "copy", ANY_PARMS),
			new FQMethod("org/apache/commons/io/IOUtils", "copyLarge", ANY_PARMS),
			new FQMethod("org/springframework/util/FileCopyUtils", "copy", ANY_PARMS),
			new FQMethod("org/springframework/util/FileCopyUtils", "copyToByteArray", ANY_PARMS),
			new FQMethod("com/google/common/io/Files", "copy", ANY_PARMS),
			new FQMethod("org/apache/poi/openxml4j/opc/StreamHelper", "copyStream", ANY_PARMS)
	// @formatter:on
	);

	private static final Set<String> BUFFERED_CLASSES = UnmodifiableSet.create(
			// @formatter:off
			"java.io.BufferedInputStream", "java.io.BufferedOutputStream", "java.io.BufferedReader",
			"java.io.BufferedWriter"
	// @formatter:on
	);

	private static final Set<String> ZLIB_CLASSES = UnmodifiableSet.create(
			// @formatter:off
			"java.util.zip.Inflater", "java.util.zip.Deflater"
	// @formatter:on
	);

	private BugReporter bugReporter;
	private JavaClass readerClass;
	private Map<Integer, SourceLineAnnotation> unendedZLIBs;
	private OpcodeStack stack;
	private int clsVersion;

	/**
	 * constructs a IOI detector given the reporter to report bugs on
	 *
	 * @param bugReporter the sync of bug reports
	 */
	public IOIssues(BugReporter bugReporter) {
		this.bugReporter = bugReporter;

		try {
			readerClass = Repository.lookupClass("java.io.Reader");
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
		}

	}

	/**
	 * implements the visitor to create and tear down the opcode stack
	 *
	 * @param clsContext the context object of the currently parsed class
	 */
	@Override
	public void visitClassContext(ClassContext clsContext) {

		try {
			stack = new OpcodeStack();
			clsVersion = clsContext.getJavaClass().getMajor();
			unendedZLIBs = new HashMap<>();
			super.visitClassContext(clsContext);
		} finally {
			unendedZLIBs = null;
			stack = null;
		}
	}

	/**
	 * implements the visitor to reset the opcode stack
	 *
	 * @param obj the currently parsed code block
	 */
	@Override
	public void visitCode(Code obj) {

		stack.resetForMethodEntry(this);
		unendedZLIBs.clear();
		super.visitCode(obj);

		for (SourceLineAnnotation sa : unendedZLIBs.values()) {
			bugReporter.reportBug(new BugInstance(this, BugType.IOI_UNENDED_ZLIB_OBJECT.name(), NORMAL_PRIORITY)
					.addClass(this).addMethod(this).addSourceLine(sa));
		}
	}

	/**
	 * implements the visitor to look for common api copy utilities to copy streams
	 * where the passed in Stream is Buffered. Since these libraries already handle
	 * the buffering, you are just slowing them down by the extra copy. Also look
	 * for copies where the source is a Reader, as this is just wasteful. Can't wrap
	 * my head around whether a Writer output is sometime valid, might be, so for
	 * now ignoring that. Also reports uses of java.io.FileInputStream and
	 * java.io.FileOutputStream on {@code java >= 1.7} as those classes have
	 * finalize methods that junk up gc.
	 * 
	 * Also look for uses of Inflater and Deflater that don't call end()
	 *
	 * @param seen the currently parsed opcode
	 */
	@Override
	public void sawOpcode(int seen) {
		IOIUserValue uvSawType = null;

		try {
			switch (seen) {
			case INVOKESPECIAL:
				uvSawType = processInvokeSpecial();
				break;

			case INVOKESTATIC:
				processInvokeStatic();
				break;

			case INVOKEVIRTUAL:
				processInvokeVirtual();
				break;

			case ASTORE:
			case ASTORE_0:
			case ASTORE_1:
			case ASTORE_2:
			case ASTORE_3:
				processAStore(seen);
				break;

			default:
				break;
			}
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
		} finally {
			stack.sawOpcode(this, seen);
			if ((uvSawType != null) && (stack.getStackDepth() > 0)) {
				OpcodeStack.Item itm = stack.getStackItem(0);
				itm.setUserValue(uvSawType);
			}
		}
	}

	@Nullable
	private IOIUserValue processInvokeSpecial() throws ClassNotFoundException {
		String methodName = getNameConstantOperand();

		if (Values.CONSTRUCTOR.equals(methodName)) {
			String clsName = getDottedClassConstantOperand();
			if (BUFFERED_CLASSES.contains(clsName)) {
				return IOIUserValue.BUFFER;
			} else if (ZLIB_CLASSES.contains(clsName)) {
				return IOIUserValue.ZLIB;
			} else if ("java.io.FileInputStream".equals(clsName) || "java.io.FileOutputStream".equals(clsName)) {
                if (clsVersion >= Const.MAJOR_1_7) {
					if (!getMethod().isStatic()) {
						String sig = getSigConstantOperand();
						int numParms = SignatureUtils.getNumParameters(sig);
						if (stack.getStackDepth() > numParms) {
							OpcodeStack.Item itm = stack.getStackItem(numParms);
							if (itm.getRegisterNumber() == 0) {
								return null;
							}
						}
					}
					bugReporter.reportBug(
							new BugInstance(this, BugType.IOI_USE_OF_FILE_STREAM_CONSTRUCTORS.name(), NORMAL_PRIORITY)
									.addClass(this).addMethod(this).addSourceLine(this));
				}
			} else if (readerClass != null) {
				JavaClass cls = Repository.lookupClass(clsName);
				if (cls.instanceOf(readerClass)) {
					return IOIUserValue.READER;
				}
			}
		}

		return null;
	}

	private void processInvokeStatic() {
		String clsName = getClassConstantOperand();
		String methodName = getNameConstantOperand();
		FQMethod m = new FQMethod(clsName, methodName, ANY_PARMS);
		if (COPY_METHODS.contains(m)) {
			String signature = getSigConstantOperand();
			int numArguments = SignatureUtils.getNumParameters(signature);
			if (stack.getStackDepth() >= numArguments) {
				for (int i = 0; i < numArguments; i++) {
					OpcodeStack.Item itm = stack.getStackItem(i);
					IOIUserValue uv = (IOIUserValue) itm.getUserValue();
					if (uv != null) {
						switch (uv) {
						case BUFFER:
							bugReporter.reportBug(
									new BugInstance(this, BugType.IOI_DOUBLE_BUFFER_COPY.name(), NORMAL_PRIORITY)
											.addClass(this).addMethod(this).addSourceLine(this));
							break;

						case READER:
							bugReporter.reportBug(
									new BugInstance(this, BugType.IOI_COPY_WITH_READER.name(), NORMAL_PRIORITY)
											.addClass(this).addMethod(this).addSourceLine(this));
							break;

						default:
							break;
						}
					}
				}
			}
		}
	}

	private void processInvokeVirtual() {
		String methodName = getNameConstantOperand();
		if ("end".equals(methodName)) {
			String clsName = getDottedClassConstantOperand();
			if (ZLIB_CLASSES.contains(clsName)) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item itm = stack.getStackItem(0);
					int reg = itm.getRegisterNumber();
					if (reg >= 0) {
						unendedZLIBs.remove(Integer.valueOf(reg));
					}
				}
			}
		}
	}

	private void processAStore(int seen) {
		if (stack.getStackDepth() > 0) {
			OpcodeStack.Item itm = stack.getStackItem(0);
			if ((IOIUserValue) itm.getUserValue() == IOIUserValue.ZLIB) {
				unendedZLIBs.put(Integer.valueOf(RegisterUtils.getAStoreReg(this, seen)),
						SourceLineAnnotation.fromVisitedInstruction(this, getPC()));
			}
		}
	}

}
