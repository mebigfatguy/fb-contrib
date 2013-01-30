/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2013 Dave Brosius
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
import java.util.Set;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantClass;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.ConstantUtf8;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

public class LoggerOddities extends BytecodeScanningDetector {
	private static JavaClass THROWABLE_CLASS;
	private static Set<String> loggerMethods;

	static {
		try {
			THROWABLE_CLASS = Repository.lookupClass("java/lang/Throwable");

			loggerMethods = new HashSet<String>();
			loggerMethods.add("trace");
			loggerMethods.add("debug");
			loggerMethods.add("info");
			loggerMethods.add("warn");
			loggerMethods.add("error");
			loggerMethods.add("fatal");
		} catch (ClassNotFoundException cnfe) {
			THROWABLE_CLASS = null;
		}
	}
	private final BugReporter bugReporter;
	private OpcodeStack stack;
	private String clsName;

    /**
     * constructs a LO detector given the reporter to report bugs on.

     * @param bugReporter the sync of bug reports
     */
	public LoggerOddities(final BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}


	/**
	 * implements the visitor to discover what the class name is if it is a normal class,
	 * or the owning class, if the class is an anonymous class.
	 *
	 * @param classContext the context object of the currently parsed class
	 */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            clsName = classContext.getJavaClass().getClassName();
            int subclassIndex = clsName.indexOf('$');
            while (subclassIndex >= 0) {
                String simpleName = clsName.substring(subclassIndex+1);
                try {
                    Integer.parseInt(simpleName);
                    clsName = clsName.substring(0, subclassIndex);
                    subclassIndex = clsName.indexOf('$');
                } catch (NumberFormatException nfe) {
                    subclassIndex = -1;
                }
            }
            clsName = clsName.replace('.', '/');
            super.visitClassContext(classContext);
        } finally {
            stack = null;
        }
    }

    /**
     * implements the visitor to reset the stack
     *
     * @param obj the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        Method m = getMethod();
        if ("<init>".equals(m.getName())) {
        	Type[] types = Type.getArgumentTypes(m.getSignature());
        	for (Type t : types)
        	{
        		String parmSig = t.getSignature();
        		if ("Lorg/slf4j/Logger;".equals(parmSig)
        		||  "Lorg/apache/log4j/Logger;".equals(parmSig)
        		||  "Lorg/apache/commons/logging/Log;".equals(parmSig)) {
                    bugReporter.reportBug(new BugInstance(this, "LO_SUSPECT_LOG_PARAMETER", NORMAL_PRIORITY)
                    .addClass(this)
                    .addMethod(this)
                    .addSourceLine(this));
        		}
        	}
        }
        super.visitCode(obj);
    }

    /**
     * implements the visitor to look for calls to Logger.getLogger with the wrong class name
     *
     * @param seen the opcode of the currently parsed instruction
     */
    @Override
    public void sawOpcode(int seen) {
        String ldcClassName = null;
        int exMessageReg = -1;

        try {
            if ((seen == LDC) || (seen == LDC_W)) {
                Constant c = getConstantRefOperand();
                if (c instanceof ConstantClass) {
                    ConstantPool pool = getConstantPool();
                    ldcClassName = ((ConstantUtf8)pool.getConstant(((ConstantClass) c).getNameIndex())).getBytes();
                }
            } else if (seen == INVOKESTATIC) {
                String callingClsName = getClassConstantOperand();
                String mthName = getNameConstantOperand();

                String loggingClassName = null;

                if ("org/slf4j/LoggerFactory".equals(callingClsName)
                &&  "getLogger".equals(mthName)) {
                    String signature = getSigConstantOperand();

                    if ("(Ljava/lang/Class;)Lorg/slf4j/Logger;".equals(signature)) {
                    	if (stack.getStackDepth() > 0) {
	                        OpcodeStack.Item item = stack.getStackItem(0);
	                        loggingClassName = (String)item.getUserValue();
	                    }
                    } else if ("(Ljava/lang/String;)Lorg/slf4j/Logger;".equals(signature)) {
                    	if (stack.getStackDepth() > 0) {
	                        OpcodeStack.Item item = stack.getStackItem(0);
	                        loggingClassName = (String)item.getConstant();
	                        if (loggingClassName != null) {
								loggingClassName = loggingClassName.replace('.', '/');
							}
	                    }
                    }
                } else if ("org/apache/log4j/Logger".equals(callingClsName)
                       &&  "getLogger".equals(mthName)) {
                    String signature = getSigConstantOperand();

                    if ("(Ljava/lang/Class;)Lorg/apache/log4j/Logger;".equals(signature)) {
                    	if (stack.getStackDepth() > 0) {
	                        OpcodeStack.Item item = stack.getStackItem(0);
	                        loggingClassName = (String)item.getUserValue();
	                    }
                    } else if ("(Ljava/lang/String;)Lorg/apache/log4j/Logger;".equals(signature)) {
                    	if (stack.getStackDepth() > 0) {
	                        OpcodeStack.Item item = stack.getStackItem(0);
	                        loggingClassName = (String)item.getConstant();
	                        if (loggingClassName != null) {
								loggingClassName = loggingClassName.replace('.', '/');
							}
	                    }
                    } else if ("(Ljava/lang/String;Lorg/apache/log4j/spi/LoggerFactory;)Lorg/apache/log4j/Logger;".equals(signature)) {
                    	if (stack.getStackDepth() > 1) {
	                        OpcodeStack.Item item = stack.getStackItem(1);
	                        loggingClassName = (String)item.getConstant();
	                        if (loggingClassName != null) {
								loggingClassName = loggingClassName.replace('.', '/');
							}
	                    }
                    }
                } else if ("org/apache/commons/logging/LogFactory".equals(callingClsName)
                	&& "getLog".equals(mthName)) {
            		String signature = getSigConstantOperand();

                    if ("(Ljava/lang/Class;)Lorg/apache/commons/logging/Log;".equals(signature)) {
                    	if (stack.getStackDepth() > 0) {
	                        OpcodeStack.Item item = stack.getStackItem(0);
	                        loggingClassName = (String)item.getUserValue();
	                    }
                    } else if ("(Ljava/lang/String;)Lorg/apache/commons/logging/Log;".equals(signature)) {
                    	if (stack.getStackDepth() > 0) {
	                        OpcodeStack.Item item = stack.getStackItem(0);
	                        loggingClassName = (String)item.getConstant();
	                        if (loggingClassName != null) {
								loggingClassName = loggingClassName.replace('.', '/');
							}
	                    }
                    }
                }

                if (loggingClassName != null) {
                    if (stack.getStackDepth() > 0) {
                        if (!loggingClassName.equals(clsName)) {
                            boolean isPrefix = clsName.startsWith(loggingClassName);
                            boolean isAnonClassPrefix;
                            if (isPrefix) {
                                String anonClass = clsName.substring(loggingClassName.length());
                                isAnonClassPrefix = anonClass.matches("(\\$\\d+)+");
                            } else {
                                isAnonClassPrefix = false;
                            }

                            if (!isAnonClassPrefix) {
                                bugReporter.reportBug(new BugInstance(this, "LO_SUSPECT_LOG_CLASS", NORMAL_PRIORITY)
                                            .addClass(this)
                                            .addMethod(this)
                                            .addSourceLine(this));
                            }
                        }
                    }
                }
            } else if (((seen == INVOKEVIRTUAL) || (seen == INVOKEINTERFACE)) && (THROWABLE_CLASS != null)) {
                String mthName = getNameConstantOperand();
                if (mthName.equals("getMessage")) {
                	String callingClsName = getClassConstantOperand();
                	JavaClass cls = Repository.lookupClass(callingClsName);
                	if (cls.instanceOf(THROWABLE_CLASS)) {
                		if (stack.getStackDepth() > 0) {
                			OpcodeStack.Item exItem = stack.getStackItem(0);
                			exMessageReg = exItem.getRegisterNumber();
                		}
                	}
                } else if (loggerMethods.contains(mthName)) {
                	String callingClsName = getClassConstantOperand();
                	if (callingClsName.endsWith("Log") || (callingClsName.endsWith("Logger"))) {
                		String sig = getSigConstantOperand();
                		if ("(Ljava/lang/String;Ljava/lang/Throwable;)V".equals(sig) || "(Ljava/lang/Object;Ljava/lang/Throwable;)V".equals(sig)) {
                			if (stack.getStackDepth() >= 2) {
                				OpcodeStack.Item exItem = stack.getStackItem(0);
                				OpcodeStack.Item msgItem = stack.getStackItem(1);

                				Integer exReg = (Integer)msgItem.getUserValue();
                				if (exReg != null) {
                					if (exReg.intValue() ==  exItem.getRegisterNumber()) {
                						bugReporter.reportBug(new BugInstance(this, "LO_STUTTERED_MESSAGE", NORMAL_PRIORITY)
                															.addClass(this)
                															.addMethod(this)
                															.addSourceLine(this));
                					}
                				}
                			}
                		}
                	}
                }
            }
        } catch (ClassNotFoundException cnfe) {
        	bugReporter.reportMissingClass(cnfe);
        } finally {
			TernaryPatcher.pre(stack, seen);
			stack.sawOpcode(this, seen);
			TernaryPatcher.post(stack, seen);
            if (ldcClassName != null) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    item.setUserValue(ldcClassName);
                }
            }
            if (exMessageReg >= 0) {
            	if (stack.getStackDepth() > 0) {
            		OpcodeStack.Item item = stack.getStackItem(0);
            		item.setUserValue(Integer.valueOf(exMessageReg));
            	}
            }
        }
    }
}
