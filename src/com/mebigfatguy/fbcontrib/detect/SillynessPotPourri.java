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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.bcel.Constants;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantDouble;
import org.apache.bcel.classfile.ConstantMethodref;
import org.apache.bcel.classfile.ConstantNameAndType;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.ConstantString;
import org.apache.bcel.classfile.ConstantValue;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.LocalVariable;
import org.apache.bcel.classfile.LocalVariableTable;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.CodeByteUtils;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.visitclass.LVTHelper;

/**
 * looks for silly bugs that are simple but do not fit into one large pattern.
 */
@CustomUserValue
public class SillynessPotPourri extends BytecodeScanningDetector
{
	private static final Set<String> collectionInterfaces = new HashSet<String>();
	static {
		collectionInterfaces.add("java/util/Collection");
		collectionInterfaces.add("java/util/List");
		collectionInterfaces.add("java/util/Set");
		collectionInterfaces.add("java/util/SortedSet");
		collectionInterfaces.add("java/util/Map");
		collectionInterfaces.add("java/util/SortedMap");
	}
	private static final Set<String> oddMissingEqualsClasses = new HashSet<String>();
	static {
		oddMissingEqualsClasses.add("java.lang.StringBuffer");
		oddMissingEqualsClasses.add("java.lang.StringBuilder");
	}

	private static final String LITERAL = "literal";
	private static final Pattern APPEND_PATTERN = Pattern.compile("append:([0-9]+):(.*)");

	private static JavaClass calendarClass;
	static {
		try {
			calendarClass = Repository.lookupClass("java/util/Calendar");
		} catch (ClassNotFoundException cnfe) {
			calendarClass = null;
		}
	}

	private static Map<String, Integer> methodsThatAreSillyOnStringLiterals = new HashMap<String, Integer>();
	static {
		methodsThatAreSillyOnStringLiterals.put("toLowerCase()Ljava/lang/String;", Values.ZERO);
		methodsThatAreSillyOnStringLiterals.put("toUpperCase()Ljava/lang/String;", Values.ZERO);
		methodsThatAreSillyOnStringLiterals.put("toLowerCase(Ljava/util/Locale;)Ljava/lang/String;", Values.ONE);
		methodsThatAreSillyOnStringLiterals.put("toUpperCase(Ljava/util/Locale;)Ljava/lang/String;", Values.ONE);
		methodsThatAreSillyOnStringLiterals.put("trim()Ljava/lang/String;", Values.ZERO);
		methodsThatAreSillyOnStringLiterals.put("isEmpty()Z", Values.ZERO);
	}

	private final BugReporter bugReporter;
	private final Set<String> toStringClasses;
	private OpcodeStack stack;
	private int lastPCs[];
	private int lastOpcode;
	private int lastReg;
	private boolean lastIfEqWasBoolean;
	private boolean lastLoadWasString;
	/** branch targets, to a set of branch instructions */
	private Map<Integer, BitSet> branchTargets;
	private Set<String> staticConstants;

	/**
	 * constructs a SPP detector given the reporter to report bugs on
	 * @param bugReporter the sync of bug reports
	 */
	public SillynessPotPourri(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
		toStringClasses = new HashSet<String>();
	}

	@Override
	public void visitField(Field field) {
		if ("serialVersionUID".equals(field.getName())
				&&  ((field.getAccessFlags() & ACC_STATIC) != 0)
				&&  ((field.getAccessFlags() & ACC_PRIVATE) == 0)) {
			bugReporter.reportBug(new BugInstance(this, BugType.SPP_SERIALVER_SHOULD_BE_PRIVATE.name(), LOW_PRIORITY)
			.addClass(this)
			.addField(this));
		}
	}

	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			stack = new OpcodeStack();
			lastPCs = new int[4];
			branchTargets = new HashMap<Integer, BitSet>();
			super.visitClassContext(classContext);
		} finally {
			stack = null;
			lastPCs = null;
			branchTargets = null;
			staticConstants = null;
		}
	}

	/**
	 * implements the visitor to reset the opcode stack
	 *
	 * @param obj the context object for the currently parsed Code
	 */
	@Override
	public void visitCode(Code obj) {
		stack.resetForMethodEntry(this);
		lastOpcode = -1;
		lastReg = -1;
		lastIfEqWasBoolean = false;
		lastLoadWasString = false;
		Arrays.fill(lastPCs, -1);
		branchTargets.clear();
		super.visitCode(obj);
	}

	/**
	 * implements the visitor to look for various silly bugs
	 *
	 * @param seen the opcode of the currently parsed instruction
	 */
	@Override
	public void sawOpcode(int seen) {
		int reg = -1;
		String userValue = null;
		try {
			stack.precomputation(this);

			if (isBranchByteCode(seen)) {
				Integer branchTarget = Integer.valueOf(getBranchTarget());
				BitSet branchInsSet = branchTargets.get(branchTarget);
				if (branchInsSet == null)
				{
					branchInsSet = new BitSet();
					branchTargets.put(branchTarget, branchInsSet);
				}
				branchInsSet.set(getPC());
			}
			//not an else if, because some of the opcodes in the previous branch also matter here.
			if ((seen == IFEQ) || (seen == IFLE) || (seen == IFNE)) {
				checkForEmptyStringAndNullChecks(seen);
			}
			//see above, several opcodes hit multiple branches.
			if ((seen == IFEQ) || (seen == IFNE) || (seen == IFGT)) {
				checkSizeEquals0();
			}
			
			if (seen == IFEQ) {
				checkNullAndInstanceOf();
			}

			if (seen == IFNE) {
				checkNotEqualsStringBuilderLength();
			} else if (seen == IFEQ) {
				checkEqualsStringBufferLength();
			} else if ((seen == IRETURN) && lastIfEqWasBoolean) {
				checkForUselessTernaryReturn();
			} else if (seen == LDC2_W) {
				checkApproximationsOfMathConstants();
			} else if (seen == DCMPL) {
				checkCompareToNaNDouble();
			} else if (seen == FCMPL) {
				checkCompareToNaNFloat();
			} else if (OpcodeUtils.isAStore(seen)) {
				reg = RegisterUtils.getAStoreReg(this, seen);
				checkStutterdAssignment(seen, reg);
				checkImmutableUsageOfStringBuilder(reg);
			} else if (OpcodeUtils.isALoad(seen)) {
				sawLoad(seen);
			} else if ((seen >= ICONST_0) && (seen <= ICONST_3)) {
				userValue = sawIntConst(userValue);
			} else if (seen == CALOAD) {
				checkImproperToCharArrayUse();
			} else if (seen == INVOKESTATIC) {
				userValue = sawInvokeStatic(userValue);
			} else if (seen == INVOKEVIRTUAL) {
				userValue = sawInvokeVirtual(userValue);
			} else if (seen == INVOKESPECIAL) {
				sawInvokeSpecial();
			} else if (seen == INVOKEINTERFACE) {
				userValue = sawInvokeInterface(userValue);
			}
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
		} finally {
			TernaryPatcher.pre(stack, seen);
			stack.sawOpcode(this, seen);
			TernaryPatcher.post(stack, seen);
			if ((stack.getStackDepth() > 0)) {
				OpcodeStack.Item item = stack.getStackItem(0);
				if (userValue != null) {
					item.setUserValue(userValue);
				} else if ("iterator".equals(item.getUserValue()) && (seen == GETFIELD) || (seen == ALOAD) || ((seen >= ALOAD_0) && (seen <= ALOAD_3))) {
					item.setUserValue(null);
				}
			}

			lastOpcode = seen;
			lastReg = reg;
			System.arraycopy(lastPCs, 1, lastPCs, 0, 3);
			lastPCs[3] = getPC();
		}
	}

	private void checkImproperToCharArrayUse() {
		if (stack.getStackDepth() > 0) {
			OpcodeStack.Item item = stack.getStackItem(0);
			String ic = (String)item.getUserValue();
			if ("iconst".equals(ic)) {
				bugReporter.reportBug(new BugInstance(this, BugType.SPP_USE_CHARAT.name(), NORMAL_PRIORITY)
				.addClass(this)
				.addMethod(this)
				.addSourceLine(this));
			}
		}
	}

	private String sawIntConst(String userValue) {
		if (stack.getStackDepth() > 0) {
			OpcodeStack.Item item = stack.getStackItem(0);
			String tca = (String)item.getUserValue();
			if ("toCharArray".equals(tca)) {
				userValue = "iconst";
			}
		}
		return userValue;
	}

	private void sawLoad(int seen) {
		lastLoadWasString = false;
		LocalVariableTable lvt = getMethod().getLocalVariableTable();
		if (lvt != null) {
			LocalVariable lv = LVTHelper.getLocalVariableAtPC(lvt, RegisterUtils.getALoadReg(this, seen), getPC());
			if (lv != null) {
				lastLoadWasString = "Ljava/lang/String;".equals(lv.getSignature());
			}
		}
	}

	private void checkStutterdAssignment(int seen, int reg) {
		if (seen == lastOpcode && reg == lastReg) {
				bugReporter.reportBug(new BugInstance(this, BugType.SPP_STUTTERED_ASSIGNMENT.name(), NORMAL_PRIORITY)
				.addClass(this)
				.addMethod(this)
				.addSourceLine(this));
		}
	}

	private void checkImmutableUsageOfStringBuilder(int reg) {
		if (stack.getStackDepth() > 0) {
			OpcodeStack.Item item = stack.getStackItem(0);
			String mName = (String) item.getUserValue();
			if (mName != null) {
				if ("trim".equals(mName)) {
					item.setUserValue(null);
				} else {
					Matcher m = APPEND_PATTERN.matcher(mName);
					if (m.matches()) {
						int appendReg = Integer.parseInt(m.group(1));
						if (reg == appendReg) {
							bugReporter.reportBug(new BugInstance(this, BugType.SPP_STRINGBUILDER_IS_MUTABLE.name(), NORMAL_PRIORITY)
							.addClass(this)
							.addMethod(this)
							.addSourceLine(this));
						}
					}
				}
			}
		}
	}

	private void checkCompareToNaNFloat() {
		if (stack.getStackDepth() > 1) {
			OpcodeStack.Item item = stack.getStackItem(0);
			Float f1 = (Float)item.getConstant();
			item = stack.getStackItem(1);
			Float f2 = (Float)item.getConstant();

			if (((f1 != null) && f1.isNaN()) || ((f2 != null) && f2.isNaN())) {
				bugReporter.reportBug(new BugInstance(this, BugType.SPP_USE_ISNAN.name(), NORMAL_PRIORITY)
				.addClass(this)
				.addMethod(this)
				.addSourceLine(this)
				.addString("float")
				.addString("Float"));
			}
		}
	}

	private void checkCompareToNaNDouble() {
		if (stack.getStackDepth() > 1) {
			OpcodeStack.Item item = stack.getStackItem(0);
			Double d1 = (Double)item.getConstant();
			item = stack.getStackItem(1);
			Double d2 = (Double)item.getConstant();

			if (((d1 != null) && d1.isNaN()) || ((d2 != null) && d2.isNaN())) {
				bugReporter.reportBug(new BugInstance(this, BugType.SPP_USE_ISNAN.name(), NORMAL_PRIORITY)
				.addClass(this)
				.addMethod(this)
				.addSourceLine(this)
				.addString("double")
				.addString("Double"));
			}
		}
	}

	private void checkApproximationsOfMathConstants() {
		Object con = getConstantRefOperand();
		if (con instanceof ConstantDouble) {
			double d = ((ConstantDouble) con).getBytes();
			double piDelta = Math.abs(d - Math.PI);
			double eDelta = Math.abs(d - Math.E);

			if (((piDelta > 0.0) && (piDelta < 0.002))
					||  ((eDelta > 0.0) && (eDelta < 0.002))) {
				bugReporter.reportBug(new BugInstance(this, BugType.SPP_USE_MATH_CONSTANT.name(), NORMAL_PRIORITY)
				.addClass(this)
				.addMethod(this)
				.addSourceLine(this));
			}
		}
	}

	private void checkForUselessTernaryReturn() {
		byte[] bytes = getCode().getCode();
		if ((lastPCs[0] != -1)
				&&  ((0x00FF & bytes[lastPCs[3]]) == ICONST_0)
				&&  ((0x00FF & bytes[lastPCs[2]]) == GOTO)
				&&  ((0x00FF & bytes[lastPCs[1]]) == ICONST_1)
				&&  ((0x00FF & bytes[lastPCs[0]]) == IFEQ)) {
			if (getMethod().getSignature().endsWith("Z")) {
				boolean bug = true;
				BitSet branchInsSet = branchTargets.get(Integer.valueOf(lastPCs[1]));
				if (branchInsSet != null)
				{
					bug = false;
				}
				branchInsSet = branchTargets.get(Integer.valueOf(lastPCs[3]));
				if ((branchInsSet != null) && (branchInsSet.cardinality() > 1))
				{
					bug = false;
				}

				if (bug) {
					bugReporter.reportBug(new BugInstance(this, BugType.SPP_USELESS_TERNARY.name(), NORMAL_PRIORITY)
					.addClass(this)
					.addMethod(this)
					.addSourceLine(this));
				}
			}
		}
	}

	private void checkEqualsStringBufferLength() {
		if (stack.getStackDepth() > 0) {
			OpcodeStack.Item itm = stack.getStackItem(0);
			lastIfEqWasBoolean = "Z".equals(itm.getSignature());
		}

		byte[] bytes = getCode().getCode();
		if (lastPCs[1] != -1) {
			if (CodeByteUtils.getbyte(bytes, lastPCs[3]) == INVOKEVIRTUAL) {
				int loadIns = CodeByteUtils.getbyte(bytes, lastPCs[2]);
				if  (((loadIns == LDC) || (loadIns == LDC_W))
						&&  (CodeByteUtils.getbyte(bytes, lastPCs[1]) == INVOKEVIRTUAL)) {
					ConstantPool pool = getConstantPool();
					int toStringIndex = CodeByteUtils.getshort(bytes, lastPCs[1]+1);
					Constant cmr = pool.getConstant(toStringIndex);
					if (cmr instanceof ConstantMethodref) {
						ConstantMethodref toStringMR = (ConstantMethodref)cmr;
						String toStringCls = toStringMR.getClass(pool);
						if (toStringCls.startsWith("java.lang.&&StringBu")) {
							int consIndex = CodeByteUtils.getbyte(bytes, lastPCs[2]+1);
							Constant c = pool.getConstant(consIndex);
							if (c instanceof ConstantString) {
								if ("".equals(((ConstantString) c).getBytes(pool))) {
									int nandtIndex = toStringMR.getNameAndTypeIndex();
									ConstantNameAndType cnt = (ConstantNameAndType)pool.getConstant(nandtIndex);
									if ("toString".equals(cnt.getName(pool))) {
										int lengthIndex = CodeByteUtils.getshort(bytes, lastPCs[3]+1);
										ConstantMethodref lengthMR = (ConstantMethodref)pool.getConstant(lengthIndex);
										nandtIndex = lengthMR.getNameAndTypeIndex();
										cnt = (ConstantNameAndType)pool.getConstant(nandtIndex);
										if ("equals".equals(cnt.getName(pool))) {
											bugReporter.reportBug(new BugInstance(this, BugType.SPP_USE_STRINGBUILDER_LENGTH.name(), NORMAL_PRIORITY)
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
			}
		}
	}

	private void checkNotEqualsStringBuilderLength() {
		byte[] bytes = getCode().getCode();
		if (lastPCs[2] != -1) {
			if ((CodeByteUtils.getbyte(bytes, lastPCs[3]) == INVOKEVIRTUAL)
					&&  (CodeByteUtils.getbyte(bytes, lastPCs[2]) == INVOKEVIRTUAL)) {
				ConstantPool pool = getConstantPool();
				int toStringIndex = CodeByteUtils.getshort(bytes, lastPCs[2]+1);
				ConstantMethodref toStringMR = (ConstantMethodref)pool.getConstant(toStringIndex);
				String toStringCls = toStringMR.getClass(pool);
				if (toStringCls.startsWith("java.lang.StringBu")) {
					int nandtIndex = toStringMR.getNameAndTypeIndex();
					ConstantNameAndType cnt = (ConstantNameAndType)pool.getConstant(nandtIndex);
					if ("toString".equals(cnt.getName(pool))) {
						int lengthIndex = CodeByteUtils.getshort(bytes, lastPCs[3]+1);
						ConstantMethodref lengthMR = (ConstantMethodref)pool.getConstant(lengthIndex);
						nandtIndex = lengthMR.getNameAndTypeIndex();
						cnt = (ConstantNameAndType)pool.getConstant(nandtIndex);
						if ("length".equals(cnt.getName(pool))) {
							bugReporter.reportBug(new BugInstance(this, BugType.SPP_USE_STRINGBUILDER_LENGTH.name(), NORMAL_PRIORITY)
							.addClass(this)
							.addMethod(this)
							.addSourceLine(this));
						}
					}
				}
			}
		}
	}

	private void checkNullAndInstanceOf() {
		byte[] bytes = getCode().getCode();
		if ((lastPCs[0] != -1) && (CodeByteUtils.getbyte(bytes, lastPCs[1]) == IFNULL) && (CodeByteUtils.getbyte(bytes, lastPCs[3]) == INSTANCEOF)) {
			int ins0 = CodeByteUtils.getbyte(bytes, lastPCs[0]);
			if ((ins0 == ALOAD) || (ins0 == ALOAD_0) || (ins0 == ALOAD_1) || (ins0 == ALOAD_2) || (ins0 == ALOAD_3)) {
				int ins2 = CodeByteUtils.getbyte(bytes, lastPCs[0]);
				if (ins0 == ins2) {
					if ((ins0 != ALOAD) || (CodeByteUtils.getbyte(bytes, lastPCs[0] + 1) == CodeByteUtils.getbyte(bytes, lastPCs[2] + 1))) {
						int ifNullTarget = lastPCs[1] + CodeByteUtils.getshort(bytes, lastPCs[1]+1);
						if (ifNullTarget == getBranchTarget()) {
							bugReporter.reportBug(new BugInstance(this, BugType.SPP_NULL_BEFORE_INSTANCEOF.name(), NORMAL_PRIORITY)
							.addClass(this)
							.addMethod(this)
							.addSourceLine(this));
						}
					}
				}
			}
		}
	}

	private void checkSizeEquals0() {
		if (stack.getStackDepth() == 1) {
			OpcodeStack.Item item = stack.getStackItem(0);
			if ("size".equals(item.getUserValue())) {
				bugReporter.reportBug(new BugInstance(this, BugType.SPP_USE_ISEMPTY.name(), NORMAL_PRIORITY)
				.addClass(this)
				.addMethod(this)
				.addSourceLine(this));
			}
		}
	}

	private void checkForEmptyStringAndNullChecks(int seen) {
		if (lastLoadWasString && (lastPCs[0] != -1)) {
			byte[] bytes = getCode().getCode();
			int loadIns = CodeByteUtils.getbyte(bytes, lastPCs[2]);
			int brOffset = (loadIns == ALOAD) ? 11 : 10;

			if ((((loadIns >= ALOAD_0) && (loadIns <= ALOAD_3)) || (loadIns == ALOAD))
					&&  (CodeByteUtils.getbyte(bytes, lastPCs[3]) == INVOKEVIRTUAL)
					&&  (CodeByteUtils.getbyte(bytes, lastPCs[2]) == loadIns)
					&&  (CodeByteUtils.getbyte(bytes, lastPCs[1]) == IFNULL)
					&&  (CodeByteUtils.getbyte(bytes, lastPCs[0]) == loadIns)
					&&  ((loadIns != ALOAD) || (CodeByteUtils.getbyte(bytes, lastPCs[2]+1) == CodeByteUtils.getbyte(bytes, lastPCs[0]+1)))
					&&  ((seen == IFNE) ? CodeByteUtils.getshort(bytes, lastPCs[1]+1) > brOffset : CodeByteUtils.getshort(bytes, lastPCs[1]+1) == brOffset)) {
				int nextOp = CodeByteUtils.getbyte(bytes, getNextPC());
				if ((nextOp != GOTO) && (nextOp != GOTO_W)) {
					ConstantPool pool = getConstantPool();
					int mpoolIndex = CodeByteUtils.getshort(bytes, lastPCs[3]+1);
					ConstantMethodref cmr = (ConstantMethodref)pool.getConstant(mpoolIndex);
					int nandtIndex = cmr.getNameAndTypeIndex();
					ConstantNameAndType cnt = (ConstantNameAndType)pool.getConstant(nandtIndex);
					if ("length".equals(cnt.getName(pool))) {
						bugReporter.reportBug(new BugInstance(this, BugType.SPP_SUSPECT_STRING_TEST.name(), NORMAL_PRIORITY)
						.addClass(this)
						.addMethod(this)
						.addSourceLine(this));
					}
				}
			}
		}
	}

	private static boolean isBranchByteCode(int seen) {
		return ((seen >= IFEQ) && (seen <= GOTO)) || (seen == IFNULL) || (seen == IFNONNULL) || (seen == GOTO_W);
	}

	private String sawInvokeStatic(String userValue) {
		String className = getClassConstantOperand();
		String methodName = getNameConstantOperand();
		if ("java/lang/System".equals(className)) {
			if ("getProperties".equals(methodName)) {
				userValue = "getProperties";
			} else if ("arraycopy".equals(methodName)) {
				if (stack.getStackDepth() >= 5) {
					OpcodeStack.Item item = stack.getStackItem(2);
					String sig = item.getSignature();
					if ((sig.charAt(0) != '[') && !"Ljava/lang/Object;".equals(sig)) {
						bugReporter.reportBug(new BugInstance(this, BugType.SPP_NON_ARRAY_PARM.name(), HIGH_PRIORITY)
						.addClass(this)
						.addMethod(this)
						.addSourceLine(this));
					}
					item = stack.getStackItem(4);
					sig = item.getSignature();
					if ((sig.charAt(0) != '[') && !"Ljava/lang/Object;".equals(sig)) {
						bugReporter.reportBug(new BugInstance(this, BugType.SPP_NON_ARRAY_PARM.name(), HIGH_PRIORITY)
						.addClass(this)
						.addMethod(this)
						.addSourceLine(this));
					}
				}
			}
		} else if ("java/lang/reflect/Array".equals(className)) {
			int offset = -1;
			if ("getLength".equals(methodName)) {
				offset = 0;
			} else if (methodName.startsWith("get")) {
				offset = 1;
			} else if (methodName.startsWith("set")) {
				offset = 2;
			}
			if (offset >= 0) {
				if (stack.getStackDepth() > offset) {
					OpcodeStack.Item item = stack.getStackItem(offset);
					String sig = item.getSignature();
					if ((sig.charAt(0) != '[') && !"Ljava/lang/Object;".equals(sig)) {
						bugReporter.reportBug(new BugInstance(this, BugType.SPP_NON_ARRAY_PARM.name(), HIGH_PRIORITY)
						.addClass(this)
						.addMethod(this)
						.addSourceLine(this));
					}
				}
			}

		}
		return userValue;
	}

	private String sawInvokeVirtual(String userValue) throws ClassNotFoundException {
		String className = getClassConstantOperand();
		String methodName = getNameConstantOperand();
		if ("java/util/BitSet".equals(className)) {
			bitSetSilliness(methodName);
		} else if ("java/lang/StringBuilder".equals(className) || "java/lang/StringBuffer".equals(className)) {
			userValue = stringBufferSilliness(userValue, methodName);
		} else if ("java/lang/String".equals(className)) {
			userValue = stringSilliness(userValue, methodName, getSigConstantOperand());
		} else if ("equals(Ljava/lang/Object;)Z".equals(methodName + getSigConstantOperand())) {
			equalsSilliness(className);      	
		} else if ("java/lang/Boolean".equals(className) && "booleanValue".equals(methodName)) {
			booleanSilliness();
		} else if (("java/util/GregorianCalendar".equals(className) || "java/util/Calendar".equals(className))
				&&      ("after".equals(methodName) || "before".equals(methodName))) {
			calendarBeforeAfterSilliness();
		} else if ("java/util/Properties".equals(className)) {
			propertiesSilliness(methodName);
		} else if ("toString".equals(methodName) && "java/lang/Object".equals(className)) {
			defaultToStringSilliness();
		}
		return userValue;
	}

	private void bitSetSilliness(String methodName) {
		if ("clear".equals(methodName)
				||  "flip".equals(methodName)
				||  "get".equals(methodName)
				||  "set".equals(methodName)) {
			if (stack.getStackDepth() > 0) {
				OpcodeStack.Item item = stack.getStackItem(0);
				Object o =item.getConstant();
				if (o instanceof Integer) {
					if (((Integer) o).intValue() < 0) {
						bugReporter.reportBug(new BugInstance(this, BugType.SPP_NEGATIVE_BITSET_ITEM.name(), NORMAL_PRIORITY)
						.addClass(this)
						.addMethod(this)
						.addSourceLine(this));
					}
				}
			}
		}
	}

	private String stringBufferSilliness(String userValue, String methodName) {
		if ("append".equals(methodName)) {
			if (stack.getStackDepth() > 1) {
				OpcodeStack.Item valItem = stack.getStackItem(0);
				OpcodeStack.Item sbItem = stack.getStackItem(1);
				Object constant = valItem.getConstant();
				boolean argIsLiteralString = (constant instanceof String) && (((String) constant).length() > 0);
				argIsLiteralString = argIsLiteralString && !looksLikeStaticFieldValue((String) constant);

				if (argIsLiteralString) {
					String existingAppend = (String) sbItem.getUserValue();
					if (existingAppend != null) {
						Matcher m = APPEND_PATTERN.matcher(existingAppend);
						if (m.matches() && LITERAL.equals(m.group(2))) {
							bugReporter.reportBug(new BugInstance(this, BugType.SPP_DOUBLE_APPENDED_LITERALS.name(), NORMAL_PRIORITY)
							.addClass(this)
							.addMethod(this)
							.addSourceLine(this));
							argIsLiteralString = false;
						}
					}
				}

				String literal = argIsLiteralString ? LITERAL : "";
				if (sbItem.getRegisterNumber() > -1) {
					userValue = "append:" + sbItem.getRegisterNumber() + ':' + literal;
				} else {
					userValue = (String) sbItem.getUserValue();
					if (userValue != null) {
						Matcher m = APPEND_PATTERN.matcher(userValue);
						if (m.matches()) {
							userValue = "append:" + m.group(1) + ':' + literal;
						}
					}
				}
			}
		}
		return userValue;
	}

	private String stringSilliness(String userValue, String methodName, String signature) {

		Integer stackOffset = methodsThatAreSillyOnStringLiterals.get(methodName + signature);
		if (stackOffset != null) {
			if (stack.getStackDepth() > stackOffset) {
			
				OpcodeStack.Item itm = stack.getStackItem(stackOffset.intValue());
				Object constant = itm.getConstant();
				if ((constant != null) && constant.getClass().equals(String.class) && (itm.getXField() == null)) {
					int priority = NORMAL_PRIORITY;
					if (Type.getArgumentTypes(getSigConstantOperand()).length > 0) {
						//if an argument is passed in, it may be locale-specific
						priority = LOW_PRIORITY;
					}
					bugReporter.reportBug(new BugInstance(this, BugType.SPP_CONVERSION_OF_STRING_LITERAL.name(), priority)
					.addClass(this)
					.addMethod(this)
					.addSourceLine(this)
					.addCalledMethod(this));
				}
			}
		} 
		//not an elseif because the below cases might be in the set methodsThatAreSillyOnStringLiterals
		if ("intern".equals(methodName)) {
			String owningMethod = getMethod().getName();
			if (!Values.STATIC_INITIALIZER.equals(owningMethod))
			{
				if (stack.getStackDepth() > 0) {
					OpcodeStack.Item item = stack.getStackItem(0);
					if (item.getConstant() != null) {
						bugReporter.reportBug(new BugInstance(this, BugType.SPP_INTERN_ON_CONSTANT.name(), NORMAL_PRIORITY)
						.addClass(this)
						.addMethod(this)
						.addSourceLine(this));
					}
				}
			}
		} 
		else if ("toCharArray".equals(methodName)) {
			userValue = "toCharArray";
		} else if ("toLowerCase".equals(methodName) ||  "toUpperCase".equals(methodName)) {
			userValue = "IgnoreCase";
		} else if ("equalsIgnoreCase".equals(methodName) || "compareToIgnoreCase".equals(methodName)) {
			if (stack.getStackDepth() > 1) {
				OpcodeStack.Item item = stack.getStackItem(1);
				if ("IgnoreCase".equals(item.getUserValue())) {
					bugReporter.reportBug(new BugInstance(this, BugType.SPP_USELESS_CASING.name(), NORMAL_PRIORITY)
					.addClass(this)
					.addMethod(this)
					.addSourceLine(this));
				}
				item = stack.getStackItem(0);
				String parm = (String)item.getConstant();
				if ("".equals(parm)) {
					bugReporter.reportBug(new BugInstance(this, BugType.SPP_EMPTY_CASING.name(), NORMAL_PRIORITY)
					.addClass(this)
					.addMethod(this)
					.addSourceLine(this));
				}
			}
		} else if ("trim".equals(methodName)) {
			userValue = "trim";
		} else if ("length".equals(methodName)) {
			if (stack.getStackDepth() > 0) {
				OpcodeStack.Item item = stack.getStackItem(0);
				if ("trim".equals(item.getUserValue())) {
					bugReporter.reportBug(new BugInstance(this, BugType.SPP_TEMPORARY_TRIM.name(), NORMAL_PRIORITY)
					.addClass(this)
					.addMethod(this)
					.addSourceLine(this));
				}
			}
		} else if ("equals".equals(methodName)) {
			if (stack.getStackDepth() > 1) {
				OpcodeStack.Item item = stack.getStackItem(1);
				if ("trim".equals(item.getUserValue())) {
					bugReporter.reportBug(new BugInstance(this, BugType.SPP_TEMPORARY_TRIM.name(), NORMAL_PRIORITY)
					.addClass(this)
					.addMethod(this)
					.addSourceLine(this));
				}
			}
		}
		else if ("toString".equals(methodName)) {
			bugReporter.reportBug(new BugInstance(this, BugType.SPP_TOSTRING_ON_STRING.name(), NORMAL_PRIORITY)
			.addClass(this)
			.addMethod(this)
			.addSourceLine(this));
		}
		return userValue;
	}

	private void equalsSilliness(String className) {
		try {
			JavaClass cls = Repository.lookupClass(className);
			if (cls.isEnum()) {
				bugReporter.reportBug(new BugInstance(this, BugType.SPP_EQUALS_ON_ENUM.name(), NORMAL_PRIORITY)
				.addClass(this)
				.addMethod(this)
				.addSourceLine(this));
			} else {
				if (stack.getStackDepth() >= 2) {
					OpcodeStack.Item item = stack.getStackItem(1);
					cls = item.getJavaClass();
					if (cls != null) {
						String clsName = cls.getClassName();
						if (oddMissingEqualsClasses.contains(clsName)) {
							bugReporter.reportBug(new BugInstance(this, BugType.SPP_EQUALS_ON_STRING_BUILDER.name(), NORMAL_PRIORITY)
							.addClass(this)
							.addMethod(this)
							.addSourceLine(this));
						}
					}
				}
			}
		} catch (ClassNotFoundException cnfe) {
			bugReporter.reportMissingClass(cnfe);
		}     
	}

	private void booleanSilliness() {
		if (lastPCs[0] != -1) {
			int range1Size = lastPCs[2] - lastPCs[0];
			if (range1Size == (getNextPC() - lastPCs[3])) {
				byte[] bytes = getCode().getCode();
				int ifeq = 0x000000FF & bytes[lastPCs[2]];
				if (ifeq == IFEQ) {
					int start1 = lastPCs[0];
					int start2 = lastPCs[3];
					boolean found = true;
					for (int i = 0; i < range1Size; i++) {
						if (bytes[start1+i] != bytes[start2+i]) {
							found = false;
							break;
						}
					}

					if (found) {
						bugReporter.reportBug(new BugInstance(this, BugType.SPP_INVALID_BOOLEAN_NULL_CHECK.name(), NORMAL_PRIORITY)
						.addClass(this)
						.addMethod(this)
						.addSourceLine(this));
					}
				}
			}
		}
	}

	private void calendarBeforeAfterSilliness() {
		if (stack.getStackDepth() > 1) {
			OpcodeStack.Item item = stack.getStackItem(0);
			String itemSig = item.getSignature();
			//Rule out java.lang.Object as mergeJumps can throw away type info (BUG)
			if (!"Ljava/lang/Object;".equals(itemSig) && !"Ljava/util/Calendar;".equals(itemSig) && !"Ljava/util/GregorianCalendar;".equals(itemSig)) {
				try {
					JavaClass cls = Repository.lookupClass(itemSig.substring(1, itemSig.length() - 1));
					if (!cls.instanceOf(calendarClass)) {
						bugReporter.reportBug(new BugInstance(this, BugType.SPP_INVALID_CALENDAR_COMPARE.name(), NORMAL_PRIORITY)
						.addClass(this)
						.addMethod(this)
						.addSourceLine(this));
					}
				} catch (ClassNotFoundException cnfe) {
					bugReporter.reportMissingClass(cnfe);
				}

			}
		}
	}

	private void defaultToStringSilliness() throws ClassNotFoundException {
		if (stack.getStackDepth() >= 1) {
			OpcodeStack.Item item = stack.getStackItem(0);
			JavaClass toStringClass = item.getJavaClass();
			if (toStringClass != null) {
				String toStringClassName = toStringClass.getClassName();
				if (!toStringClass.isInterface() && !toStringClass.isAbstract() && !"java.lang.Object".equals(toStringClassName) && !"java.lang.String".equals(toStringClassName) && toStringClasses.add(toStringClassName)) {
					bugReporter.reportBug(new BugInstance(this, BugType.SPP_NON_USEFUL_TOSTRING.name(), toStringClass.isFinal() ? NORMAL_PRIORITY : LOW_PRIORITY)
					.addClass(this)
					.addMethod(this)
					.addSourceLine(this));
				}
			}
		}
	}

	private void propertiesSilliness(String methodName) {
		if (("get".equals(methodName) || "getProperty".equals(methodName))) {
			if (stack.getStackDepth() > 1) {
				OpcodeStack.Item item = stack.getStackItem(1);
				if ("getProperties".equals(item.getUserValue())) {
					bugReporter.reportBug(new BugInstance(this, BugType.SPP_USE_GETPROPERTY.name(), NORMAL_PRIORITY)
					.addClass(this)
					.addMethod(this)
					.addSourceLine(this));
				}
			}
		}
	}

	private String sawInvokeInterface(String userValue) {
		String className = getClassConstantOperand();
		if ("java/util/Map".equals(className)) {
			String method = getNameConstantOperand();
			if ("keySet".equals(method)) {
				userValue = "keySet";
			}
		} else if ("java/util/Set".equals(className)) {
			String method = getNameConstantOperand();
			if ("contains".equals(method)) {
				if (stack.getStackDepth() >= 2) {
					OpcodeStack.Item item = stack.getStackItem(1);
					if ("keySet".equals(item.getUserValue())) {
						bugReporter.reportBug(new BugInstance(this, BugType.SPP_USE_CONTAINSKEY.name(), NORMAL_PRIORITY)
						.addClass(this)
						.addMethod(this)
						.addSourceLine(this));
					}
				}
			}
		} else if ("java/util/List".equals(className)) {
			String method = getNameConstantOperand();
			if ("iterator".equals(method)) {
				userValue = "iterator";
			}
		} else if ("java/util/Iterator".equals(className)) {
			String method = getNameConstantOperand();
			if ("next".equals(method)) {
				if (stack.getStackDepth() >= 1) {
					OpcodeStack.Item item = stack.getStackItem(0);
					if ("iterator".equals(item.getUserValue())) {
						bugReporter.reportBug(new BugInstance(this, BugType.SPP_USE_GET0.name(), NORMAL_PRIORITY)
						.addClass(this)
						.addMethod(this)
						.addSourceLine(this));
					}
				}
			}
		}

		if (collectionInterfaces.contains(className)) {
			String method = getNameConstantOperand();
			if ("size".equals(method)) {
				userValue = "size";
			}
		}
		return userValue;
	}

	private void sawInvokeSpecial() {
		String className = getClassConstantOperand();
		if ("java/lang/StringBuffer".equals(className)
				||  "java/lang/StringBuilder".equals(className)) {
			String methodName = getNameConstantOperand();
			if (Values.CONSTRUCTOR.equals(methodName)) {
				String signature = getSigConstantOperand();
				if ("(I)V".equals(signature)) {
					if (lastOpcode == BIPUSH) {
						if (stack.getStackDepth() > 0) {
							OpcodeStack.Item item = stack.getStackItem(0);
							Object o = item.getConstant();
							if (o instanceof Integer) {
								int parm = ((Integer) o).intValue();
								if ((parm > 32)
										&&  (parm < 127)
										&&  (parm != 64)
										&&  ((parm % 10) != 0)
										&&  ((parm % 5) != 0)) {
									bugReporter.reportBug(new BugInstance(this, BugType.SPP_NO_CHAR_SB_CTOR.name(), LOW_PRIORITY)
									.addClass(this)
									.addMethod(this)
									.addSourceLine(this));
								}
							}
						}
					}
				} else if ("(Ljava/lang/String;)V".equals(signature)) {
					if (stack.getStackDepth() > 0) {
						OpcodeStack.Item item = stack.getStackItem(0);
						String con = (String)item.getConstant();
						if ("".equals(con)) {
							bugReporter.reportBug(new BugInstance(this, BugType.SPP_STRINGBUFFER_WITH_EMPTY_STRING.name(), NORMAL_PRIORITY)
							.addClass(this)
							.addMethod(this)
							.addSourceLine(this));
						}
					}
				}
			}
		} else if ("java/math/BigDecimal".equals(className)) {
			if (stack.getStackDepth() > 0) {
				OpcodeStack.Item item = stack.getStackItem(0);
				Object constant = item.getConstant();
				if (constant instanceof Double)
				{
					Double v = (Double) constant;
					if ((v != 0.0) && (v != 1.0)) {
						bugReporter.reportBug(new BugInstance(this, BugType.SPP_USE_BIGDECIMAL_STRING_CTOR.name(), NORMAL_PRIORITY)
						.addClass(this)
						.addMethod(this)
						.addSourceLine(this));
					}
				}
			}
		}
	}

	private boolean looksLikeStaticFieldValue(String constant) {
		if (staticConstants == null) {
			staticConstants = new HashSet<String>();

			Field[] fields = getClassContext().getJavaClass().getFields();
			for (Field f : fields) {
				if (((f.getAccessFlags() & (Constants.ACC_FINAL|Constants.ACC_STATIC)) == (Constants.ACC_FINAL|Constants.ACC_STATIC)) && "Ljava/lang/String;".equals(f.getSignature())) {
					ConstantValue cv = f.getConstantValue();
					if (cv != null) {
						int cvIndex = cv.getConstantValueIndex();
						staticConstants.add(getConstantPool().getConstantString(cvIndex, Constants.CONSTANT_String));
					}
				}
			}
		}

		return staticConstants.contains(constant);
	}
}
