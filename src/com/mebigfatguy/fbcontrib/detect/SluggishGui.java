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

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for methods that implement awt or swing listeners and perform time
 * consuming operations. Doing these operations in the gui thread will cause the
 * interface to appear sluggish and non-responsive to the user. It is better to
 * use a separate thread to do the time consuming work so that the user has a
 * better experience.
 */
public class SluggishGui extends BytecodeScanningDetector {

    private static final Set<String> expensiveCalls = UnmodifiableSet.create(
        "java/io/BufferedOutputStream:<init>",
        "java/io/DataOutputStream:<init>",
        "java/io/FileOutputStream:<init>",
        "java/io/ObjectOutputStream:<init>",
        "java/io/PipedOutputStream:<init>",
        "java/io/BufferedInputStream:<init>",
        "java/io/DataInputStream:<init>",
        "java/io/FileInputStream:<init>",
        "java/io/ObjectInputStream:<init>",
        "java/io/PipedInputStream:<init>",
        "java/io/BufferedWriter:<init>",
        "java/io/FileWriter:<init>",
        "java/io/OutpuStreamWriter:<init>",
        "java/io/BufferedReader:<init>",
        "java/io/FileReader:<init>",
        "java/io/InputStreamReader:<init>",
        "java/io/RandomAccessFile:<init>",
        "java/lang/Class:getResourceAsStream",
        "java/lang/ClassLoader:getResourceAsStream",
        "java/lang/ClassLoader:loadClass",
        "java/sql/DriverManager:getConnection",
        "java/sql/Connection:createStatement",
        "java/sql/Connection:prepareStatement",
        "java/sql/Connection:prepareCall",
        "javax/sql/DataSource:getConnection",
        "javax/xml/parsers/DocumentBuilder:parse",
        "javax/xml/parsers/DocumentBuilder:parse",
        "javax/xml/parsers/SAXParser:parse",
        "javax/xml/transform/Transformer:transform"
    );

    private BugReporter bugReporter;
    private Set<String> expensiveThisCalls;
    private Set<JavaClass> guiInterfaces;
    private Map<Code, Method> listenerCode;
    private String methodName;
    private String methodSig;
    private boolean isListenerMethod = false;
    private boolean methodReported = false;

    /**
     * constructs a SG detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public SluggishGui(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * overrides the visitor to reset look for gui interfaces
     *
     * @param classContext
     *            the context object for the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            guiInterfaces = new HashSet<JavaClass>();
            JavaClass cls = classContext.getJavaClass();
            JavaClass[] infs = cls.getAllInterfaces();
            for (JavaClass inf : infs) {
                String name = inf.getClassName();
                if ((name.startsWith("java.awt.") || name.startsWith("javax.swing.")) && name.endsWith("Listener")) {
                    guiInterfaces.add(inf);
                }
            }

            if (guiInterfaces.size() > 0) {
                listenerCode = new LinkedHashMap<Code, Method>();
                expensiveThisCalls = new HashSet<String>();
                super.visitClassContext(classContext);
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        } finally {
            guiInterfaces = null;
            listenerCode = null;
            expensiveThisCalls = null;
        }
    }

    /**
     * overrides the visitor to visit all of the collected listener methods
     *
     * @param obj
     *            the context object of the currently parsed class
     */
    @Override
    public void visitAfter(JavaClass obj) {
        isListenerMethod = true;
        for (Code l : listenerCode.keySet()) {
            methodReported = false;
            super.visitCode(l);
        }
        super.visitAfter(obj);
    }

    /**
     * overrides the visitor collect method info
     *
     * @param obj
     *            the context object of the currently parsed method
     */
    @Override
    public void visitMethod(Method obj) {
        methodName = obj.getName();
        methodSig = obj.getSignature();
    }

    /**
     * overrides the visitor to segregate method into two, those that implement
     * listeners, and those that don't. The ones that don't are processed first.
     *
     * @param obj
     *            the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        for (JavaClass inf : guiInterfaces) {
            Method[] methods = inf.getMethods();
            for (Method m : methods) {
                if (m.getName().equals(methodName)) {
                    if (m.getSignature().equals(methodSig)) {
                        listenerCode.put(obj, this.getMethod());
                        return;
                    }
                }
            }
        }
        isListenerMethod = false;
        methodReported = false;
        super.visitCode(obj);
    }

    /**
     * overrides the visitor to look for the execution of expensive calls
     *
     * @param seen
     *            the currently parsed opcode
     */
    @Override
    public void sawOpcode(int seen) {
        if (methodReported)
            return;

        if ((seen == INVOKEINTERFACE) || (seen == INVOKEVIRTUAL) || (seen == INVOKESPECIAL) || (seen == INVOKESTATIC)) {
            String clsName = getClassConstantOperand();
            String mName = getNameConstantOperand();
            String methodInfo = clsName + ':' + mName;
            String thisMethodInfo = (clsName.equals(getClassName())) ? (mName + ':' + methodSig) : "0";

            if (expensiveCalls.contains(methodInfo) || expensiveThisCalls.contains(thisMethodInfo)) {
                if (isListenerMethod) {
                    bugReporter.reportBug(new BugInstance(this, BugType.SG_SLUGGISH_GUI.name(), NORMAL_PRIORITY).addClass(this)
                            .addMethod(this.getClassContext().getJavaClass(), listenerCode.get(this.getCode())));
                } else {
                    expensiveThisCalls.add(getMethodName() + ':' + getMethodSig());
                }
                methodReported = true;
            }
        }
    }
}
