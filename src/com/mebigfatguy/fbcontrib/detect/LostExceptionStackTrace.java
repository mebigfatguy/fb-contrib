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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.Constants;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.CodeException;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.LocalVariable;
import org.apache.bcel.classfile.LocalVariableTable;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for methods that catch exceptions, and rethrow another exception without encapsulating
 * the original exception within it. Doing this loses the stack history, and where the original
 * problem occurred. This makes finding and fixing errors difficult.
 */
@CustomUserValue
public class LostExceptionStackTrace extends BytecodeScanningDetector
{
    private static JavaClass errorClass;
    private static JavaClass throwableClass;
    private static JavaClass assertionClass;

	static {
		try {
            errorClass = Repository.lookupClass("java/lang/Error");
            throwableClass = Repository.lookupClass("java/lang/Throwable");
            assertionClass = Repository.lookupClass("java/lang/AssertionError");
		} catch (ClassNotFoundException cnfe) {
            errorClass = null;
            throwableClass = null;
		}
	}

	private final BugReporter bugReporter;
	private OpcodeStack stack;
	private CodeException[] exceptions;
	private Set<CatchInfo> catchInfos;
	private Map<Integer, Boolean> exReg;
	private boolean lastWasExitPoint = false;

    /**
     * constructs a LEST detector given the reporter to report bugs on

     * @param bugReporter the sync of bug reports
     */
	public LostExceptionStackTrace(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}

	/**
	 * implements the visitor to make sure the jdk is 1.4 or better
	 *
	 * @param classContext the context object of the currently parsed class
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			if (errorClass != null && throwableClass != null && !isPre14Class(classContext.getJavaClass())) {
				stack = new OpcodeStack();
				catchInfos = new HashSet<CatchInfo>();
				exReg = new HashMap<Integer, Boolean>();
				super.visitClassContext(classContext);
			}
		} finally {
			stack = null;
			catchInfos = null;
			exceptions = null;
			exReg = null;
		}
	}

    /**
	 * looks for methods that contain a catch block and an ATHROW opcode
	 *
	 * @param code the context object of the current code block
	 * @param method the context object of the current method
	 * @return if the class throws exceptions
	 */
	public boolean prescreen(Code code, Method method) {
		if (method.isSynthetic())
        {
            return false;
        }

		CodeException[] ce = code.getExceptionTable();
		if (ce == null || ce.length == 0)
        {
            return false;
        }

		BitSet bytecodeSet = getClassContext().getBytecodeSet(method);
		return bytecodeSet != null && bytecodeSet.get(Constants.ATHROW);
	}

	/**
	 * implements the visitor to filter out methods that don't throw exceptions
	 *
	 * @param obj the context object of the currently parsed code block
	 */
	@Override
	public void visitCode(Code obj) {
		if (prescreen(obj, getMethod())) {
			stack.resetForMethodEntry(this);
			catchInfos.clear();
			exceptions = collectExceptions(obj.getExceptionTable());
			exReg.clear();
			lastWasExitPoint = false;
			super.visitCode(obj);
		}
	}

	/**
	 * collects all the valid exception objects (ones where start and finish are before the target
	 *
	 * @param exs the exceptions from the class file
	 * @return the filtered exceptions
	 */
	public CodeException[] collectExceptions(CodeException[] exs) {
		List<CodeException> filteredEx = new ArrayList<CodeException>();
		for (CodeException ce : exs) {
			if (ce.getCatchType() != 0 && ce.getStartPC() < ce.getEndPC() && ce.getEndPC() <= ce.getHandlerPC()) {
				filteredEx.add(ce);
			}
		}
		return filteredEx.toArray(new CodeException[filteredEx.size()]);
	}

	/**
	 * implements the visitor to find throwing alternative exceptions from a catch block, without
	 * forwarding along the original exception
	 */
	@Override
	public void sawOpcode(int seen) {
		boolean markAsValid = false;

		try {
			stack.mergeJumps(this);
			int pc = getPC();
			for (CodeException ex : exceptions) {
				if (pc == ex.getEndPC()) {
					if (seen >= IRETURN && seen <= RETURN)
                    {
                        addCatchBlock(ex.getHandlerPC(), Integer.MAX_VALUE);
                    }
                    else if (seen == GOTO || seen == GOTO_W)
                    {
                        addCatchBlock(ex.getHandlerPC(), this.getBranchTarget());
                    }
                    else
                    {
                        addCatchBlock(ex.getHandlerPC(), Integer.MAX_VALUE);
                    }
				} else if (pc == ex.getHandlerPC()) {
                    removePreviousHandlers(pc);
                }
			}

			Iterator<CatchInfo> it = catchInfos.iterator();
			while (it.hasNext()) {
				try {
					CatchInfo catchInfo = it.next();
					if (pc == catchInfo.getStart()) {
                        if (!updateExceptionRegister(catchInfo, seen, pc))
                        {
                            it.remove();
                        }
						break;
					} else if (pc > catchInfo.getFinish()) {
						it.remove();
						break;
					} else if (pc > catchInfo.getStart() && pc <= catchInfo.getFinish()) {
						if (seen == INVOKESPECIAL) {
							if ("<init>".equals(getNameConstantOperand())) {
								String className = getClassConstantOperand();
								JavaClass exClass = Repository.lookupClass(className);
								if (exClass.instanceOf(errorClass) || (exClass.instanceOf(throwableClass))) {
									String sig = getSigConstantOperand();
									if (sig.indexOf("Exception") >= 0
									||  sig.indexOf("Throwable") >= 0
									||  sig.indexOf("Error") >= 0) {
										markAsValid = true;
										break;
									}
									if (exClass.instanceOf(assertionClass)) {
	                                    //just ignore LEST for AssertionErrors
	                                    markAsValid = true;
	                                    break;
	                                }
								}
							} else if (isPossibleExBuilder(catchInfo.getRegister())) {
								markAsValid = true;
							}
						} else if (seen == INVOKEVIRTUAL) {
                            if ("initCause".equals(getNameConstantOperand())) {
    							String className = getClassConstantOperand();
    							JavaClass exClass = Repository.lookupClass(className);
    							if (exClass.instanceOf(throwableClass)) {
    								if (stack.getStackDepth() > 1) {
    									OpcodeStack.Item itm = stack.getStackItem(1);
    									int reg = itm.getRegisterNumber();
    									if (reg >= 0)
                                        {
                                            exReg.put(Integer.valueOf(reg), Boolean.TRUE);
                                        }
    									markAsValid = true; // Fixes javac generated code
    								}
    							}
                            } else if ("getTargetException".equals(getNameConstantOperand()) && "java/lang/reflect/InvocationTargetException".equals(getClassConstantOperand())) {
                                markAsValid = true;
							} else if (isPossibleExBuilder(catchInfo.getRegister())) {
								markAsValid = true;
							}
						} else if (seen == INVOKEINTERFACE || seen == INVOKESTATIC) {
							if (isPossibleExBuilder(catchInfo.getRegister()))
                            {
                                markAsValid = true;
                            }
						} else if (seen == ATHROW) {
							if (stack.getStackDepth() > 0) {
								OpcodeStack.Item itm = stack.getStackItem(0);
								if (itm.getRegisterNumber() != catchInfo.getRegister()
								&&  itm.getUserValue() == null) {
                                    if (!isPre14Class(itm.getJavaClass()))
                                    {
                                    	int priority = getPrevOpcode(1) == MONITOREXIT ? LOW_PRIORITY : NORMAL_PRIORITY;
    									bugReporter.reportBug(new BugInstance(this, "LEST_LOST_EXCEPTION_STACK_TRACE", priority)
    													.addClass(this)
    													.addMethod(this)
    													.addSourceLine(this));
                                    }
									it.remove();
									break;
								}
							}
						} else if (seen == ASTORE || seen >= ASTORE_0 && seen <= ASTORE_3) {
						    if (lastWasExitPoint) {
						        //crazy jdk6 finally block injection -- shut off detection
						        catchInfos.clear();
						        break;
						    }

							if (stack.getStackDepth() > 0) {
								OpcodeStack.Item itm = stack.getStackItem(0);
								int reg = RegisterUtils.getAStoreReg(this, seen);
								exReg.put(Integer.valueOf(reg), (Boolean)itm.getUserValue());
								if (reg == catchInfo.getRegister() && catchInfo.getFinish() == Integer.MAX_VALUE) {
									it.remove();
								}
							}
						} else if (seen == ALOAD || seen >= ALOAD_0 && seen <= ALOAD_3) {
							Boolean valid = exReg.get(Integer.valueOf(RegisterUtils.getALoadReg(this, seen)));
							if (valid != null)
                            {
                                markAsValid = valid.booleanValue();
                            }
						} else if (seen >= IRETURN && seen <= RETURN) {
						    removeIndeterminateHandlers(pc);
						    break;
						}
					}
				} catch (ClassNotFoundException cnfe) {
					bugReporter.reportMissingClass(cnfe);
					it.remove();
				}
			}

			lastWasExitPoint = seen >= IRETURN && seen <= RETURN || seen == GOTO || seen == GOTO_W || seen == ATHROW;
		}
		finally {
			TernaryPatcher.pre(stack, seen);
			stack.sawOpcode(this, seen);
			TernaryPatcher.post(stack, seen);
			if (markAsValid) {
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item itm = stack.getStackItem(0);
					itm.setUserValue(Boolean.TRUE);
				}
			}
		}
	}

	/** returns whether the method called might be a method that builds an exception using
	 * the original exception. It does so by looking to see if the method returns an exception,
	 * and if one of the parameters is the original exception
	 * @param excReg the register of the original exception caught
	 * @return whether this method call could be an exception builder method
	 */
	public boolean isPossibleExBuilder(int excReg) throws ClassNotFoundException {
		String sig = getSigConstantOperand();
		Type returnType = Type.getReturnType(sig);
		String returnSig = returnType.getSignature();
		if (returnSig.startsWith("L")) {
			returnSig = returnSig.substring(1, returnSig.length() - 1);
			JavaClass retCls = Repository.lookupClass(returnSig);
			if (retCls.instanceOf(errorClass)) {
				int numParms = Type.getArgumentTypes(sig).length;
				if (stack.getStackDepth() >= numParms) {
					for (int p = 0; p < numParms; p++) {
						OpcodeStack.Item item = stack.getStackItem(p);
						if (item.getRegisterNumber() == excReg)
                        {
                            return true;
                        }
					}
				}
			}
		}
		return false;
	}

    /** returns whether the class in question was compiled with a jdk less than 1.4
     *
     * @param cls the class to check
     * @return whether the class is compiled with a jdk less than 1.4
     */
    private boolean isPre14Class(JavaClass cls)
    {
        return cls != null && cls.getMajor() < Constants.MAJOR_1_4;
    }

    private void removePreviousHandlers(int pc)
    {
        //This unnecessarily squashes some nested catch blocks, but better than false positives
        Iterator<CatchInfo> it = catchInfos.iterator();
        while (it.hasNext()) {
            CatchInfo ci = it.next();
            if (ci.getStart() < pc)
            {
                it.remove();
            }
        }
    }

    private void removeIndeterminateHandlers(int pc)
    {
        Iterator<CatchInfo> it = catchInfos.iterator();
        while (it.hasNext()) {
            CatchInfo ci = it.next();
            if (ci.getStart() < pc && ci.getFinish() == Integer.MAX_VALUE)
            {
                it.remove();
            }
        }
    }

    /**
     * looks to update the catchinfo block with the register used for the
     * exception variable. If their is a local variable table, but the local
     * variable can't be found return false, signifying an empty catch block.
     *
     * @param ci the catchinfo record for the catch starting at this pc
     * @param seen the opcode of the currently visited instruction
     * @param pc the current pc
     *
     * @return whether the catch block is empty
     */
    private boolean updateExceptionRegister(CatchInfo ci, int seen, int pc) {
        if (seen == ASTORE || seen >= ASTORE_0 && seen <= ASTORE_3) {
            int reg = RegisterUtils.getAStoreReg(this, seen);
            ci.setReg(reg);
            exReg.put(Integer.valueOf(reg), Boolean.TRUE);
            LocalVariableTable lvt = getMethod().getLocalVariableTable();
            if (lvt != null) {
                LocalVariable lv = lvt.getLocalVariable(reg, pc+2);
                if (lv != null) {
                    int finish = lv.getStartPC() + lv.getLength();
                    if (finish < ci.getFinish())
                    {
                        ci.setFinish(finish);
                    }
                }
                else
                {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * add a catch block info record for the catch block that is guessed to be
     * in the range of start to finish
     *
     * @param start the handler pc
     * @param finish the guessed end of the catch block
     */
	private void addCatchBlock(int start, int finish) {
		CatchInfo ci = new CatchInfo(start, finish);
		catchInfos.add(ci);
	}

	private static class CatchInfo {
		private final int catchStart;
		private int catchFinish;
		private int exReg;

		public CatchInfo(int start, int finish) {
			catchStart = start;
			catchFinish = finish;
			exReg = -1;
		}

		public void setReg(int reg) {
			exReg = reg;
		}

		public int getStart() {
			return catchStart;
		}

		public int getFinish() {
			return catchFinish;
		}

		public void setFinish(int finish) {
			catchFinish = finish;
		}

		public int getRegister() {
			return exReg;
		}
		
		@Override
		public String toString() {
		    return "Register: " + exReg + " Range: [" + catchStart + "->" + catchFinish + "]";
		}
	}
}
