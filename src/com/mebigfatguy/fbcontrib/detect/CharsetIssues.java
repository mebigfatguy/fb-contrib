/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2016 Dave Brosius
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.PublicAPI;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
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

    private static final String CHARSET_SIG = "Ljava/nio/charset/Charset;";

    private static final Map<FQMethod, Integer> REPLACEABLE_ENCODING_METHODS;
    private static final Map<FQMethod, Integer> UNREPLACEABLE_ENCODING_METHODS;
    public static final Set<String> STANDARD_JDK7_ENCODINGS = UnmodifiableSet.create("US-ASCII", "ISO-8859-1", "UTF-8", "UTF-16BE", "UTF-16LE", "UTF-16");

    /*
     * The stack offset refers to the relative position of the Ljava/lang/String; of interest (i.e. the one that is the charset) For example, a stack offset of
     * 0 means the String charset was the last param, and a stack offset of 2 means it was the 3rd to last.
     *
     * Not coincidentally, the argument that needs to be replaced is the [(# of arguments) - offset]th one
     */
    static {
        Map<FQMethod, Integer> replaceable = new HashMap<FQMethod, Integer>(8);
        replaceable.put(new FQMethod("java/io/InputStreamReader", "<init>", "(Ljava/io/InputStream;Ljava/lang/String;)V"), Values.ZERO);
        replaceable.put(new FQMethod("java/io/OutputStreamWriter", "<init>", "(Ljava/io/OutputStream;Ljava/lang/String;)V"), Values.ZERO);
        replaceable.put(new FQMethod(Values.SLASHED_JAVA_LANG_STRING, "<init>", "([BLjava/lang/String;)V"), Values.ZERO);
        replaceable.put(new FQMethod(Values.SLASHED_JAVA_LANG_STRING, "<init>", "([BIILjava/lang/String;)V"), Values.ZERO);
        replaceable.put(new FQMethod(Values.SLASHED_JAVA_LANG_STRING, "getBytes", "(Ljava/lang/String;)[B"), Values.ZERO);
        replaceable.put(new FQMethod("java/util/Formatter", "<init>", "(Ljava/io/File;Ljava/lang/String;Ljava/util/Locale;)V"), Values.ONE);

        REPLACEABLE_ENCODING_METHODS = Collections.unmodifiableMap(replaceable);

        Map<FQMethod, Integer> unreplaceable = new HashMap<FQMethod, Integer>(32);
        unreplaceable.put(new FQMethod("java/net/URLEncoder", "encode", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"), Values.ZERO);
        unreplaceable.put(new FQMethod("java/net/URLDecoder", "decode", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"), Values.ZERO);
        unreplaceable.put(new FQMethod("java/io/ByteArrayOutputStream", "toString", "(Ljava/lang/String;)V"), Values.ZERO);
        unreplaceable.put(new FQMethod("java/io/PrintStream", "<init>", "(Ljava/lang/String;Ljava/lang/String;)V"), Values.ZERO);
        unreplaceable.put(new FQMethod("java/io/PrintStream", "<init>", "(Ljava/io/File;Ljava/lang/String;)V"), Values.ZERO);
        unreplaceable.put(new FQMethod("java/io/PrintStream", "<init>", "(Ljava/io/OutputStream;BLjava/lang/String;)V"), Values.ZERO);
        unreplaceable.put(new FQMethod("java/io/PrintStream", "toCharset", "(Ljava/lang/String;)Ljava/nio/charset/Charset;"), Values.ZERO);
        unreplaceable.put(new FQMethod("java/io/PrintWriter", "<init>", "(Ljava/lang/String;Ljava/lang/String;)V"), Values.ZERO);
        unreplaceable.put(new FQMethod("java/io/PrintWriter", "<init>", "(Ljava/io/File;Ljava/lang/String;)V"), Values.ZERO);
        unreplaceable.put(new FQMethod("java/io/PrintWriter", "toCharset", "(Ljava/lang/String;)Ljava/nio/charset/Charset;"), Values.ZERO);
        unreplaceable.put(new FQMethod("java/lang/StringCoding", "decode", "(Ljava/lang/String;[BII)[C"), Values.THREE);
        unreplaceable.put(new FQMethod("java/lang/StringCoding", "encode", "(Ljava/lang/String;[CII)[B"), Values.THREE);
        unreplaceable.put(new FQMethod("java/util/Formatter", "<init>", "(Ljava/io/File;Ljava/lang/String;)V"), Values.ZERO);
        unreplaceable.put(new FQMethod("java/util/Formatter", "<init>", "(Ljava/io/OutputStream;Ljava/lang/String;Ljava/util/Locale;)V"), Values.ONE);
        unreplaceable.put(new FQMethod("java/util/Formatter", "<init>", "(Ljava/io/OutputStream;Ljava/lang/String;)V"), Values.ZERO);
        unreplaceable.put(new FQMethod("java/util/Formatter", "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/util/Locale;)V"), Values.ONE);
        unreplaceable.put(new FQMethod("java/util/Formatter", "<init>", "(Ljava/lang/String;Ljava/lang/String;)V"), Values.ZERO);
        unreplaceable.put(new FQMethod("java/util/Formatter", "toCharset", "(Ljava/lang/String;)V"), Values.ZERO);
        unreplaceable.put(new FQMethod("java/util/logging/Handler", "setEncoding", "(Ljava/lang/String;)V"), Values.ZERO);
        unreplaceable.put(new FQMethod("java/util/logging/MemoryHandler", "setEncoding", "(Ljava/lang/String;)V"), Values.ZERO);
        unreplaceable.put(new FQMethod("java/util/logging/StreamHandler", "setEncoding", "(Ljava/lang/String;)V"), Values.ZERO);
        unreplaceable.put(new FQMethod("java/util/Scanner", "<init>", "(Ljava/io/File;Ljava/lang/String;)V"), Values.ZERO);
        unreplaceable.put(new FQMethod("java/util/Scanner", "<init>", "(Ljava/io/InputStream;Ljava/lang/String;)V"), Values.ZERO);
        unreplaceable.put(new FQMethod("java/util/Scanner", "<init>", "(Ljava/nio/file/Path;Ljava/lang/String;)V"), Values.ZERO);
        unreplaceable.put(new FQMethod("java/util/Scanner", "<init>", "(Ljava/nio/channels/ReadableByteChannel;Ljava/lang/String;)V"), Values.ZERO);
        unreplaceable.put(new FQMethod("java/lang/StringCoding", "decode", "(Ljava/lang/String;[BII)[C"), Values.THREE);
        unreplaceable.put(new FQMethod("java/lang/StringCoding", "encode", "(Ljava/lang/String;[CII)[B"), Values.THREE);
        unreplaceable.put(new FQMethod("javax/servlet/ServletResponse", "setCharacterEncoding", "(Ljava/lang/String;)V"), Values.ZERO);
        unreplaceable.put(new FQMethod("java/beans/XMLEncoder", "<init>", "(Ljava/io/OutputStream;Ljava/lang/String;ZI)V"), Values.TWO);
        unreplaceable.put(
                new FQMethod("java/nio/channels/Channels", "newReader", "(Ljava/nio/channels/ReadableByteChannel;Ljava/lang/String;)Ljava/io/Reader;"),
                Values.ZERO);
        unreplaceable.put(
                new FQMethod("java/nio/channels/Channels", "newWriter", "(Ljava/nio/channels/WritableByteChannel;Ljava/lang/String;)Ljava/io/Writer;"),
                Values.ZERO);

        UNREPLACEABLE_ENCODING_METHODS = Collections.unmodifiableMap(unreplaceable);
    }

    private final BugReporter bugReporter;
    private OpcodeStack stack;
    private int classVersion;

    /**
     * constructs a CSI detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public CharsetIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to make sure the class is at least 1.4, and if so continues, reseting the opcode stack
     *
     * @param classContext
     *            the currently parsed java class
     */
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

    /**
     * implements the visitor to reset the opcode stack for a new method
     *
     * @param obj
     *            the currently parsed method
     */
    @Override
    public void visitMethod(Method obj) {
        stack.resetForMethodEntry(this);
    }

    /**
     * implements the visitor to look for method calls that take a parameter that either represents a encoding via a string, or takes a Charset. If the method
     * can take both, and a string is presented for a standard charset available in jdk 7, and the code is compiled against 7, then report. It also looks for
     * mistakes, encodings that aren't recognized in the current jvm.
     *
     * @param seen
     *            the currently parsed opcode
     */
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
                    FQMethod methodInfo = new FQMethod(className, methodName, methodSig);
                    Integer stackOffset = REPLACEABLE_ENCODING_METHODS.get(methodInfo);
                    if (stackOffset != null) {
                        int offset = stackOffset.intValue();
                        if (stack.getStackDepth() > offset) {
                            OpcodeStack.Item item = stack.getStackItem(offset);
                            encoding = (String) item.getConstant();
                            if (encoding != null) {
                                encoding = encoding.toUpperCase(Locale.ENGLISH);
                                if ((classVersion >= Constants.MAJOR_1_7) && STANDARD_JDK7_ENCODINGS.contains(encoding)) {
                                    // the counts put in the Pair are indexed from
                                    // the beginning of
                                    String changedMethodSig = replaceNthArgWithCharsetString(methodSig, offset);
                                    bugReporter.reportBug(new BugInstance(this, BugType.CSI_CHAR_SET_ISSUES_USE_STANDARD_CHARSET.name(), NORMAL_PRIORITY)
                                            .addClass(this).addMethod(this).addSourceLine(this).addCalledMethod(this)
                                            .addCalledMethod(className, methodName, changedMethodSig, seen == INVOKESTATIC));
                                }
                            }
                        }
                    } else {
                        Integer offsetValue = UNREPLACEABLE_ENCODING_METHODS.get(methodInfo);
                        if (offsetValue != null) {
                            int offset = offsetValue.intValue();
                            if (stack.getStackDepth() > offset) {
                                OpcodeStack.Item item = stack.getStackItem(offset);
                                encoding = (String) item.getConstant();
                                if (encoding != null) {
                                    encoding = encoding.toUpperCase(Locale.ENGLISH);
                                    if ((classVersion >= Constants.MAJOR_1_7) && STANDARD_JDK7_ENCODINGS.contains(encoding)) {
                                        bugReporter
                                                .reportBug(new BugInstance(this, BugType.CSI_CHAR_SET_ISSUES_USE_STANDARD_CHARSET_NAME.name(), NORMAL_PRIORITY)
                                                        .addClass(this).addMethod(this).addSourceLine(this).addCalledMethod(this));
                                    }
                                }
                            }
                        }
                    }

                    if (encoding != null) {
                        try {
                            Charset.forName(encoding);
                        } catch (IllegalArgumentException e) { // encompasses both
                                                               // IllegalCharsetNameException
                                                               // and
                                                               // UnsupportedCharsetException
                            bugReporter.reportBug(new BugInstance(this, BugType.CSI_CHAR_SET_ISSUES_UNKNOWN_ENCODING.name(), NORMAL_PRIORITY).addClass(this)
                                    .addMethod(this).addSourceLine(this).addCalledMethod(this).addString(encoding));
                        }
                    }
                break;
                default:
                break;
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    /**
     * rebuilds a signature replacing a String argument at a specified spot, with a Charset parameter.
     *
     * @param sig
     *            the signature to replace
     * @param stackOffset
     *            the offset of the parameter to replace
     *
     * @return a new signature with a Charset parameter
     */
    private static String replaceNthArgWithCharsetString(String sig, int stackOffset) {
        Type[] arguments = Type.getArgumentTypes(sig);

        StringBuilder sb = new StringBuilder("(");
        int argumentIndexToReplace = (arguments.length - stackOffset) - 1;

        for (int i = 0; i < arguments.length; i++) {
            if (i == argumentIndexToReplace) {
                sb.append(CHARSET_SIG);
            } else {
                sb.append(arguments[i].getSignature());
            }
        }

        sb.append(sig.substring(sig.lastIndexOf(')'), sig.length()));
        return sb.toString();
    }

    /**
     * used by external tools, lists the method signature checked for, for unreplaceable encoding methods
     *
     * @return a map of these methods
     */
    @PublicAPI("Used by fb-contrib-eclipse-quickfixes to determine type of fix to apply")
    public static Map<String, Integer> getUnreplaceableCharsetEncodings() {
        Map<String, Integer> encodings = new HashMap<String, Integer>();
        for (Map.Entry<FQMethod, Integer> entry : UNREPLACEABLE_ENCODING_METHODS.entrySet()) {
            encodings.put(entry.getKey().toString(), entry.getValue());
        }
        return encodings;
    }

    /**
     * used by external tools, lists the method signature checked for, for replaceable encoding methods
     *
     * @return a map of these methods
     */
    @PublicAPI("Used by fb-contrib-eclipse-quickfixes to determine type of fix to apply")
    public static Map<String, Integer> getReplaceableCharsetEncodings() {
        Map<String, Integer> encodings = new HashMap<String, Integer>();
        for (Map.Entry<FQMethod, Integer> entry : REPLACEABLE_ENCODING_METHODS.entrySet()) {
            encodings.put(entry.getKey().toString(), entry.getValue());
        }
        return encodings;
    }
}
