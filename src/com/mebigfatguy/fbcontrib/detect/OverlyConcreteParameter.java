/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2011 Dave Brosius
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
import org.apache.bcel.classfile.ExceptionTable;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.LocalVariable;
import org.apache.bcel.classfile.LocalVariableTable;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.RegisterUtils;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for parameters that are defined by classes, but only use methods defined by an
 * implemented interface or super class. Relying on concrete classes in public signatures causes cohesion,
 * and makes low impact changes more difficult.
 */
public class OverlyConcreteParameter extends BytecodeScanningDetector
{
	private final BugReporter bugReporter;
	private JavaClass[] constrainingClasses;
	private Map<Integer, Map<JavaClass, List<MethodInfo>>> parameterDefiners;
	private Set<Integer> usedParameters;
	private JavaClass objectClass;
	private OpcodeStack stack;
	private int parmCount;
	private boolean methodSignatureIsConstrained;
	private boolean methodIsStatic;

	/**
     * constructs a OCP detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
	 */
	public OverlyConcreteParameter(final BugReporter bugReporter) {
		this.bugReporter = bugReporter;
		try {
			objectClass = Repository.lookupClass("java/lang/Object");
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
			objectClass = null;
		}
	}

	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			JavaClass[] infs = classContext.getJavaClass().getAllInterfaces();
			JavaClass[] sups = classContext.getJavaClass().getSuperClasses();
			constrainingClasses = new JavaClass[infs.length + sups.length];
			System.arraycopy(infs, 0, constrainingClasses, 0, infs.length);
			System.arraycopy(sups, 0, constrainingClasses, infs.length, sups.length);
			parameterDefiners = new HashMap<Integer, Map<JavaClass, List<MethodInfo>>>();
			usedParameters = new HashSet<Integer>();
			stack = new OpcodeStack();
			super.visitClassContext(classContext);
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
		} finally {
			constrainingClasses = null;
			parameterDefiners = null;
			usedParameters = null;
			stack = null;
		}
	}

	@Override
	public void visitMethod(Method obj) {
		methodSignatureIsConstrained = false;
		String methodName = obj.getName();

		if (!"<init>".equals(methodName)
		&&  !"<clinit>".equals(methodName)) {
			String methodSig = obj.getSignature();

			methodSignatureIsConstrained = methodIsSpecial(methodName, methodSig);
			if (!methodSignatureIsConstrained) {
				String parms = methodSig.split("\\(|\\)")[1];
				if (parms.indexOf(';') >= 0) {

					outer:for (JavaClass cls : constrainingClasses) {
						Method[] methods = cls.getMethods();
						for (Method m : methods) {
							if (methodName.equals(m.getName())) {
								if (methodSig.equals(m.getSignature())) {
									methodSignatureIsConstrained = true;
									break outer;
								}
							}
						}
					}
				}
			}
		}
	}

	@Override
	public void visitCode(final Code obj) {
		try {
			if (methodSignatureIsConstrained) {
				return;
			}

			if (obj.getCode() == null) {
				return;
			}
			Method m = getMethod();
			if (m.getName().startsWith("access$")) {
				return;
			}

			methodIsStatic = m.isStatic();
			parmCount = m.getArgumentTypes().length;

			if (parmCount == 0) {
				return;
			}

			parameterDefiners.clear();
			usedParameters.clear();
			stack.resetForMethodEntry(this);

			if (buildParameterDefiners()) {
				super.visitCode(obj);
				reportBugs();
			}
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
		}
	}

	@Override
	public void sawOpcode(final int seen) {
		if (parameterDefiners.isEmpty()) {
			return;
		}

		try {
			stack.mergeJumps(this);

			if ((seen == INVOKEVIRTUAL) || (seen == INVOKESTATIC) || (seen == INVOKESPECIAL) || (seen == INVOKEINTERFACE)) {
				String methodSig = getSigConstantOperand();
				Type[] parmTypes = Type.getArgumentTypes(methodSig);
				int stackDepth = stack.getStackDepth();
				if (stackDepth >= parmTypes.length) {
					for (int i = 0; i < parmTypes.length; i++) {
						OpcodeStack.Item itm = stack.getStackItem(i);
						int reg = itm.getRegisterNumber();
						removeUselessDefiners(parmTypes[parmTypes.length - i - 1].getSignature(), reg);
					}
				}

				if ((seen != INVOKESPECIAL) && (seen != INVOKESTATIC)) {
					if (stackDepth > parmTypes.length) {
						OpcodeStack.Item itm = stack.getStackItem(parmTypes.length);
						int reg = itm.getRegisterNumber();
						int parm = reg;
						if (!methodIsStatic) {
							parm--;
						}
						if ((parm >= 0) && (parm < parmCount)) {
							removeUselessDefiners(reg);
						}
					} else {
						parameterDefiners.clear();
					}
				}
			} else if ((seen == ASTORE) || ((seen >= ASTORE_0) && (seen <= ASTORE_3)) || (seen == PUTFIELD) || (seen == GETFIELD) || (seen == PUTSTATIC) || (seen == GETSTATIC)) {
				//Don't check parameters that are aliased
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item itm = stack.getStackItem(0);
					int reg = itm.getRegisterNumber();
					int parm = reg;
					if (!methodIsStatic) {
						parm--;
					}
					if ((parm >= 0) && (parm < parmCount)) {
						parameterDefiners.remove(Integer.valueOf(reg));
					}
				} else {
					parameterDefiners.clear();
				}

				if ((seen == GETFIELD) || (seen == PUTFIELD)) {
					if (stack.getStackDepth() > 1) {
						OpcodeStack.Item itm = stack.getStackItem(1);
						int reg = itm.getRegisterNumber();
						int parm = reg;
						if (!methodIsStatic) {
							parm--;
						}
						if ((parm >= 0) && (parm < parmCount)) {
							parameterDefiners.remove(Integer.valueOf(reg));
						}
					} else {
						parameterDefiners.clear();
					}
				}

			} else if ((seen == ALOAD) || ((seen >= ALOAD_0) && (seen <= ALOAD_3))) {
				int reg = RegisterUtils.getALoadReg(this, seen);

				int parm = reg;
				if (!methodIsStatic) {
					parm--;
				}
				if ((parm >= 0) && (parm < parmCount)) {
					usedParameters.add(Integer.valueOf(reg));
				}
			} else if (seen == AASTORE) {
				//Don't check parameters that are stored in
				if (stack.getStackDepth() >= 3) {
					OpcodeStack.Item itm = stack.getStackItem(0);
					int reg = itm.getRegisterNumber();
					int parm = reg;
					if (!methodIsStatic) {
						parm--;
					}
					if ((parm >= 0) && (parm < parmCount)) {
						parameterDefiners.remove(Integer.valueOf(reg));
					}
				} else {
					parameterDefiners.clear();
				}
			} else if (seen == ARETURN) {
				if (stack.getStackDepth() >= 1) {
					OpcodeStack.Item item = stack.getStackItem(0);
					int reg = item.getRegisterNumber();
					int parm = reg;
					if (!methodIsStatic) {
						parm--;
					}

					if ((parm >= 0) && (parm < parmCount)) {
						parameterDefiners.remove(Integer.valueOf(reg));
					}
				} else {
					parameterDefiners.clear();
				}
			}
		} finally {
			stack.sawOpcode(this, seen);
		}
	}

	private boolean methodIsSpecial(String methodName, String methodSig) {
		if ("readObject".equals(methodName) && "(Ljava/io/ObjectInputStream;)V".equals(methodSig)) {
			return true;
		}

		return false;
	}

	private void reportBugs() {
		Iterator<Map.Entry<Integer, Map<JavaClass, List<MethodInfo>>>> it = parameterDefiners.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Map<JavaClass, List<MethodInfo>>> entry = it.next();

			Integer reg = entry.getKey();
			if (!usedParameters.contains(reg)) {
				it.remove();
				continue;
			}
			Map<JavaClass, List<MethodInfo>> definers = entry.getValue();
			definers.remove(objectClass);
			if (definers.size() > 0) {
				String name = "";
				LocalVariableTable lvt = getMethod().getLocalVariableTable();
				if (lvt != null) {
					LocalVariable lv = lvt.getLocalVariable(reg.intValue(), 0);
					if (lv != null) {
						name = lv.getName();
					}
				}
				int parm = reg.intValue();
				if (!methodIsStatic) {
					parm--;
				}
				parm++; //users expect 1 based parameters

				String infName = definers.keySet().iterator().next().getClassName();
				bugReporter.reportBug( new BugInstance(this, "OCP_OVERLY_CONCRETE_PARAMETER", NORMAL_PRIORITY)
					.addClass(this)
					.addMethod(this)
					.addSourceLine(this, 0)
					.addString("Parameter [" + parm + "] " + name + " implements " + infName));
			}
		}
	}

	/**
	 * builds a map of method information for each method of each interface that each parameter implements of this method
	 * @return a map by parameter id of all the method signatures that interfaces of that parameter implements
	 *
	 * @throws ClassNotFoundException if the class can't be loaded
	 */
	private boolean buildParameterDefiners()
		throws ClassNotFoundException {

		Type[] parms = getMethod().getArgumentTypes();
		if (parms.length == 0) {
			return false;
		}

		boolean hasPossiblyOverlyConcreteParm = false;

		for (int i = 0; i < parms.length; i++) {
			String parm = parms[i].getSignature();
			if (parm.startsWith("L")) {
				String clsName = parm.substring(1, parm.length() - 1).replace('/', '.');
				if (clsName.startsWith("java.lang.")) {
					continue;
				}

				JavaClass cls = Repository.lookupClass(clsName);
				if (cls.isClass() && (!cls.isAbstract())) {
					Map<JavaClass, List<MethodInfo>> definers = getClassDefiners(cls);

					if (definers.size() > 0) {
						parameterDefiners.put( Integer.valueOf(i + (methodIsStatic ? 0 : 1)), definers );
						hasPossiblyOverlyConcreteParm = true;
					}
				}
			}
		}

		return hasPossiblyOverlyConcreteParm;
	}

	/**
	 * returns a map of method information for each public method for each interface this class implements
	 * @param cls the class whose interfaces to record
	 *
	 * @return a map of (method name)(method sig) by interface
	 * @throws ClassNotFoundException if unable to load the class
	 */
	private Map<JavaClass, List<MethodInfo>> getClassDefiners(final JavaClass cls)
		throws ClassNotFoundException {
		Map<JavaClass, List<MethodInfo>> definers = new HashMap<JavaClass, List<MethodInfo>>();

		for (JavaClass ci : cls.getAllInterfaces()) {
			if ("java.lang.Comparable".equals(ci.getClassName())) {
				continue;
			}
			List<MethodInfo> methodInfos = getPublicMethodInfos(ci);
			if (methodInfos.size() > 0) {
				definers.put(ci, methodInfos);
			}
		}
		return definers;
	}

	/**
	 * returns a lost of method information of all public or protected methods in this class
	 *
	 * @param cls the class to look for methods
	 * @return a map of (method name)(method signature)
	 */
	private List<MethodInfo> getPublicMethodInfos(final JavaClass cls) {
		List<MethodInfo> methodInfos = new ArrayList<MethodInfo>();
		Method[] methods = cls.getMethods();
		for (Method m : methods) {
			if ((m.getAccessFlags() & (Constants.ACC_PUBLIC|Constants.ACC_PROTECTED)) != 0) {
				ExceptionTable et = m.getExceptionTable();
				methodInfos.add(new MethodInfo(m.getName(), m.getSignature(), et == null ? null : et.getExceptionNames()));
			}
		}
		return methodInfos;
	}

	private void removeUselessDefiners(final int reg) {

		Map<JavaClass, List<MethodInfo>> definers = parameterDefiners.get(Integer.valueOf(reg));
		if ((definers != null) && (definers.size() > 0)) {
			String methodSig = getSigConstantOperand();
			String methodName = getNameConstantOperand();
			MethodInfo methodInfo = new MethodInfo(methodName, methodSig, null);

			Iterator<List<MethodInfo>> it = definers.values().iterator();
			while (it.hasNext()) {
				boolean methodDefined = false;
				List<MethodInfo> methodSigs = it.next();

                for (MethodInfo mi : methodSigs) {
					if (methodInfo.equals(mi)) {
						methodDefined = true;
						String[] exceptions = mi.getMethodExceptions();
						if (exceptions != null) {
							for (String ex : exceptions) {
								if (!isExceptionHandled(ex)) {
									methodDefined = false;
									break;
								}
							}
						}
						break;
					}
				}
				if (!methodDefined) {
					it.remove();
				}
			}
			if (definers.isEmpty()) {
				parameterDefiners.remove(Integer.valueOf(reg));
			}
		}
	}

	/**
	 * returns whether this exception is handled either in a try/catch or throws clause at this pc
	 *
	 * @param ex the name of the exception
	 *
	 * @return whether the exception is handled
	 */
	private boolean isExceptionHandled(String ex) {
		try {
			JavaClass thrownEx = Repository.lookupClass(ex);
			//First look at the throws clause
			ExceptionTable et = getMethod().getExceptionTable();
			if (et != null) {
				String[] throwClauseExNames = et.getExceptionNames();
				for (String throwClauseExName : throwClauseExNames) {
					JavaClass throwClauseEx = Repository.lookupClass(throwClauseExName);
					if (thrownEx.instanceOf(throwClauseEx)) {
						return true;
					}
				}
			}
			// Next look at the try catch blocks
			CodeException[] catchExs = getCode().getExceptionTable();
			if (catchExs != null) {
				int pc = getPC();
				for (CodeException catchEx : catchExs) {
					if ((pc >= catchEx.getStartPC()) && (pc <= catchEx.getEndPC())) {
						int type = catchEx.getCatchType();
						if (type != 0) {
							String catchExName = getConstantPool().getConstantString(type, Constants.CONSTANT_Class);
							JavaClass catchException = Repository.lookupClass(catchExName);
							if (thrownEx.instanceOf(catchException)) {
								return true;
							}
						}
					}
				}
			}
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
		}
		return false;
	}

	private void removeUselessDefiners(String parmSig, final int reg) {
		if (parmSig.startsWith("L")) {
			parmSig = parmSig.substring( 1, parmSig.length() - 1).replace('/', '.');
			if ("java.lang.Object".equals(parmSig)) {
				parameterDefiners.remove(Integer.valueOf(reg));
				return;
			}

			Map<JavaClass, List<MethodInfo>> definers = parameterDefiners.get(Integer.valueOf(reg));
			if ((definers != null) && (definers.size() > 0)) {
				Iterator<JavaClass> it = definers.keySet().iterator();
				while (it.hasNext()) {
					JavaClass definer = it.next();
					if (!definer.getClassName().equals(parmSig)) {
						it.remove();
					}
				}

				if (definers.isEmpty()) {
					parameterDefiners.remove(Integer.valueOf(reg));
				}
			}
		}
	}

	public static class MethodInfo
	{
		private final String methodName;
		private final String methodSig;
		private final String[] methodExceptions;

		public MethodInfo(String name, String sig, String[] excs) {
			methodName = name;
			methodSig = sig;
			methodExceptions = excs;
		}

		public String getMethodName() {
			return methodName;
		}

		public String getMethodSignature() {
			return methodSig;
		}

		public String[] getMethodExceptions() {
			return methodExceptions;
		}

		@Override
		public int hashCode() {
			return methodName.hashCode() ^ methodSig.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof MethodInfo)) {
				return false;
			}

			MethodInfo that = (MethodInfo)o;

			if (!methodName.equals(that.methodName)) {
				return false;
			}
			if (!methodSig.equals(that.methodSig)) {
				return false;
			}

			return true;
		}

		@Override
		public String toString() {
			return methodName + methodSig;
		}
	}
}
