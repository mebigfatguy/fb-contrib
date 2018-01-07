/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2018 Dave Brosius
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.bcel.Constants;
import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.CodeException;
import org.apache.bcel.classfile.ConstantClass;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.ExceptionTable;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.LocalVariable;
import org.apache.bcel.classfile.LocalVariableTable;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for methods that catch checked exceptions, and throw unchecked exceptions in their place. There are several levels of concern. Least important are
 * methods constrained by interface or super class contracts not to throw checked exceptions but appear owned by the same author. Next are methods constrained
 * by interface or super class contracts and throw other types of checked exceptions. Lastly are method not constrained by any interface or superclass contract.
 */
public class ExceptionSoftening extends BytecodeScanningDetector {

    private final BugReporter bugReporter;
    private JavaClass runtimeClass;
    private OpcodeStack stack;
    private Map<Integer, CodeException> catchHandlerPCs;
    private List<CatchInfo> catchInfos;
    private LocalVariableTable lvt;
    private Map<String, Set<String>> constrainingInfo;
    private boolean isBooleanMethod;
    private boolean hasValidFalseReturn;
    private int catchFalseReturnPC;

    /**
     * constructs a EXS detector given the reporter to report bugs on.
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public ExceptionSoftening(BugReporter bugReporter) {
        this.bugReporter = bugReporter;

        try {
            runtimeClass = Repository.lookupClass(Values.SLASHED_JAVA_LANG_RUNTIMEEXCEPTION);
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        }
    }

    /**
     * overrides the visitor to reset the stack
     *
     * @param classContext
     *            the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            if (runtimeClass != null) {
                stack = new OpcodeStack();
                super.visitClassContext(classContext);
            }
        } finally {
            stack = null;
        }
    }

    /**
     * overrides the visitor to look for methods that catch checked exceptions and rethrow runtime exceptions
     *
     * @param obj
     *            the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        try {
            Method method = getMethod();
            if (method.isSynthetic()) {
                return;
            }

            isBooleanMethod = Type.BOOLEAN.equals(method.getReturnType());
            if (isBooleanMethod || prescreen(method)) {
                catchHandlerPCs = collectExceptions(obj.getExceptionTable());
                if (!catchHandlerPCs.isEmpty()) {
                    stack.resetForMethodEntry(this);
                    catchInfos = new ArrayList<>();
                    lvt = method.getLocalVariableTable();
                    constrainingInfo = null;
                    hasValidFalseReturn = false;
                    catchFalseReturnPC = -1;
                    super.visitCode(obj);

                    if (!hasValidFalseReturn && (catchFalseReturnPC >= 0) && !method.getName().startsWith("is")) {
                        bugReporter.reportBug(new BugInstance(this, BugType.EXS_EXCEPTION_SOFTENING_RETURN_FALSE.name(), NORMAL_PRIORITY).addClass(this)
                                .addMethod(this).addSourceLine(this, catchFalseReturnPC));
                    }
                }
            }
        } finally {
            catchInfos = null;
            catchHandlerPCs = null;
            lvt = null;
            constrainingInfo = null;
        }
    }

    /**
     * overrides the visitor to find catch blocks that throw runtime exceptions
     *
     * @param seen
     *            the opcode of the currently parsed instruction
     */
    @Override
    public void sawOpcode(int seen) {
        try {
            stack.precomputation(this);

            int pc = getPC();
            CodeException ex = catchHandlerPCs.get(Integer.valueOf(pc));
            if (ex != null) {
                int endPC;
                if ((seen == GOTO) || (seen == GOTO_W)) {
                    endPC = this.getBranchTarget();
                } else {
                    endPC = Integer.MAX_VALUE;
                }
                ConstantPool pool = getConstantPool();
                ConstantClass ccls = (ConstantClass) pool.getConstant(ex.getCatchType());
                String catchSig = ccls.getBytes(pool);
                CatchInfo ci = new CatchInfo(ex.getHandlerPC(), endPC, catchSig);
                catchInfos.add(ci);
            }

            updateEndPCsOnCatchRegScope(catchInfos, pc, seen);
            removeFinishedCatchBlocks(catchInfos, pc);

            if (seen == ATHROW) {
                processThrow();
            } else if ((seen == IRETURN) && isBooleanMethod && !hasValidFalseReturn && (stack.getStackDepth() > 0)) {
                processBooleanReturn();
            }

        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    private void processThrow() {
        try {
            if (!catchInfos.isEmpty()) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item itm = stack.getStackItem(0);
                    JavaClass exClass = itm.getJavaClass();
                    if ((exClass != null) && exClass.instanceOf(runtimeClass)) {
                        Set<String> possibleCatchSignatures = findPossibleCatchSignatures(catchInfos, getPC());
                        if (!possibleCatchSignatures.contains(exClass.getClassName())) {
                            boolean anyRuntimes = false;
                            for (String possibleCatches : possibleCatchSignatures) {
                                exClass = Repository.lookupClass(possibleCatches);
                                if (exClass.instanceOf(runtimeClass)) {
                                    anyRuntimes = true;
                                    break;
                                }
                            }

                            if (!anyRuntimes) {

                                if (constrainingInfo == null) {
                                    constrainingInfo = getConstrainingInfo(getClassContext().getJavaClass(), getMethod());
                                }

                                BugType bug = null;
                                int priority = NORMAL_PRIORITY;

                                if (constrainingInfo == null) {
                                    bug = BugType.EXS_EXCEPTION_SOFTENING_NO_CONSTRAINTS;
                                    priority = HIGH_PRIORITY;
                                } else if (!constrainingInfo.values().iterator().next().isEmpty()) {
                                    bug = BugType.EXS_EXCEPTION_SOFTENING_HAS_CHECKED;
                                    priority = NORMAL_PRIORITY;
                                } else {
                                    String pack1 = constrainingInfo.keySet().iterator().next();
                                    String pack2 = getClassContext().getJavaClass().getClassName();
                                    int dotPos = pack1.lastIndexOf('.');
                                    if (dotPos >= 0) {
                                        pack1 = pack1.substring(0, dotPos);
                                    } else {
                                        pack1 = "";
                                    }
                                    dotPos = pack2.lastIndexOf('.');
                                    if (dotPos >= 0) {
                                        pack2 = pack2.substring(0, dotPos);
                                    } else {
                                        pack2 = "";
                                    }
                                    if (SignatureUtils.similarPackages(pack1, pack2, 2)) {
                                        bug = BugType.EXS_EXCEPTION_SOFTENING_NO_CHECKED;
                                        priority = NORMAL_PRIORITY;
                                    }
                                }

                                if (bug != null) {
                                    bugReporter.reportBug(new BugInstance(this, bug.name(), priority).addClass(this).addMethod(this).addSourceLine(this));
                                }
                            }
                        }
                    }
                }
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        }
    }

    private void processBooleanReturn() {
        OpcodeStack.Item item = stack.getStackItem(0);
        Integer returnVal = (Integer) item.getConstant();
        if (returnVal == null) {
            hasValidFalseReturn = true;
        } else if ((catchFalseReturnPC < 0) && (returnVal.intValue() == 0)) {
            int pc = getPC();
            Set<String> sigs = findPossibleCatchSignatures(catchInfos, pc);
            for (String sig : sigs) {
                if (!sig.isEmpty()) {
                    catchFalseReturnPC = pc;
                    break;
                }
            }
            if (catchFalseReturnPC < 0) {
                hasValidFalseReturn = true;
            }
        }
    }

    /**
     * collects all the valid exception objects (ones where start and finish are before the target) and with a catch type
     *
     * @param exceptions
     *            the exceptions from the class file
     * @return the filtered exceptions keyed by catch end pc
     */
    private static LinkedHashMap<Integer, CodeException> collectExceptions(CodeException... exceptions) {
        List<CodeException> filteredEx = new ArrayList<>();
        for (CodeException ce : exceptions) {
            if ((ce.getCatchType() != 0) && (ce.getStartPC() < ce.getEndPC()) && (ce.getEndPC() <= ce.getHandlerPC())) {
                filteredEx.add(ce);
            }
        }

        LinkedHashMap<Integer, CodeException> handlers = new LinkedHashMap<>();

        for (CodeException ex : filteredEx) {
            handlers.put(Integer.valueOf(ex.getEndPC()), ex);
        }

        return handlers;
    }

    /**
     * remove catchinfo blocks from the map where the handler end is before the current pc
     *
     * @param infos
     *            the exception handlers installed
     * @param pc
     *            the current pc
     */
    private static void removeFinishedCatchBlocks(List<CatchInfo> infos, int pc) {
        Iterator<CatchInfo> it = infos.iterator();
        while (it.hasNext()) {
            if (it.next().getFinish() < pc) {
                it.remove();
            }
        }
    }

    /**
     * reduces the end pc based on the optional LocalVariableTable's exception register scope
     *
     * @param infos
     *            the list of active catch blocks
     * @param pc
     *            the current pc
     * @param seen
     *            the currently parsed opcode
     */
    private void updateEndPCsOnCatchRegScope(List<CatchInfo> infos, int pc, int seen) {
        if (lvt != null) {
            for (CatchInfo ci : infos) {
                if ((ci.getStart() == pc) && OpcodeUtils.isAStore(seen)) {
                    int exReg = RegisterUtils.getAStoreReg(this, seen);
                    LocalVariable lv = lvt.getLocalVariable(exReg, pc + 1);
                    if (lv != null) {
                        ci.setFinish(lv.getStartPC() + lv.getLength());
                    }
                    break;
                }
            }
        }
    }

    /**
     * returns an array of catch types that the current pc is in
     *
     * @param infos
     *            the list of catch infos for this method
     * @param pc
     *            the current pc
     * @return an set of catch exception types that the pc is currently in
     */
    private static Set<String> findPossibleCatchSignatures(List<CatchInfo> infos, int pc) {
        Set<String> catchTypes = new HashSet<>(6);
        ListIterator<CatchInfo> it = infos.listIterator(infos.size());
        while (it.hasPrevious()) {
            CatchInfo ci = it.previous();
            if ((pc >= ci.getStart()) && (pc < ci.getFinish())) {
                catchTypes.add(ci.getSignature());
            } else {
                break;
            }
        }

        return catchTypes;
    }

    /**
     * finds the super class or interface that constrains the types of exceptions that can be thrown from the given method
     *
     * @param cls
     *            the currently parsed class
     * @param m
     *            the method to check
     * @return a map containing the class name to a set of exceptions that constrain this method
     *
     * @throws ClassNotFoundException
     *             if a super class or super interface can't be loaded from the repository
     */
    @Nullable
    private Map<String, Set<String>> getConstrainingInfo(JavaClass cls, Method m) throws ClassNotFoundException {
        String methodName = m.getName();
        String methodSig = m.getSignature();

        {
            // First look for the method in interfaces of the class
            JavaClass[] infClasses = cls.getInterfaces();

            for (JavaClass infCls : infClasses) {
                Method infMethod = findMethod(infCls, methodName, methodSig);
                if (infMethod != null) {
                    return buildConstrainingInfo(infCls, infMethod);
                }

                Map<String, Set<String>> constrainingExs = getConstrainingInfo(infCls, m);
                if (constrainingExs != null) {
                    return constrainingExs;
                }
            }
        }

        {
            // Next look at the superclass
            JavaClass superCls = cls.getSuperClass();
            if (superCls == null) {
                return null;
            }

            Method superMethod = findMethod(superCls, methodName, methodSig);
            if (superMethod != null) {
                return buildConstrainingInfo(superCls, superMethod);
            }

            // Otherwise recursively call this on the super class
            return getConstrainingInfo(superCls, m);

        }
    }

    /**
     * finds a method that matches the name and signature in the given class
     *
     * @param cls
     *            the class to look in
     * @param methodName
     *            the name to look for
     * @param methodSig
     *            the signature to look for
     *
     * @return the method or null
     */
    @Nullable
    private static Method findMethod(JavaClass cls, String methodName, String methodSig) {
        Method[] methods = cls.getMethods();
        for (Method method : methods) {
            if (method.getName().equals(methodName) && method.getSignature().equals(methodSig)) {
                return method;
            }
        }
        return null;
    }

    /**
     * returns exception names describing what exceptions are allowed to be thrown
     *
     * @param cls
     *            the cls to find the exceptions in
     * @param m
     *            the method to add exceptions from
     * @return a map with one entry of a class name to a set of exceptions that constrain what can be thrown.
     *
     * @throws ClassNotFoundException
     *             if an exception class can't be loaded from the repository
     */
    private Map<String, Set<String>> buildConstrainingInfo(JavaClass cls, Method m) throws ClassNotFoundException {
        Map<String, Set<String>> constraintInfo = new HashMap<>();
        Set<String> exs = new HashSet<>();
        ExceptionTable et = m.getExceptionTable();
        if (et != null) {
            int[] indexTable = et.getExceptionIndexTable();
            ConstantPool pool = cls.getConstantPool();
            for (int index : indexTable) {
                if (index != 0) {
                    ConstantClass ccls = (ConstantClass) pool.getConstant(index);
                    String exName = ccls.getBytes(pool);
                    JavaClass exClass = Repository.lookupClass(exName);
                    if (!exClass.instanceOf(runtimeClass)) {
                        exs.add(ccls.getBytes(pool));
                    }
                }
            }
        }
        constraintInfo.put(cls.getClassName(), exs);
        return constraintInfo;
    }

    /**
     * returns whether a method explicitly throws an exception
     *
     * @param method
     *            the currently parsed method
     * @return if the method throws an exception
     */
    private boolean prescreen(Method method) {
        BitSet bytecodeSet = getClassContext().getBytecodeSet(method);
        return (bytecodeSet != null) && (bytecodeSet.get(Constants.ATHROW));
    }

    /**
     * holds information about a catch block the start and end pcs, as well as the exception signature. you can't always determine the end of a catch block, and
     * in this case the value will be Integer.MAX_VALUE
     */
    private static class CatchInfo {
        private final int catchStart;
        private int catchFinish;
        private final String catchSignature;

        public CatchInfo(int start, int finish, String signature) {
            catchStart = start;
            catchFinish = finish;
            catchSignature = signature;
        }

        public int getStart() {
            return catchStart;
        }

        public void setFinish(int finish) {
            catchFinish = finish;
        }

        public int getFinish() {
            return catchFinish;
        }

        public String getSignature() {
            return catchSignature;
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }
}
