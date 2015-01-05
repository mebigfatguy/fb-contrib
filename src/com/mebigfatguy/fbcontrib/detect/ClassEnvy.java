/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2015 Dave Brosius
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

import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.LineNumberTable;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * finds methods that excessively use methods from another class. This probably means
 * these methods should be defined in that other class.
 */
public class ClassEnvy extends BytecodeScanningDetector
{
	private static final String ENVY_PERCENT_PROPERTY = "fb-contrib.ce.percent";
	private static final Set<String> ignorableInterfaces = new HashSet<String>(3);
	static {
		ignorableInterfaces.add("java.io.Serializable");
		ignorableInterfaces.add("java.lang.Cloneable");
		ignorableInterfaces.add("java.lang.Comparable");
	}

	private final BugReporter bugReporter;
	private OpcodeStack stack;
	private String packageName;
	private String clsName;
	private Map<String, BitSet> clsAccessCount;
	private int thisClsAccessCount;
	private String methodName;
	private boolean methodIsStatic;
	private double envyPercent = 0.90;
	private int envyMin = 5;

	/**
	 * constructs a CE detector given the reporter to report bugs on
	 * @param bugReporter the sync of bug reports
	 */
	public ClassEnvy(final BugReporter bugReporter) {
		this.bugReporter = bugReporter;
		String percent = System.getProperty(ENVY_PERCENT_PROPERTY);
		if (percent != null) {
			try {
				envyPercent = Double.parseDouble(percent);
			} catch (NumberFormatException nfe) {
				//Stick with original
			}
		}
		Integer min = Integer.getInteger("ENVY_MIN_PROPERTY");
		if (min != null) {
			envyMin = min.intValue();
		}
	}

	/**
	 * overrides the visitor to collect package and class names
	 *
	 * @param classContext the context object that holds the JavaClass being parsed
	 */
	@Override
	public void visitClassContext(final ClassContext classContext) {
		try {
			JavaClass cls = classContext.getJavaClass();
			packageName = cls.getPackageName();
			clsName = cls.getClassName();
			stack = new OpcodeStack();
			super.visitClassContext(classContext);
		} finally {
			stack = null;
			clsAccessCount = null;
		}
	}

	/**
	 * overrides the visitor to check whether the method is static
	 *
	 * @param obj the method currently being parsed
	 */
	@Override
	public void visitMethod(final Method obj) {
		methodName = obj.getName();
		methodIsStatic = (obj.getAccessFlags() & ACC_STATIC) != 0;
	}

	/**
	 * overrides the visitor to look for the method that uses another class the most, and
	 * if it exceeds the threshold reports it
	 *
	 * @param obj the code that is currently being parsed
	 */
	@Override
	@SuppressWarnings("unchecked")
	public void visitCode(final Code obj) {
		stack.resetForMethodEntry(this);
		thisClsAccessCount = 0;
		if (Values.STATIC_INITIALIZER.equals(methodName)) {
			return;
		}

		clsAccessCount = new HashMap<String, BitSet>();
		super.visitCode(obj);

		if (clsAccessCount.size() > 0) {
			Map.Entry<String, Set<Integer>>[]envies = clsAccessCount.entrySet().toArray(new Map.Entry[clsAccessCount.size()]);
			Arrays.sort(envies, new Comparator<Map.Entry<String, Set<Integer>>>() {
				@Override
				public int compare(final Map.Entry<String, Set<Integer>> entry1, final Map.Entry<String, Set<Integer>> entry2) {
					return entry2.getValue().size() - entry1.getValue().size();
				}
			});

			Map.Entry<String, Set<Integer>> bestEnvyEntry = envies[0];
			int bestEnvyCount = bestEnvyEntry.getValue().size();
			if (bestEnvyCount < envyMin) {
				return;
			}

			double bestPercent = ((double)bestEnvyCount) / ((double) (bestEnvyCount + thisClsAccessCount));

			if (bestPercent > envyPercent) {
	            String bestEnvy = bestEnvyEntry.getKey();
				if (implementsCommonInterface(bestEnvy)) {
					return;
				}

				bugReporter.reportBug( new BugInstance( this, BugType.CE_CLASS_ENVY.name(), NORMAL_PRIORITY)
				.addClass(this)
				.addMethod(this)
				.addSourceLineRange(this, 0, obj.getCode().length-1)
				.addString(bestEnvy));
			}
		}
	}

	/**
	 * overrides the visitor to look for method calls, and populate a class access count map
	 * based on the owning class of methods called.
	 *
	 * @param seen the opcode currently being parsed
	 */
	@Override
	public void sawOpcode(final int seen) {
		try {
	        stack.precomputation(this);

			if ((seen == INVOKEVIRTUAL)
					||  (seen == INVOKEINTERFACE)
					||  (seen == INVOKESTATIC)
					||  (seen == INVOKESPECIAL)) {
				String calledClass = getClassConstantOperand().replace('/', '.');

				if (seen == INVOKEINTERFACE) {
					int parmCount = Type.getArgumentTypes(this.getSigConstantOperand()).length;
					if (!countClassAccess(parmCount)) {
						countClassAccess(calledClass);
					}
				} else {
					countClassAccess(calledClass);
				}
			} else if (seen == PUTFIELD) {
				countClassAccess(1);
			} else if (seen == GETFIELD) {
				countClassAccess(0);
			} else if ((seen == ALOAD_0) && (!methodIsStatic)) {
				countClassAccess(clsName);
			}
		} finally {
			stack.sawOpcode(this, seen);
		}
	}

	/**
	 * return whether or not a class implements a common or marker interface
	 *
	 * @param name the class name to check
	 *
	 * @return if this class implements a common or marker interface
	 */
	private boolean implementsCommonInterface(String name) {
		try {
			JavaClass cls = Repository.lookupClass(name);
			JavaClass[] infs = cls.getAllInterfaces();

			for (JavaClass inf : infs) {
				String infName = inf.getClassName();
				if (ignorableInterfaces.contains(infName)) {
					continue;
				}
				if (infName.startsWith("java.")) {
					return true;
				}
			}
			return false;

		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
			return true;
		}
	}

	/**
	 * increment the count of class access of the class on the stack
	 *
	 * @param classAtStackIndex the position on the stack of the class in question
	 *
	 * @return true if the class is counted
	 */
	private boolean countClassAccess(final int classAtStackIndex) {
		String calledClass;

		try {
			if (stack.getStackDepth() > classAtStackIndex) {
				OpcodeStack.Item itm = stack.getStackItem(classAtStackIndex);
				JavaClass cls = itm.getJavaClass();
				if (cls != null) {
					calledClass = cls.getClassName();
					countClassAccess(calledClass);
					return true;
				}
			}
		} catch (ClassNotFoundException cfne) {
			bugReporter.reportMissingClass(cfne);
		}

		return false;
	}

	/**
	 * increment the count of class access of the specified class if it is in a similar
	 * package to the caller, and is not general purpose
	 *
	 * @param calledClass the class to check
	 */
	private void countClassAccess(final String calledClass) {
		if (calledClass.equals(clsName)) {
			thisClsAccessCount++;
		} else {
			String calledPackage = SignatureUtils.getPackageName(calledClass);
			if (SignatureUtils.similarPackages(calledPackage, packageName, 2) && !generalPurpose(calledClass)) {
				BitSet lineNumbers = clsAccessCount.get(calledClass);
				if (lineNumbers == null) {
					lineNumbers = new BitSet();
					addLineNumber(lineNumbers);
					clsAccessCount.put(calledClass, lineNumbers);
				} else {
					addLineNumber(lineNumbers);
				}
			}
		}
	}

	/**
	 * add the current line number to a set of line numbers
	 *
	 * @param lineNumbers the current set of line numbers
	 */
	private void addLineNumber(BitSet lineNumbers) {
		LineNumberTable lnt = getCode().getLineNumberTable();
		if (lnt == null) {
			lineNumbers.set(-lineNumbers.size());
		} else {
			int line = lnt.getSourceLine(getPC());
			if (line < 0) {
				lineNumbers.set(lineNumbers.size());
			} else {
				lineNumbers.set(line);
			}
		}
	}

	/**
	 * checks to see if the specified class is a built in class, or implements a simple interface
	 *
	 * @param className the class in question
	 *
	 * @return whether or not the class is general purpose
	 */
	private boolean generalPurpose(final String className) {

		if (className.startsWith("java.") || className.startsWith("javax.")) {
			return true;
		}

		try {
			JavaClass cls = Repository.lookupClass(className);
			JavaClass[] infs = cls.getAllInterfaces();
			for (JavaClass inf : infs) {
				String infName = inf.getClassName();
				if ("java.io.Serializable".equals(infName)
						||  "java.lang.Cloneable".equals(infName)
						||  "java.lang.Comparable".equals(infName)
						||  "java.lang.Runnable".equals(infName)) {
					continue;
				}
				if (infName.startsWith("java.lang.")
						||  infName.startsWith("javax.lang.")) {
					return true;
				}
			}
			JavaClass[] sups = cls.getSuperClasses();
			for (JavaClass sup : sups) {
				String supName = sup.getClassName();
				if ("java.lang.Object".equals(supName)
						||  "java.lang.Exception".equals(supName)
						||  "java.lang.RuntimeException".equals(supName)
						||  "java.lang.Throwable".equals(supName)) {
					continue;
				}
				if (supName.startsWith("java.lang.")
						||  supName.startsWith("javax.lang.")) {
					return true;
				}
			}
		} catch (ClassNotFoundException cfne) {
			bugReporter.reportMissingClass(cfne);
			return true;
		}

		return false;
	}
}
