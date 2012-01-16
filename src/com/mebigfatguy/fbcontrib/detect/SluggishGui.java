/*
 * fb-contrib - Auxiliary detectors for Java programs
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

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for methods that implement awt or swing listeners and perform time 
 * consuming operations. Doing these operations in the gui thread will cause the
 * interface to appear sluggish and non-responsive to the user. It is better to 
 * use a separate thread to do the time consuming work so that the user
 * has a better experience.
 */
public class SluggishGui extends BytecodeScanningDetector
{
	
	private static final Set<String> expensiveCalls = new HashSet<String>();
	static {
		expensiveCalls.add("java/io/BufferedOutputStream:<init>");
		expensiveCalls.add("java/io/DataOutputStream:<init>");
		expensiveCalls.add("java/io/FileOutputStream:<init>");
		expensiveCalls.add("java/io/ObjectOutputStream:<init>");
		expensiveCalls.add("java/io/PipedOutputStream:<init>");
		expensiveCalls.add("java/io/BufferedInputStream:<init>");
		expensiveCalls.add("java/io/DataInputStream:<init>");
		expensiveCalls.add("java/io/FileInputStream:<init>");
		expensiveCalls.add("java/io/ObjectInputStream:<init>");
		expensiveCalls.add("java/io/PipedInputStream:<init>");
		expensiveCalls.add("java/io/BufferedWriter:<init>");
		expensiveCalls.add("java/io/FileWriter:<init>");
		expensiveCalls.add("java/io/OutpuStreamWriter:<init>");
		expensiveCalls.add("java/io/BufferedReader:<init>");
		expensiveCalls.add("java/io/FileReader:<init>");
		expensiveCalls.add("java/io/InputStreamReader:<init>");
		expensiveCalls.add("java/io/RandomAccessFile:<init>");
		expensiveCalls.add("java/lang/Class:getResourceAsStream");
		expensiveCalls.add("java/lang/ClassLoader:getResourceAsStream");
		expensiveCalls.add("java/lang/ClassLoader:loadClass");
		expensiveCalls.add("java/sql/DriverManager:getConnection");
		expensiveCalls.add("java/sql/Connection:createStatement");
		expensiveCalls.add("java/sql/Connection:prepareStatement");
		expensiveCalls.add("java/sql/Connection:prepareCall");
		expensiveCalls.add("javax/sql/DataSource:getConnection");
		expensiveCalls.add("javax/xml/parsers/DocumentBuilder:parse");
		expensiveCalls.add("javax/xml/parsers/DocumentBuilder:parse");
		expensiveCalls.add("javax/xml/parsers/SAXParser:parse");
		expensiveCalls.add("javax/xml/transform/Transformer:transform");
	}
	
	private BugReporter bugReporter;
	private Set<String> expensiveThisCalls;
	private Set<JavaClass> guiInterfaces;
	private Map<Code,Method> listenerCode;
	private String methodName;
	private String methodSig;
	private boolean isListenerMethod = false;
	private boolean methodReported = false;
	
    /**
     * constructs a SG detector given the reporter to report bugs on

     * @param bugReporter the sync of bug reports
     */
	public SluggishGui(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}
	
	/**
	 * overrides the visitor to reset look for gui interfaces
	 * 
	 * @param classContext the context object for the currently parsed class
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			guiInterfaces = new HashSet<JavaClass>();
			JavaClass cls = classContext.getJavaClass();
			JavaClass[] infs = cls.getAllInterfaces();
			for (JavaClass inf : infs) {
				String name = inf.getClassName();
				if ((name.startsWith("java.awt.")
				||  name.startsWith("javax.swing."))
				&&  name.endsWith("Listener")) {
					guiInterfaces.add(inf);
				}
			}
			
			if (guiInterfaces.size() > 0) {
				listenerCode = new LinkedHashMap<Code,Method>();
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
	 * @param obj the context object of the currently parsed class
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
	 * @param obj the context object of the currently parsed method
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
	 * @param obj the context object of the currently parsed code block
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
	 * @param seen the currently parsed opcode
	 */
	@Override
	public void sawOpcode(int seen) {
		if (methodReported)
			return;
		
		if ((seen == INVOKEINTERFACE)
		||  (seen == INVOKEVIRTUAL)
		||  (seen == INVOKESPECIAL)
		||  (seen == INVOKESTATIC)) {
			String clsName = getClassConstantOperand();
			String mName = getNameConstantOperand();
			String methodInfo = clsName + ":" + mName;
			String thisMethodInfo = (clsName.equals(getClassName())) ? (mName + ":" + methodSig) : "0";
				
			if (expensiveCalls.contains(methodInfo) || expensiveThisCalls.contains(thisMethodInfo)) {
				if (isListenerMethod) {
					bugReporter.reportBug(new BugInstance(this, "SG_SLUGGISH_GUI", NORMAL_PRIORITY)
												.addClass(this)
												.addMethod(this.getClassContext().getJavaClass(), listenerCode.get(this.getCode())));
				} else {
					expensiveThisCalls.add(getMethodName() + ":" + getMethodSig());
				}
				methodReported = true;
			}
		}
	}
}
