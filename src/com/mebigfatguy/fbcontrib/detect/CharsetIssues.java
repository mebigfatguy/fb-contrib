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

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for issues related to manually specified charsets by using string literals.
 */
public class CharsetIssues extends BytecodeScanningDetector {
	
	private static final String STRING_SIG = "Ljava/lang/String;";
	private static final String CHARSET_SIG = "Ljava/nio/charset/Charset;";
	
	private static Map<String, Pair> REPLACEABLE_ENCODING_METHODS = new HashMap<String, Pair>();
	private static Map<String, Integer> UNREPLACEABLE_ENCODING_METHODS = new HashMap<String, Integer>();
	private static Set<String> STANDARD_JDK7_ENCODINGS = new HashSet<String>();
	
	static {			   //			  0         10        20        30        40        50        60        70
						   //For counting 012345678901234567890123456789012345678901234567890123456789012345678901234567890
		REPLACEABLE_ENCODING_METHODS.put("java/io/InputStreamReader.<init>(Ljava/io/InputStream;Ljava/lang/String;)V", new Pair(0, 54));
		REPLACEABLE_ENCODING_METHODS.put("java/io/OutputStreamWriter.<init>(Ljava/io/OutputStream;Ljava/lang/String;)V", new Pair(0, 56));
		REPLACEABLE_ENCODING_METHODS.put("java/lang/String.<init>([BLjava/lang/String;)V", new Pair(0, 26));		//I'm not sure about this
		REPLACEABLE_ENCODING_METHODS.put("java/lang/String.<init>([BIILjava/lang/String;)V", new Pair(0, 28));		// or this.  When is this invoked?
		REPLACEABLE_ENCODING_METHODS.put("java/lang/String.getBytes(Ljava/lang/String;)[B", new Pair(0, 56));
		REPLACEABLE_ENCODING_METHODS.put("java/util/Formatter.<init>(Ljava/io/File;Ljava/lang/String;Ljava/util/Locale;)V", new Pair(1, 41));
		
		
		UNREPLACEABLE_ENCODING_METHODS.put("java/net/URLEncoder.encode(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", Values.ZERO);
		UNREPLACEABLE_ENCODING_METHODS.put("java/net/URLDecoder.decode(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", Values.ZERO);
		UNREPLACEABLE_ENCODING_METHODS.put("java/io/ByteArrayOutputStream.toString(Ljava/lang/String;)V", Values.ZERO);
		UNREPLACEABLE_ENCODING_METHODS.put("java/io/PrintStream.<init>(Ljava/lang/String;Ljava/lang/String;)V", Values.ZERO);
		UNREPLACEABLE_ENCODING_METHODS.put("java/io/PrintStream.<init>(Ljava/io/File;Ljava/lang/String;)V", Values.ZERO);
		UNREPLACEABLE_ENCODING_METHODS.put("java/io/PrintStream.<init>(Ljava/io/OutputStream;BLjava/lang/String;)V", Values.ZERO);
		UNREPLACEABLE_ENCODING_METHODS.put("java/io/PrintStream.toCharset(Ljava/lang/String;)Ljava/nio/charset/Charset;", Values.ZERO);
		UNREPLACEABLE_ENCODING_METHODS.put("java/io/PrintWriter.<init>(Ljava/lang/String;Ljava/lang/String;)V", Values.ZERO);
		UNREPLACEABLE_ENCODING_METHODS.put("java/io/PrintWriter.<init>(Ljava/io/File;Ljava/lang/String;)V", Values.ZERO);
		UNREPLACEABLE_ENCODING_METHODS.put("java/io/PrintWriter.toCharset(Ljava/lang/String;)Ljava/nio/charset/Charset;", Values.ZERO);
		UNREPLACEABLE_ENCODING_METHODS.put("java/lang/StringCoding.decode(Ljava/lang/String;[BII)[C", Values.THREE);
		UNREPLACEABLE_ENCODING_METHODS.put("java/lang/StringCoding.encode(Ljava/lang/String;[CII)[B", Values.THREE);
		UNREPLACEABLE_ENCODING_METHODS.put("java/util/Formatter.<init>(Ljava/io/File;Ljava/lang/String;)V", Values.ZERO);
		UNREPLACEABLE_ENCODING_METHODS.put("java/util/Formatter.<init>(Ljava/io/OutputStream;Ljava/lang/String;Ljava/util/Locale;)V", Values.ONE);
		UNREPLACEABLE_ENCODING_METHODS.put("java/util/Formatter.<init>(Ljava/io/OutputStream;Ljava/lang/String;)V", Values.ZERO);
		UNREPLACEABLE_ENCODING_METHODS.put("java/util/Formatter.<init>(Ljava/lang/String;Ljava/lang/String;Ljava/util/Locale;)V", Values.ONE);
		UNREPLACEABLE_ENCODING_METHODS.put("java/util/Formatter.<init>(Ljava/lang/String;Ljava/lang/String;)V", Values.ZERO);
		UNREPLACEABLE_ENCODING_METHODS.put("java/util/Formatter.toCharset(Ljava/lang/String;)V", Values.ZERO);
		UNREPLACEABLE_ENCODING_METHODS.put("java/util/logging/Handler.setEncoding(Ljava/lang/String;)V", Values.ZERO);
		UNREPLACEABLE_ENCODING_METHODS.put("java/util/logging/StreamHandler.setEncoding(Ljava/lang/String;)V", Values.ZERO);
		UNREPLACEABLE_ENCODING_METHODS.put("java/util/Scanner.<init>(Ljava/io/File;Ljava/lang/String;)V", Values.ZERO);
		UNREPLACEABLE_ENCODING_METHODS.put("java/util/Scanner.<init>(Ljava/io/InputStream;Ljava/lang/String;)V", Values.ZERO);
		UNREPLACEABLE_ENCODING_METHODS.put("java/util/Scanner.<init>(Ljava/nio/file/Path;Ljava/lang/String;)V", Values.ZERO);
		UNREPLACEABLE_ENCODING_METHODS.put("java/util/Scanner.<init>(Ljava/nio/channels/ReadableByteChannel;Ljava/lang/String;)V", Values.ZERO);
		UNREPLACEABLE_ENCODING_METHODS.put("java/lang/StringCoding.decode(Ljava/lang/String;[BII)[C", Values.THREE);
		UNREPLACEABLE_ENCODING_METHODS.put("javax/servlet/ServletResponse.setCharacterEncoding(Ljava/lang/String;)V", Values.ZERO);
		
		
		STANDARD_JDK7_ENCODINGS.add("US-ASCII");
		STANDARD_JDK7_ENCODINGS.add("ISO-8859-1");
		STANDARD_JDK7_ENCODINGS.add("UTF-8");
		STANDARD_JDK7_ENCODINGS.add("UTF-16BE");
		STANDARD_JDK7_ENCODINGS.add("UTF-16LE");
		STANDARD_JDK7_ENCODINGS.add("UTF-16");
	}
	
	private final BugReporter bugReporter;
	private OpcodeStack stack;
	private int classVersion;
	
	/**
	 * constructs a CSI detector given the reporter to report bugs on
	 * @param bugReporter the sync of bug reports
	 */
	public CharsetIssues(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}
	
	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			classVersion = classContext.getJavaClass().getMajor();
			if (classVersion >= Constants.MAJOR_1_4) {
				stack = new OpcodeStack();
				super.visitClassContext(classContext);
			}
		} finally {
			stack = null;
		}
	}
	
	@Override
	public void visitMethod(Method obj) {
		stack.resetForMethodEntry(this);
	}
	
	private String replaceStringSigWithCharsetString(String sig, int startIndex) {
		StringBuilder sb = new StringBuilder(sig);
		sb.replace(startIndex, startIndex + STRING_SIG.length(), CHARSET_SIG);
		return sb.toString();
	}
	
	@Override
	public void sawOpcode(int seen) {
		try {
			stack.precomputation(this);
			
			switch (seen) {
				case INVOKESPECIAL:
				case INVOKESTATIC:
				case INVOKEINTERFACE:
				case INVOKEVIRTUAL:
					String encoding = null;
				String className = getClassConstantOperand();
				String methodName = getNameConstantOperand();
				String methodSig = getSigConstantOperand();
				String methodInfo = className + "." + methodName + methodSig;
					Pair offsetInfo = REPLACEABLE_ENCODING_METHODS.get(methodInfo);
					if (offsetInfo != null) {
						int offset = offsetInfo.stackDepth;
						if (stack.getStackDepth() > offset) {
							OpcodeStack.Item item = stack.getStackItem(offset);
							encoding = (String) item.getConstant();
							
							if (STANDARD_JDK7_ENCODINGS.contains(encoding) && (classVersion >= Constants.MAJOR_1_7)) {
								// the counts put in the Pair are indexed from the beginning of
								String changedMethodSig = replaceStringSigWithCharsetString(
										methodInfo, offsetInfo.indexOfStringSig).substring(className.length() + methodName.length()+ 1);
								bugReporter.reportBug(new BugInstance(this, "CSI_CHAR_SET_ISSUES_USE_STANDARD_CHARSET", NORMAL_PRIORITY)
											.addClass(this)
											.addMethod(this)
											.addSourceLine(this)
											.addCalledMethod(this)
											.addCalledMethod(className, methodName, changedMethodSig, seen == INVOKESTATIC));
							}
						}
					} else {
						Integer offsetValue = UNREPLACEABLE_ENCODING_METHODS.get(methodInfo);
						if (offsetValue != null) {
							int offset = offsetValue.intValue();
							if (stack.getStackDepth() > offset) {
								OpcodeStack.Item item = stack.getStackItem(offset);
								encoding = (String) item.getConstant();
								if (STANDARD_JDK7_ENCODINGS.contains(encoding) && (classVersion >= Constants.MAJOR_1_7)) {
									bugReporter.reportBug(new BugInstance(this, "CSI_CHAR_SET_ISSUES_USE_STANDARD_CHARSET_NAME", NORMAL_PRIORITY)
												.addClass(this)
												.addMethod(this)
												.addSourceLine(this)
												.addCalledMethod(this));
								}
							}
						}
					}
					
					if (encoding != null) {
						try {
							Charset.forName(encoding);
						} catch (IllegalArgumentException e) {  //encompasses both IllegalCharsetNameException and UnsupportedCharsetException
							bugReporter.reportBug(new BugInstance(this, "CSI_CHAR_SET_ISSUES_UNNNOWN_ENCODING", NORMAL_PRIORITY)
										.addClass(this)
										.addMethod(this)
										.addSourceLine(this)
										.addCalledMethod(this)
										.addString(encoding));
						}
					}
				break;
			}
		} finally {
			stack.sawOpcode(this, seen);
		}
	}

	private static class Pair{
		public final int stackDepth;
		public final int indexOfStringSig; //to replace
		public Pair(int stackDepth, int indexOfStringSig) {
			this.stackDepth = stackDepth;
			this.indexOfStringSig = indexOfStringSig;
		}	
	}
}
