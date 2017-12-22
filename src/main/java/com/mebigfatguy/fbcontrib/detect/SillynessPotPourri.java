/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2017 Dave Brosius
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

import javax.annotation.Nullable;

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
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.CodeByteUtils;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.QMethod;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.TernaryPatcher;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XField;
import edu.umd.cs.findbugs.ba.XMethod;
import edu.umd.cs.findbugs.visitclass.LVTHelper;

/**
 * looks for silly bugs that are simple but do not fit into one large pattern.
 */
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "CLI_CONSTANT_LIST_INDEX", justification = "lastPCs is an int[] of size 4 for efficiency reasons")
@CustomUserValue
public class SillynessPotPourri extends BytecodeScanningDetector {

    private static final Set<String> collectionInterfaces = UnmodifiableSet.create(Values.SLASHED_JAVA_UTIL_COLLECTION, Values.SLASHED_JAVA_UTIL_LIST,
            Values.SLASHED_JAVA_UTIL_SET, "java/util/SortedSet", Values.SLASHED_JAVA_UTIL_MAP, "java/util/SortedMap");

    private static final Set<String> oddMissingEqualsClasses = UnmodifiableSet.create("java.lang.StringBuffer", "java.lang.StringBuilder");

    /**
     * java.util.Optional is handled in the detector OptionalIssues
     */
    private static final Set<String> optionalClasses = UnmodifiableSet.create("com.google.common.base.Optional", "org.openjdk.jmh.util.Optional");

    private static final String LITERAL = "literal";
    private static final Pattern APPEND_PATTERN = Pattern.compile("([0-9]+):(.*)");

    private static final Set<String> mapSets = UnmodifiableSet.create("keySet", "values", "entrySet");

    private static JavaClass calendarClass;
    private static JavaClass mapClass;

    static {
        try {
            calendarClass = Repository.lookupClass("java/util/Calendar");
            mapClass = Repository.lookupClass("java/util/Map");
        } catch (ClassNotFoundException cnfe) {
            calendarClass = null;
            mapClass = null;
        }
    }

    private static Map<QMethod, Integer> methodsThatAreSillyOnStringLiterals = new HashMap<>();

    static {
        String localeToString = new SignatureBuilder().withParamTypes("java/util/Locale").withReturnType(Values.SLASHED_JAVA_LANG_STRING).toString();
        methodsThatAreSillyOnStringLiterals.put(new QMethod("toLowerCase", SignatureBuilder.SIG_VOID_TO_STRING), Values.ZERO);
        methodsThatAreSillyOnStringLiterals.put(new QMethod("toUpperCase", SignatureBuilder.SIG_VOID_TO_STRING), Values.ZERO);
        methodsThatAreSillyOnStringLiterals.put(new QMethod("toLowerCase", localeToString), Values.ONE);
        methodsThatAreSillyOnStringLiterals.put(new QMethod("toUpperCase", localeToString), Values.ONE);
        methodsThatAreSillyOnStringLiterals.put(new QMethod("trim", SignatureBuilder.SIG_VOID_TO_STRING), Values.ZERO);
        methodsThatAreSillyOnStringLiterals.put(new QMethod("isEmpty", SignatureBuilder.SIG_VOID_TO_BOOLEAN), Values.ZERO);
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
    private Map<SPPUserValue, Integer> trimLocations;
    private boolean isInterface;

    /**
     * constructs a SPP detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public SillynessPotPourri(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
        toStringClasses = new HashSet<>();
    }

    @Override
    public void visitField(Field field) {
        if (!isInterface && "serialVersionUID".equals(field.getName()) && (field.isStatic()) && (!field.isPrivate())) {
            bugReporter.reportBug(new BugInstance(this, BugType.SPP_SERIALVER_SHOULD_BE_PRIVATE.name(), LOW_PRIORITY).addClass(this).addField(this));
        }
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            lastPCs = new int[4];
            branchTargets = new HashMap<>();
            trimLocations = new HashMap<>();
            isInterface = classContext.getJavaClass().isInterface();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            lastPCs = null;
            branchTargets = null;
            trimLocations = null;
            staticConstants = null;
        }
    }

    /**
     * implements the visitor to reset the opcode stack
     *
     * @param obj
     *            the context object for the currently parsed Code
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
        trimLocations.clear();
        super.visitCode(obj);
    }

    /**
     * implements the visitor to look for various silly bugs
     *
     * @param seen
     *            the opcode of the currently parsed instruction
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "SF_SWITCH_FALLTHROUGH", justification = "This fall-through is deliberate and documented")
    @Override
    public void sawOpcode(int seen) {
        int reg = -1;
        SPPUserValue userValue = null;
        try {
            stack.precomputation(this);

            checkTrimLocations();

            if (isBranchByteCode(seen)) {
                Integer branchTarget = Integer.valueOf(getBranchTarget());
                BitSet branchInsSet = branchTargets.get(branchTarget);
                if (branchInsSet == null) {
                    branchInsSet = new BitSet();
                    branchTargets.put(branchTarget, branchInsSet);
                }
                branchInsSet.set(getPC());
            }
            // not an else if, because some of the opcodes in the previous
            // branch also matter here.
            if ((seen == IFEQ) || (seen == IFLE) || (seen == IFNE)) {
                checkForEmptyStringAndNullChecks(seen);
            }
            // see above, several opcodes hit multiple branches.
            if ((seen == IFEQ) || (seen == IFNE) || (seen == IFGT)) {
                checkSizeEquals0();
            }

            if (seen == IFEQ) {
                checkNullAndInstanceOf();
            }

            switch (seen) {
                case IFNE:
                    checkNotEqualsStringBuilderLength();
                break;
                case IFEQ:
                    checkEqualsStringBufferLength();
                break;
                case IRETURN: {
                    if (lastIfEqWasBoolean) {
                        checkForUselessTernaryReturn();
                    }
                }
                // $FALL-THROUGH$
                case LRETURN:
                case DRETURN:
                case FRETURN:
                case ARETURN:
                    trimLocations.clear();
                break;
                case LDC2_W:
                    checkApproximationsOfMathConstants();
                break;
                case DCMPL:
                    checkCompareToNaNDouble();
                break;
                case FCMPL:
                    checkCompareToNaNFloat();
                break;
                case ICONST_0:
                case ICONST_1:
                case ICONST_2:
                case ICONST_3:
                    userValue = sawIntConst();
                break;
                case CALOAD:
                    checkImproperToCharArrayUse();
                break;
                case INVOKESTATIC:
                    userValue = sawInvokeStatic();
                break;
                case INVOKEVIRTUAL:
                    userValue = sawInvokeVirtual();
                break;
                case INVOKESPECIAL:
                    sawInvokeSpecial();
                break;
                case INVOKEINTERFACE:
                    userValue = sawInvokeInterface();
                break;
                case IFNULL:
                case IFNONNULL:
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item itm = stack.getStackItem(0);
                        JavaClass cls = itm.getJavaClass();
                        if ((cls != null) && optionalClasses.contains(cls.getClassName())) {
                            bugReporter.reportBug(new BugInstance(this, BugType.SPP_NULL_CHECK_ON_OPTIONAL.name(), NORMAL_PRIORITY).addClass(this)
                                    .addMethod(this).addSourceLine(this));
                        }

                        XMethod method = itm.getReturnValueOf();
                        if ((method != null) && (mapClass != null)) {
                            if (mapSets.contains(method.getName())) {

                                cls = Repository.lookupClass(method.getClassName());
                                if (cls.implementationOf(mapClass)) {
                                    bugReporter.reportBug(new BugInstance(this, BugType.SPP_NULL_CHECK_ON_MAP_SUBSET_ACCESSOR.name(), NORMAL_PRIORITY)
                                            .addClass(this).addMethod(this).addSourceLine(this));
                                }

                            }
                        }
                    }
                break;
                default:
                    if (OpcodeUtils.isALoad(seen)) {
                        sawLoad(seen);
                    } else if (OpcodeUtils.isAStore(seen)) {
                        reg = RegisterUtils.getAStoreReg(this, seen);
                        checkTrimDupStore();
                        checkStutterdAssignment(seen, reg);
                        checkImmutableUsageOfStringBuilder(reg);
                    }
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
                } else {
                    SPPUserValue uv = (SPPUserValue) item.getUserValue();
                    if ((((uv != null) && (uv.getMethod() == SPPMethod.ITERATOR)) && (seen == GETFIELD)) || (seen == ALOAD)
                            || ((seen >= ALOAD_0) && (seen <= ALOAD_3))) {
                        item.setUserValue(null);
                    }
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
            SPPUserValue uv = (SPPUserValue) item.getUserValue();
            if ((uv != null) && (uv.getMethod() == SPPMethod.ICONST)) {
                bugReporter.reportBug(new BugInstance(this, BugType.SPP_USE_CHARAT.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
            }
        }
    }

    @Nullable
    private SPPUserValue sawIntConst() {
        if (stack.getStackDepth() > 0) {
            OpcodeStack.Item item = stack.getStackItem(0);
            SPPUserValue uv = (SPPUserValue) item.getUserValue();
            if ((uv != null) && (uv.getMethod() == SPPMethod.TOCHARARRAY)) {
                return new SPPUserValue(SPPMethod.ICONST);
            }
        }

        return null;
    }

    private void sawLoad(int seen) {
        lastLoadWasString = false;
        LocalVariableTable lvt = getMethod().getLocalVariableTable();
        if (lvt != null) {
            LocalVariable lv = LVTHelper.getLocalVariableAtPC(lvt, RegisterUtils.getALoadReg(this, seen), getPC());
            if (lv != null) {
                lastLoadWasString = Values.SIG_JAVA_LANG_STRING.equals(lv.getSignature());
            }
        }
    }

    /**
     * determines whether this operation is storing the result of a trim() call, where the trimmed string was duplicated on the stack. If it was, it clears any
     * trim uservalue that was left behind in the dupped stack object
     */
    private void checkTrimDupStore() {
        if ((stack.getStackDepth() >= 2) && (getPrevOpcode(1) == Constants.DUP)) {
            OpcodeStack.Item item = stack.getStackItem(0);
            SPPUserValue uv = (SPPUserValue) item.getUserValue();
            if ((uv == null) || (uv.getMethod() != SPPMethod.TRIM)) {
                return;
            }

            item = stack.getStackItem(1);
            uv = (SPPUserValue) item.getUserValue();
            if ((uv == null) || (uv.getMethod() != SPPMethod.TRIM)) {
                return;
            }

            item.setUserValue(null);
        }
    }

    private void checkStutterdAssignment(int seen, int reg) {
        if ((seen == lastOpcode) && (reg == lastReg)) {
            bugReporter.reportBug(
                    new BugInstance(this, BugType.SPP_STUTTERED_ASSIGNMENT.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
        }
    }

    private void checkImmutableUsageOfStringBuilder(int reg) {
        if (stack.getStackDepth() > 0) {
            OpcodeStack.Item item = stack.getStackItem(0);
            SPPUserValue userValue = (SPPUserValue) item.getUserValue();
            if (userValue != null) {
                if (userValue.getMethod() == SPPMethod.TRIM) {
                    item.setUserValue(null);
                } else if (userValue.getMethod() == SPPMethod.APPEND) {
                    Matcher m = APPEND_PATTERN.matcher(userValue.getDetails());
                    if (m.matches()) {
                        int appendReg = Integer.parseInt(m.group(1));
                        if (reg == appendReg) {
                            bugReporter.reportBug(new BugInstance(this, BugType.SPP_STRINGBUILDER_IS_MUTABLE.name(), NORMAL_PRIORITY).addClass(this)
                                    .addMethod(this).addSourceLine(this));
                        }
                    }
                }
            }
        }
    }

    private void checkCompareToNaNFloat() {
        if (stack.getStackDepth() > 1) {
            OpcodeStack.Item item = stack.getStackItem(0);
            Float f1 = (Float) item.getConstant();
            item = stack.getStackItem(1);
            Float f2 = (Float) item.getConstant();

            if (((f1 != null) && f1.isNaN()) || ((f2 != null) && f2.isNaN())) {
                bugReporter.reportBug(new BugInstance(this, BugType.SPP_USE_ISNAN.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this)
                        .addString("float").addString("Float"));
            }
        }
    }

    private void checkCompareToNaNDouble() {
        if (stack.getStackDepth() > 1) {
            OpcodeStack.Item item = stack.getStackItem(0);
            Double d1 = (Double) item.getConstant();
            item = stack.getStackItem(1);
            Double d2 = (Double) item.getConstant();

            if (((d1 != null) && d1.isNaN()) || ((d2 != null) && d2.isNaN())) {
                bugReporter.reportBug(new BugInstance(this, BugType.SPP_USE_ISNAN.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this)
                        .addString("double").addString("Double"));
            }
        }
    }

    private void checkApproximationsOfMathConstants() {
        Object con = getConstantRefOperand();
        if (con instanceof ConstantDouble) {
            double d = ((ConstantDouble) con).getBytes();
            double piDelta = Math.abs(d - Math.PI);
            double eDelta = Math.abs(d - Math.E);

            if (((piDelta > 0.0) && (piDelta < 0.002)) || ((eDelta > 0.0) && (eDelta < 0.002))) {
                bugReporter.reportBug(
                        new BugInstance(this, BugType.SPP_USE_MATH_CONSTANT.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
            }
        }
    }

    private void checkForUselessTernaryReturn() {
        byte[] bytes = getCode().getCode();
        if ((lastPCs[0] != -1) && ((0x00FF & bytes[lastPCs[3]]) == ICONST_0) && ((0x00FF & bytes[lastPCs[2]]) == GOTO)
                && ((0x00FF & bytes[lastPCs[1]]) == ICONST_1) && ((0x00FF & bytes[lastPCs[0]]) == IFEQ)
                && getMethod().getSignature().endsWith(Values.SIG_PRIMITIVE_BOOLEAN)) {
            boolean bug = true;
            BitSet branchInsSet = branchTargets.get(Integer.valueOf(lastPCs[1]));
            if (branchInsSet != null) {
                bug = false;
            }
            branchInsSet = branchTargets.get(Integer.valueOf(lastPCs[3]));
            if ((branchInsSet != null) && (branchInsSet.cardinality() > 1)) {
                bug = false;
            }

            if (bug) {
                bugReporter.reportBug(
                        new BugInstance(this, BugType.SPP_USELESS_TERNARY.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
            }
        }
    }

    private void checkEqualsStringBufferLength() {
        if (stack.getStackDepth() > 0) {
            OpcodeStack.Item itm = stack.getStackItem(0);
            lastIfEqWasBoolean = Values.SIG_PRIMITIVE_BOOLEAN.equals(itm.getSignature());
        }

        byte[] bytes = getCode().getCode();
        if ((lastPCs[1] != -1) && (CodeByteUtils.getbyte(bytes, lastPCs[3]) == INVOKEVIRTUAL)) {
            int loadIns = CodeByteUtils.getbyte(bytes, lastPCs[2]);
            if (((loadIns == LDC) || (loadIns == LDC_W)) && (CodeByteUtils.getbyte(bytes, lastPCs[1]) == INVOKEVIRTUAL)) {
                ConstantPool pool = getConstantPool();
                int toStringIndex = CodeByteUtils.getshort(bytes, lastPCs[1] + 1);
                Constant cmr = pool.getConstant(toStringIndex);
                if (cmr instanceof ConstantMethodref) {
                    ConstantMethodref toStringMR = (ConstantMethodref) cmr;
                    String toStringCls = toStringMR.getClass(pool);
                    if (toStringCls.startsWith("java.lang.StringBu")) {
                        int consIndex = CodeByteUtils.getbyte(bytes, lastPCs[2] + 1);
                        Constant c = pool.getConstant(consIndex);
                        if ((c instanceof ConstantString) && ((ConstantString) c).getBytes(pool).isEmpty()) {
                            int nandtIndex = toStringMR.getNameAndTypeIndex();
                            ConstantNameAndType cnt = (ConstantNameAndType) pool.getConstant(nandtIndex);
                            if (Values.TOSTRING.equals(cnt.getName(pool))) {
                                int lengthIndex = CodeByteUtils.getshort(bytes, lastPCs[3] + 1);
                                ConstantMethodref lengthMR = (ConstantMethodref) pool.getConstant(lengthIndex);
                                nandtIndex = lengthMR.getNameAndTypeIndex();
                                cnt = (ConstantNameAndType) pool.getConstant(nandtIndex);
                                if ("equals".equals(cnt.getName(pool))) {
                                    bugReporter.reportBug(new BugInstance(this, BugType.SPP_USE_STRINGBUILDER_LENGTH.name(), NORMAL_PRIORITY).addClass(this)
                                            .addMethod(this).addSourceLine(this));
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
        if ((lastPCs[2] != -1) && (CodeByteUtils.getbyte(bytes, lastPCs[3]) == INVOKEVIRTUAL) && (CodeByteUtils.getbyte(bytes, lastPCs[2]) == INVOKEVIRTUAL)) {
            ConstantPool pool = getConstantPool();
            int toStringIndex = CodeByteUtils.getshort(bytes, lastPCs[2] + 1);
            ConstantMethodref toStringMR = (ConstantMethodref) pool.getConstant(toStringIndex);
            String toStringCls = toStringMR.getClass(pool);
            if (toStringCls.startsWith("java.lang.StringBu")) {
                int nandtIndex = toStringMR.getNameAndTypeIndex();
                ConstantNameAndType cnt = (ConstantNameAndType) pool.getConstant(nandtIndex);
                if (Values.TOSTRING.equals(cnt.getName(pool))) {
                    int lengthIndex = CodeByteUtils.getshort(bytes, lastPCs[3] + 1);
                    ConstantMethodref lengthMR = (ConstantMethodref) pool.getConstant(lengthIndex);
                    nandtIndex = lengthMR.getNameAndTypeIndex();
                    cnt = (ConstantNameAndType) pool.getConstant(nandtIndex);
                    if ("length".equals(cnt.getName(pool))) {
                        bugReporter.reportBug(new BugInstance(this, BugType.SPP_USE_STRINGBUILDER_LENGTH.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                                .addSourceLine(this));
                    }
                }
            }
        }
    }

    private void checkNullAndInstanceOf() {
        byte[] bytes = getCode().getCode();
        if ((lastPCs[0] != -1) && (CodeByteUtils.getbyte(bytes, lastPCs[1]) == IFNULL) && (CodeByteUtils.getbyte(bytes, lastPCs[3]) == INSTANCEOF)) {
            int ins0 = CodeByteUtils.getbyte(bytes, lastPCs[0]);
            if (OpcodeUtils.isALoad(ins0)) {
                int ins2 = CodeByteUtils.getbyte(bytes, lastPCs[2]);
                if ((ins0 == ins2) && ((ins0 != ALOAD) || (CodeByteUtils.getbyte(bytes, lastPCs[0] + 1) == CodeByteUtils.getbyte(bytes, lastPCs[2] + 1)))) {
                    int ifNullTarget = lastPCs[1] + CodeByteUtils.getshort(bytes, lastPCs[1] + 1);
                    if (ifNullTarget == getBranchTarget()) {
                        bugReporter.reportBug(new BugInstance(this, BugType.SPP_NULL_BEFORE_INSTANCEOF.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                                .addSourceLine(this));
                    }
                }
            }
        }
    }

    private void checkSizeEquals0() {
        if (stack.getStackDepth() == 1) {
            OpcodeStack.Item item = stack.getStackItem(0);
            SPPUserValue uv = (SPPUserValue) item.getUserValue();
            if ((uv != null) && (uv.getMethod() == SPPMethod.SIZE)) {
                bugReporter
                        .reportBug(new BugInstance(this, BugType.SPP_USE_ISEMPTY.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
            }
        }
    }

    private void checkForEmptyStringAndNullChecks(int seen) {
        if (lastLoadWasString && (lastPCs[0] != -1)) {
            byte[] bytes = getCode().getCode();
            int loadIns = CodeByteUtils.getbyte(bytes, lastPCs[2]);

            if ((((loadIns >= ALOAD_0) && (loadIns <= ALOAD_3)) || (loadIns == ALOAD)) && (CodeByteUtils.getbyte(bytes, lastPCs[3]) == INVOKEVIRTUAL)
                    && (CodeByteUtils.getbyte(bytes, lastPCs[2]) == loadIns) && (CodeByteUtils.getbyte(bytes, lastPCs[1]) == IFNULL)
                    && (CodeByteUtils.getbyte(bytes, lastPCs[0]) == loadIns)
                    && ((loadIns != ALOAD) || (CodeByteUtils.getbyte(bytes, lastPCs[2] + 1) == CodeByteUtils.getbyte(bytes, lastPCs[0] + 1)))) {

                int brOffset = (loadIns == ALOAD) ? 11 : 10;
                if ((seen == IFNE) ? CodeByteUtils.getshort(bytes, lastPCs[1] + 1) > brOffset : CodeByteUtils.getshort(bytes, lastPCs[1] + 1) == brOffset) {
                    int nextOp = CodeByteUtils.getbyte(bytes, getNextPC());
                    if ((nextOp != GOTO) && (nextOp != GOTO_W)) {
                        ConstantPool pool = getConstantPool();
                        int mpoolIndex = CodeByteUtils.getshort(bytes, lastPCs[3] + 1);
                        ConstantMethodref cmr = (ConstantMethodref) pool.getConstant(mpoolIndex);
                        int nandtIndex = cmr.getNameAndTypeIndex();
                        ConstantNameAndType cnt = (ConstantNameAndType) pool.getConstant(nandtIndex);
                        if ("length".equals(cnt.getName(pool))) {
                            bugReporter.reportBug(new BugInstance(this, BugType.SPP_SUSPECT_STRING_TEST.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                                    .addSourceLine(this));
                        }
                    }
                }
            }
        }
    }

    private static boolean isBranchByteCode(int seen) {
        return ((seen >= IFEQ) && (seen <= GOTO)) || (seen == IFNULL) || (seen == IFNONNULL) || (seen == GOTO_W);
    }

    private SPPUserValue sawInvokeStatic() {
        SPPUserValue userValue = null;

        String className = getClassConstantOperand();
        String methodName = getNameConstantOperand();
        if ("java/lang/System".equals(className)) {
            if ("getProperties".equals(methodName)) {
                userValue = new SPPUserValue(SPPMethod.GETPROPERTIES);
            } else if ("arraycopy".equals(methodName) && (stack.getStackDepth() >= 5)) {
                checkForArrayParameter(stack.getStackItem(2));
                checkForArrayParameter(stack.getStackItem(4));
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
            if ((offset >= 0) && (stack.getStackDepth() > offset)) {
                checkForArrayParameter(stack.getStackItem(offset));
            }
        } else if (Values.SLASHED_JAVA_LANG_STRING.equals(className) && "format".equals(methodName) && (stack.getStackDepth() >= 2)) {
            OpcodeStack.Item item = stack.getStackItem(1);
            String format = (String) item.getConstant();
            if ((format != null) && !format.contains("%")) {
                bugReporter.reportBug(
                        new BugInstance(this, BugType.SPP_STATIC_FORMAT_STRING.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
            }
        } else if ("org/apache/commons/lang3/builder/ToStringBuilder".equals(className)
                || "org/apache/commons/lang/builder/ToStringBuilder".equals(className)) {
            if ("reflectionToString".equals(methodName) && SignatureBuilder.SIG_OBJECT_TO_STRING.equals(getSigConstantOperand())) {
                if (stack.getStackDepth() >= 1) {
                    OpcodeStack.Item itm = stack.getStackItem(0);
                    String toStringSig = itm.getSignature();
                    if ((toStringSig != null) && toStringSig.contains("/ToStringStyle")) {
                        bugReporter.reportBug(new BugInstance(this, BugType.SPP_WRONG_COMMONS_TO_STRING_OBJECT.name(), NORMAL_PRIORITY).addClass(this)
                                .addMethod(this).addSourceLine(this));
                    }
                }
            }
        }
        return userValue;
    }

    private void checkForArrayParameter(OpcodeStack.Item item) {
        String sig = item.getSignature();
        if (!sig.startsWith(Values.SIG_ARRAY_PREFIX) && !Values.SIG_JAVA_LANG_OBJECT.equals(sig)) {
            bugReporter.reportBug(new BugInstance(this, BugType.SPP_NON_ARRAY_PARM.name(), HIGH_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
        }
    }

    @Nullable
    private SPPUserValue sawInvokeVirtual() throws ClassNotFoundException {
        String className = getClassConstantOperand();
        String methodName = getNameConstantOperand();
        if ("java/util/BitSet".equals(className)) {
            bitSetSilliness(methodName);
        } else if (SignatureUtils.isPlainStringConvertableClass(className)) {
            return stringBufferSilliness(methodName);
        } else if (Values.SLASHED_JAVA_LANG_STRING.equals(className)) {
            return stringSilliness(methodName, getSigConstantOperand());
        } else if ("equals".equals(methodName) && SignatureBuilder.SIG_OBJECT_TO_BOOLEAN.equals(getSigConstantOperand())) {
            equalsSilliness(className);
        } else if ("java/lang/Boolean".equals(className) && "booleanValue".equals(methodName)) {
            booleanSilliness();
        } else if (("java/util/GregorianCalendar".equals(className) || "java/util/Calendar".equals(className))
                && ("after".equals(methodName) || "before".equals(methodName))) {
            calendarBeforeAfterSilliness();
        } else if ("java/util/Properties".equals(className)) {
            propertiesSilliness(methodName);
        } else if (Values.TOSTRING.equals(methodName) && Values.SLASHED_JAVA_LANG_OBJECT.equals(className)) {
            defaultToStringSilliness();
        }
        return null;
    }

    private void bitSetSilliness(String methodName) {
        if (("clear".equals(methodName) || "flip".equals(methodName) || "get".equals(methodName) || "set".equals(methodName)) && (stack.getStackDepth() > 0)) {
            OpcodeStack.Item item = stack.getStackItem(0);
            Object o = item.getConstant();
            if ((o instanceof Integer) && (((Integer) o).intValue() < 0)) {
                bugReporter.reportBug(
                        new BugInstance(this, BugType.SPP_NEGATIVE_BITSET_ITEM.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
            }
        }
    }

    @Nullable
    private SPPUserValue stringBufferSilliness(String methodName) {
        if ("append".equals(methodName) && (stack.getStackDepth() > 1)) {
            OpcodeStack.Item valItem = stack.getStackItem(0);
            OpcodeStack.Item sbItem = stack.getStackItem(1);
            Object constant = valItem.getConstant();
            boolean argIsLiteralString = (constant instanceof String) && (((String) constant).length() > 0);
            argIsLiteralString = argIsLiteralString && !looksLikeStaticFieldValue((String) constant);

            if (argIsLiteralString) {
                SPPUserValue uv = (SPPUserValue) sbItem.getUserValue();
                if ((uv != null) && (uv.getMethod() == SPPMethod.APPEND)) {
                    Matcher m = APPEND_PATTERN.matcher(uv.getDetails());
                    if (m.matches() && LITERAL.equals(m.group(2))) {
                        bugReporter.reportBug(new BugInstance(this, BugType.SPP_DOUBLE_APPENDED_LITERALS.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                                .addSourceLine(this));
                        argIsLiteralString = false;
                    }
                }
            }

            String literal = argIsLiteralString ? LITERAL : "";

            SPPUserValue userValue = null;
            int registerNumber = sbItem.getRegisterNumber();
            if (registerNumber > -1) {
                userValue = new SPPUserValue(SPPMethod.APPEND, registerNumber + ':' + literal);
            } else {
                userValue = (SPPUserValue) sbItem.getUserValue();
                if ((userValue != null) && (userValue.getMethod() == SPPMethod.APPEND)) {
                    Matcher m = APPEND_PATTERN.matcher(userValue.getDetails());
                    if (m.matches()) {
                        userValue = new SPPUserValue(SPPMethod.APPEND, m.group(1) + ':' + literal);
                    }
                }
            }
            return userValue;
        }
        return null;
    }

    private SPPUserValue stringSilliness(String methodName, String signature) {

        Integer stackOffset = methodsThatAreSillyOnStringLiterals.get(new QMethod(methodName, signature));
        int offset;
        if ((stackOffset != null) && (stack.getStackDepth() > (offset = stackOffset.intValue()))) {
            OpcodeStack.Item itm = stack.getStackItem(offset);
            Object constant = itm.getConstant();
            if ((constant != null) && constant.getClass().equals(String.class) && (itm.getXField() == null)) {
                int priority = NORMAL_PRIORITY;
                if (SignatureUtils.getNumParameters(getSigConstantOperand()) > 0) {
                    // if an argument is passed in, it may be
                    // locale-specific
                    priority = LOW_PRIORITY;
                }
                bugReporter.reportBug(new BugInstance(this, BugType.SPP_CONVERSION_OF_STRING_LITERAL.name(), priority).addClass(this).addMethod(this)
                        .addSourceLine(this).addCalledMethod(this));
            }
        }
        // not an elseif because the below cases might be in the set methodsThatAreSillyOnStringLiterals
        SPPUserValue userValue = null;

        if ("intern".equals(methodName)) {
            String owningMethod = getMethod().getName();
            if (!Values.STATIC_INITIALIZER.equals(owningMethod) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item item = stack.getStackItem(0);
                if (item.getConstant() != null) {
                    bugReporter.reportBug(
                            new BugInstance(this, BugType.SPP_INTERN_ON_CONSTANT.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
                }
            }
        } else if ("toCharArray".equals(methodName)) {
            userValue = new SPPUserValue(SPPMethod.TOCHARARRAY);
        } else if ("toLowerCase".equals(methodName) || "toUpperCase".equals(methodName)) {
            userValue = new SPPUserValue(SPPMethod.IGNORECASE);
        } else if ("equalsIgnoreCase".equals(methodName) || "compareToIgnoreCase".equals(methodName)) {
            if (stack.getStackDepth() > 1) {
                OpcodeStack.Item item = stack.getStackItem(1);
                SPPUserValue uv = (SPPUserValue) item.getUserValue();

                if ((uv != null) && (uv.getMethod() == SPPMethod.IGNORECASE)) {
                    bugReporter.reportBug(
                            new BugInstance(this, BugType.SPP_USELESS_CASING.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
                }
                item = stack.getStackItem(0);
                String parm = (String) item.getConstant();
                if ("".equals(parm)) {
                    bugReporter.reportBug(
                            new BugInstance(this, BugType.SPP_EMPTY_CASING.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
                }
            }
        } else if ("trim".equals(methodName)) {
            userValue = getTrimUserValue();
        } else if ("length".equals(methodName)) {
            if (stack.getStackDepth() > 0) {
                checkForTrim(stack.getStackItem(0));
            }
        } else if ("equals".equals(methodName)) {
            if (stack.getStackDepth() > 1) {
                checkForTrim(stack.getStackItem(1));
            }
        } else if (Values.TOSTRING.equals(methodName)) {
            bugReporter.reportBug(
                    new BugInstance(this, BugType.SPP_TOSTRING_ON_STRING.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
        }
        return userValue;
    }

    private void checkForTrim(OpcodeStack.Item item) {
        SPPUserValue uv = (SPPUserValue) item.getUserValue();
        if ((uv != null) && (uv.getMethod() == SPPMethod.TRIM)) {
            if (uv.getDetails() == null) {
                bugReporter.reportBug(
                        new BugInstance(this, BugType.SPP_TEMPORARY_TRIM.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
            } else {
                trimLocations.put(uv, Integer.valueOf(getPC()));
            }
        }
    }

    private void equalsSilliness(String className) {
        try {
            JavaClass cls = Repository.lookupClass(className);
            if (cls.isEnum()) {
                bugReporter.reportBug(
                        new BugInstance(this, BugType.SPP_EQUALS_ON_ENUM.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
            } else {
                if (stack.getStackDepth() >= 2) {
                    OpcodeStack.Item item = stack.getStackItem(1);
                    cls = item.getJavaClass();
                    if (cls != null) {
                        String clsName = cls.getClassName();
                        if (oddMissingEqualsClasses.contains(clsName)) {
                            bugReporter.reportBug(new BugInstance(this, BugType.SPP_EQUALS_ON_STRING_BUILDER.name(), NORMAL_PRIORITY).addClass(this)
                                    .addMethod(this).addSourceLine(this));
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
                        if (bytes[start1 + i] != bytes[start2 + i]) {
                            found = false;
                            break;
                        }
                    }

                    if (found) {
                        bugReporter.reportBug(new BugInstance(this, BugType.SPP_INVALID_BOOLEAN_NULL_CHECK.name(), NORMAL_PRIORITY).addClass(this)
                                .addMethod(this).addSourceLine(this));
                    }
                }
            }
        }
    }

    private void calendarBeforeAfterSilliness() {
        if (stack.getStackDepth() > 1) {
            OpcodeStack.Item item = stack.getStackItem(0);
            String itemSig = item.getSignature();
            // Rule out java.lang.Object as mergeJumps can throw away type info
            // (BUG)
            if (!Values.SIG_JAVA_LANG_OBJECT.equals(itemSig) && !"Ljava/util/Calendar;".equals(itemSig) && !"Ljava/util/GregorianCalendar;".equals(itemSig)) {
                try {
                    JavaClass cls = Repository.lookupClass(SignatureUtils.stripSignature(itemSig));
                    if (!cls.instanceOf(calendarClass)) {
                        bugReporter.reportBug(new BugInstance(this, BugType.SPP_INVALID_CALENDAR_COMPARE.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
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
                if (!toStringClass.isInterface() && !toStringClass.isAbstract() && !Values.DOTTED_JAVA_LANG_OBJECT.equals(toStringClassName)
                        && !Values.DOTTED_JAVA_LANG_STRING.equals(toStringClassName) && toStringClasses.add(toStringClassName)) {
                    try {
                        JavaClass cls = Repository.lookupClass(toStringClassName);

                        if (!hasToString(cls)) {
                            bugReporter.reportBug(
                                    new BugInstance(this, BugType.SPP_NON_USEFUL_TOSTRING.name(), toStringClass.isFinal() ? NORMAL_PRIORITY : LOW_PRIORITY)
                                            .addClass(this).addMethod(this).addSourceLine(this));
                        }
                    } catch (ClassNotFoundException cnfe) {
                        bugReporter.reportMissingClass(cnfe);
                    }
                }
            }
        }
    }

    private void propertiesSilliness(String methodName) {
        if (("get".equals(methodName) || "getProperty".equals(methodName)) && (stack.getStackDepth() > 1)) {
            OpcodeStack.Item item = stack.getStackItem(1);
            SPPUserValue uv = (SPPUserValue) item.getUserValue();
            if ((uv != null) && (uv.getMethod() == SPPMethod.GETPROPERTIES)) {
                bugReporter.reportBug(
                        new BugInstance(this, BugType.SPP_USE_GETPROPERTY.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
            }
        }
    }

    private SPPUserValue sawInvokeInterface() {
        SPPUserValue userValue = null;
        String className = getClassConstantOperand();

        if (Values.SLASHED_JAVA_UTIL_MAP.equals(className)) {
            String method = getNameConstantOperand();
            if ("keySet".equals(method)) {
                userValue = new SPPUserValue(SPPMethod.KEYSET);
            }
        } else if (Values.SLASHED_JAVA_UTIL_SET.equals(className)) {
            String method = getNameConstantOperand();
            if ("contains".equals(method) && (stack.getStackDepth() >= 2)) {
                OpcodeStack.Item item = stack.getStackItem(1);
                SPPUserValue uv = (SPPUserValue) item.getUserValue();
                if ((uv != null) && (uv.getMethod() == SPPMethod.KEYSET)) {
                    bugReporter.reportBug(
                            new BugInstance(this, BugType.SPP_USE_CONTAINSKEY.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
                }
            }
        } else if (Values.SLASHED_JAVA_UTIL_LIST.equals(className)) {
            String method = getNameConstantOperand();
            if ("iterator".equals(method)) {
                userValue = new SPPUserValue(SPPMethod.ITERATOR);
            }
        } else if ("java/util/Iterator".equals(className)) {
            String method = getNameConstantOperand();
            if ("next".equals(method) && (stack.getStackDepth() >= 1)) {
                OpcodeStack.Item item = stack.getStackItem(0);
                SPPUserValue uv = (SPPUserValue) item.getUserValue();
                if ((uv != null) && (uv.getMethod() == SPPMethod.ITERATOR)) {
                    bugReporter
                            .reportBug(new BugInstance(this, BugType.SPP_USE_GET0.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
                }
            }
        }

        if (collectionInterfaces.contains(className)) {
            String method = getNameConstantOperand();
            if ("size".equals(method)) {
                if (!OpcodeUtils.isIStore(getNextOpcode())) {
                    userValue = new SPPUserValue(SPPMethod.SIZE);
                }
            }
        }
        return userValue;
    }

    private void sawInvokeSpecial() {
        String className = getClassConstantOperand();
        if (SignatureUtils.isPlainStringConvertableClass(className)) {
            String methodName = getNameConstantOperand();
            if (Values.CONSTRUCTOR.equals(methodName)) {
                String signature = getSigConstantOperand();
                if (SignatureBuilder.SIG_INT_TO_VOID.equals(signature)) {
                    if ((lastOpcode == BIPUSH) && (stack.getStackDepth() > 0)) {
                        OpcodeStack.Item item = stack.getStackItem(0);
                        Object o = item.getConstant();
                        if (o instanceof Integer) {
                            int parm = ((Integer) o).intValue();
                            if ((parm > 32) && (parm < 127) && (parm != 64) && (parm != 48) && ((parm % 5) != 0)) {
                                bugReporter.reportBug(new BugInstance(this, BugType.SPP_NO_CHAR_SB_CTOR.name(), LOW_PRIORITY).addClass(this).addMethod(this)
                                        .addSourceLine(this));
                            }
                        }
                    }
                } else if (SignatureBuilder.SIG_STRING_TO_VOID.equals(signature) && (stack.getStackDepth() > 0)) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    String con = (String) item.getConstant();
                    if ("".equals(con)) {
                        bugReporter.reportBug(new BugInstance(this, BugType.SPP_STRINGBUFFER_WITH_EMPTY_STRING.name(), NORMAL_PRIORITY).addClass(this)
                                .addMethod(this).addSourceLine(this));
                    }
                }
            }
        } else if ("java/math/BigDecimal".equals(className) && (stack.getStackDepth() > 0)) {
            OpcodeStack.Item item = stack.getStackItem(0);
            Object constant = item.getConstant();
            if (constant instanceof Double) {
                double v = ((Double) constant).doubleValue();
                if ((v != 0.0) && (v != 1.0)) {
                    bugReporter.reportBug(new BugInstance(this, BugType.SPP_USE_BIGDECIMAL_STRING_CTOR.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                            .addSourceLine(this));
                }
            }
        }
    }

    private boolean looksLikeStaticFieldValue(String constant) {
        if (staticConstants == null) {
            staticConstants = new HashSet<>();

            Field[] fields = getClassContext().getJavaClass().getFields();
            for (Field f : fields) {
                if (((f.getAccessFlags() & (Constants.ACC_FINAL | Constants.ACC_STATIC)) == (Constants.ACC_FINAL | Constants.ACC_STATIC))
                        && Values.SIG_JAVA_LANG_STRING.equals(f.getSignature())) {
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

    private boolean hasToString(JavaClass cls) throws ClassNotFoundException {
        if (Values.DOTTED_JAVA_LANG_OBJECT.equals(cls.getClassName())) {
            return false;
        }
        for (Method m : cls.getMethods()) {
            if (Values.TOSTRING.equals(m.getName()) && SignatureBuilder.SIG_VOID_TO_STRING.equals(m.getSignature())) {
                return true;
            }
        }
        return hasToString(cls.getSuperClass());
    }

    @Nullable
    private SPPUserValue getTrimUserValue() {
        if (stack.getStackDepth() == 0) {
            return null;
        }

        OpcodeStack.Item item = stack.getStackItem(0);

        int reg = item.getRegisterNumber();
        if (reg >= 0) {
            return new SPPUserValue(SPPMethod.TRIM, String.valueOf(reg));
        }

        XField field = item.getXField();
        if (field != null) {
            return new SPPUserValue(SPPMethod.TRIM, field.getName());
        }

        XMethod method = item.getReturnValueOf();
        if (method != null) {
            return new SPPUserValue(SPPMethod.TRIM, method.getName());
        }

        return new SPPUserValue(SPPMethod.TRIM);
    }

    private void checkTrimLocations() {
        if (trimLocations.isEmpty()) {
            return;
        }

        SPPUserValue curV = getTrimUserValue();
        if (curV == null) {
            return;
        }

        Integer pc = trimLocations.remove(curV);
        if (pc != null) {
            bugReporter.reportBug(new BugInstance(this, BugType.SPP_TEMPORARY_TRIM.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this,
                    pc.intValue()));
        }
    }

    enum SPPMethod {
        APPEND, GETPROPERTIES, ICONST, IGNORECASE, ITERATOR, KEYSET, TOCHARARRAY, SIZE, TRIM
    }

    static class SPPUserValue {

        private SPPMethod method;
        private String details;

        public SPPUserValue(SPPMethod mthd) {
            this(mthd, null);
        }

        public SPPUserValue(SPPMethod mthd, String detail) {
            method = mthd;
            details = detail;
        }

        public SPPMethod getMethod() {
            return method;
        }

        public String getDetails() {
            return details;
        }

        @Override
        public int hashCode() {
            return method.hashCode() ^ ((details == null) ? 0 : details.hashCode());
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SPPUserValue)) {
                return false;
            }

            SPPUserValue that = (SPPUserValue) o;

            if (method != that.method) {
                return false;
            }

            if (details == null) {
                return that.details == null;
            }

            return details.equals(that.details);
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }
}
