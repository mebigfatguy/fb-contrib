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

import java.util.Set;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for various issues around input/output/streaming library use
 */
@CustomUserValue
public class IOIssues extends BytecodeScanningDetector {

    enum IOIUserValue {
        BUFFER, READER
    };

    private static final String ANY_PARMS = "(*)";
    private static Set<FQMethod> COPY_METHODS = UnmodifiableSet.create(
    //@formatter:off
            new FQMethod("java/nio/file/Files", "copy", ANY_PARMS),
            new FQMethod("org/apache/commons/io/IOUtils", "copy", ANY_PARMS),
            new FQMethod("org/apache/commons/io/IOUtils", "copyLarge", ANY_PARMS),
            new FQMethod("org/springframework/util/FileCopyUtils", "copy", ANY_PARMS),
            new FQMethod("org/springframework/util/FileCopyUtils", "copyToByteArray", ANY_PARMS),
            new FQMethod("com/google/common/io/Files", "copy", ANY_PARMS),
            new FQMethod("org/apache/poi/openxml4j/opc/StreamHelper", "copyStream", ANY_PARMS)
    //@formatter:on
    );

    private static final Set<String> BUFFERED_CLASSES = UnmodifiableSet.create(
    //@formatter:off
            "java.io.BufferedInputStream",
            "java.io.BufferedOutputStream",
            "java.io.BufferedReader",
            "java.io.BufferedWriter"
    //@formatter:on
    );

    private static JavaClass READER_CLASS;

    static {
        try {
            READER_CLASS = Repository.lookupClass("java.io.Reader");
        } catch (ClassNotFoundException cnfe) {
            READER_CLASS = null;
        }
    }

    private BugReporter bugReporter;
    private OpcodeStack stack;

    /**
     * constructs a IOI detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public IOIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to create and tear down the opcode stack
     *
     * @param clsContext
     *            the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext clsContext) {
        try {
            stack = new OpcodeStack();
            super.visitClassContext(clsContext);
        } finally {
            stack = null;
        }
    }

    /**
     * implements the visitor to reset the opcode stack
     *
     * @param obj
     *            the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {

        stack.resetForMethodEntry(this);
        super.visitCode(obj);
    }

    /**
     * implements the visitor to look for common api copy utilities to copy streams where the passed in Stream is Buffered. Since these libraries already handle
     * the buffering, you are just slowing them down by the extra copy. Also look for copies where the source is a Reader, as this is just wasteful. Can't wrap
     * my head around whether a Writer output is sometime valid, might be, so for now ignoring that.
     *
     * @param seen
     *            the currently parsed opcode
     */
    @Override
    public void sawOpcode(int seen) {
        IOIUserValue uvSawBuffer = null;

        try {
            switch (seen) {
                case INVOKESPECIAL:
                    uvSawBuffer = processInvokeSpecial();
                break;

                case INVOKESTATIC:
                    processInvokeStatic();
                break;

                default:
                break;
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        } finally {
            stack.sawOpcode(this, seen);
            if ((uvSawBuffer != null) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item itm = stack.getStackItem(0);
                itm.setUserValue(uvSawBuffer);
            }
        }
    }

    private IOIUserValue processInvokeSpecial() throws ClassNotFoundException {
        String methodName = getNameConstantOperand();

        if (Values.CONSTRUCTOR.equals(methodName)) {
            String clsName = getDottedClassConstantOperand();
            if (BUFFERED_CLASSES.contains(clsName)) {
                return IOIUserValue.BUFFER;
            } else {
                JavaClass cls = Repository.lookupClass(clsName);
                if (cls.instanceOf(READER_CLASS)) {
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
            Type[] argTypes = Type.getArgumentTypes(signature);
            if (stack.getStackDepth() >= argTypes.length) {
                for (int i = 0; i < argTypes.length; i++) {
                    OpcodeStack.Item itm = stack.getStackItem(i);
                    IOIUserValue uv = (IOIUserValue) itm.getUserValue();
                    if (uv != null) {
                        switch (uv) {
                            case BUFFER:
                                bugReporter.reportBug(new BugInstance(this, BugType.IOI_DOUBLE_BUFFER_COPY.name(), NORMAL_PRIORITY).addClass(this)
                                        .addMethod(this).addSourceLine(this));
                            break;

                            case READER:
                                bugReporter.reportBug(new BugInstance(this, BugType.IOI_COPY_WITH_READER.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                                        .addSourceLine(this));
                        }
                        break;
                    }
                }
            }
        }
    }

}
