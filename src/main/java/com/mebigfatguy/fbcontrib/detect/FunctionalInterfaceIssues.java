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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Attribute;
import org.apache.bcel.classfile.BootstrapMethod;
import org.apache.bcel.classfile.BootstrapMethods;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantCP;
import org.apache.bcel.classfile.ConstantInvokeDynamic;
import org.apache.bcel.classfile.ConstantMethodHandle;
import org.apache.bcel.classfile.ConstantNameAndType;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.QMethod;
import com.mebigfatguy.fbcontrib.utils.StopOpcodeParsingException;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.SourceLineAnnotation;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for issues around use of @FunctionalInterface classes, especially in use with Streams..
 */
@CustomUserValue
public class FunctionalInterfaceIssues extends BytecodeScanningDetector {

    private static final QMethod CONTAINS = new QMethod("contains", "(Ljava/lang/Object;)Z");

    private static final FQMethod COLLECT = new FQMethod("java/util/stream/Stream", "collect", "(Ljava/util/stream/Collector;)Ljava/lang/Object;");
    private static final FQMethod FILTER = new FQMethod("java/util/stream/Stream", "filter", "(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;");
    private static final FQMethod FINDFIRST = new FQMethod("java/util/stream/Stream", "findFirst", "()Ljava/util/Optional;");
    private static final FQMethod ISPRESENT = new FQMethod("java/util/Optional", "isPresent", "()Z");
    private static final FQMethod GET = new FQMethod("java/util/List", "get", "(I)Ljava/lang/Object;");

    enum ParseState {
        NORMAL, LAMBDA;
    }

    enum AnonState {
        SEEN_NOTHING, SEEN_ALOAD_0, SEEN_INVOKE
    }

    enum FIIUserValue {
        COLLECT, FILTER, FINDFIRST;
    }

    private BugReporter bugReporter;
    private JavaClass cls;
    private OpcodeStack stack;
    private BootstrapMethods bootstrapAtt;
    private Map<String, List<FIInfo>> functionalInterfaceInfo;

    private ParseState parseState;
    private AnonState anonState;

    public FunctionalInterfaceIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            cls = classContext.getJavaClass();
            if (cls.getMajor() >= Const.MAJOR_1_8) {
                bootstrapAtt = getBootstrapAttribute(cls);
                if (bootstrapAtt != null) {
                    stack = new OpcodeStack();
                    functionalInterfaceInfo = new HashMap<>();
                    parseState = ParseState.NORMAL;
                    super.visitClassContext(classContext);
                    parseState = ParseState.LAMBDA;
                    super.visitClassContext(classContext);

                    for (Map.Entry<String, List<FIInfo>> entry : functionalInterfaceInfo.entrySet()) {
                        for (FIInfo fii : entry.getValue()) {
                            bugReporter.reportBug(new BugInstance(this, BugType.FII_USE_METHOD_REFERENCE.name(), NORMAL_PRIORITY).addClass(this)
                                    .addMethod(cls, fii.getMethod()).addSourceLine(fii.getSrcLine()));
                        }
                    }
                }
            }
        } finally {
            functionalInterfaceInfo = null;
            bootstrapAtt = null;
            stack = null;
            cls = null;
        }
    }

    @Override
    public void visitCode(Code obj) {

        Method m = getMethod();
        switch (parseState) {
            case LAMBDA:
                if ((m.getAccessFlags() & Const.ACC_SYNTHETIC) != 0) {
                    List<FIInfo> fiis = functionalInterfaceInfo.get(m.getName());
                    if (fiis != null) {
                        try {
                            anonState = AnonState.SEEN_NOTHING;
                            super.visitCode(obj);
                        } catch (StopOpcodeParsingException e) {
                        }
                    }
                }
            break;

            case NORMAL:
                if ((m.getAccessFlags() & Const.ACC_SYNTHETIC) == 0) {
                    stack.resetForMethodEntry(this);
                    super.visitCode(obj);
                    break;
                }
        }
    }

    @Override
    public void sawOpcode(int seen) {
        FIIUserValue userValue = null;

        try {
            if (parseState == ParseState.LAMBDA) {
                switch (anonState) {
                    case SEEN_NOTHING:
                        if (seen == Const.ALOAD_0) {
                            anonState = AnonState.SEEN_ALOAD_0;
                        } else {
                            functionalInterfaceInfo.remove(getMethod().getName());
                            throw new StopOpcodeParsingException();
                        }
                    break;

                    case SEEN_ALOAD_0:
                        if ((seen == Const.INVOKEVIRTUAL) || (seen == Const.INVOKEINTERFACE)) {
                            String signature = getSigConstantOperand();
                            if (signature.startsWith("()")) {
                                anonState = AnonState.SEEN_INVOKE;
                            } else {
                                functionalInterfaceInfo.remove(getMethod().getName());
                                throw new StopOpcodeParsingException();
                            }
                        } else {
                            functionalInterfaceInfo.remove(getMethod().getName());
                            throw new StopOpcodeParsingException();
                        }
                    break;

                    case SEEN_INVOKE:
                        if (!OpcodeUtils.isReturn(seen)) {
                            functionalInterfaceInfo.remove(getMethod().getName());
                        }
                        throw new StopOpcodeParsingException();

                    default:
                        functionalInterfaceInfo.remove(getMethod().getName());
                        throw new StopOpcodeParsingException();
                }
            } else {
                switch (seen) {
                    case Const.INVOKEDYNAMIC:
                        ConstantInvokeDynamic cid = (ConstantInvokeDynamic) getConstantRefOperand();

                        ConstantMethodHandle cmh = getMethodHandle(cid.getBootstrapMethodAttrIndex());
                        String anonName = getAnonymousName(cmh);
                        if (anonName != null) {

                            List<FIInfo> fiis = functionalInterfaceInfo.get(anonName);
                            if (fiis == null) {
                                fiis = new ArrayList<>();
                                functionalInterfaceInfo.put(anonName, fiis);
                            }

                            FIInfo fii = new FIInfo(getMethod(), SourceLineAnnotation.fromVisitedInstruction(this));
                            fiis.add(fii);
                        }
                    break;

                    case Const.INVOKEINTERFACE:
                        QMethod m = new QMethod(getNameConstantOperand(), getSigConstantOperand());

                        if (CONTAINS.equals(m)) {
                            if (stack.getStackDepth() >= 2) {
                                OpcodeStack.Item itm = stack.getStackItem(1);
                                if ((itm.getRegisterNumber() < 0) && (FIIUserValue.COLLECT == itm.getUserValue())) {
                                    bugReporter.reportBug(new BugInstance(this, BugType.FII_AVOID_CONTAINS_ON_COLLECTED_STREAM.name(), NORMAL_PRIORITY)
                                            .addClass(this).addMethod(this).addSourceLine(this));
                                }
                            }
                        } else {
                            FQMethod fqm = new FQMethod(getClassConstantOperand(), getNameConstantOperand(), getSigConstantOperand());
                            if (COLLECT.equals(fqm)) {
                                userValue = FIIUserValue.COLLECT;
                            } else if (FILTER.equals(fqm)) {
                                userValue = FIIUserValue.FILTER;
                            } else if (FINDFIRST.equals(fqm)) {
                                if (stack.getStackDepth() > 0) {
                                    OpcodeStack.Item itm = stack.getStackItem(0);
                                    if (itm.getUserValue() == FIIUserValue.FILTER) {
                                        userValue = FIIUserValue.FINDFIRST;
                                    }
                                }
                            } else if (GET.equals(fqm)) {
                                if (stack.getStackDepth() >= 2) {
                                    OpcodeStack.Item itm = stack.getStackItem(0);
                                    if (Values.ZERO.equals(itm.getConstant())) {
                                        itm = stack.getStackItem(1);
                                        if ((itm.getUserValue() == FIIUserValue.COLLECT) && (itm.getRegisterNumber() < 0)) {
                                            bugReporter.reportBug(new BugInstance(this, BugType.FII_USE_FIND_FIRST.name(), NORMAL_PRIORITY).addClass(this)
                                                    .addMethod(this).addSourceLine(this));
                                        }
                                    }
                                }
                            }
                        }
                    break;

                    case Constants.INVOKEVIRTUAL:
                        FQMethod fqm = new FQMethod(getClassConstantOperand(), getNameConstantOperand(), getSigConstantOperand());
                        if (ISPRESENT.equals(fqm)) {
                            if (stack.getStackDepth() > 0) {
                                OpcodeStack.Item itm = stack.getStackItem(0);
                                if ((itm.getUserValue() == FIIUserValue.FINDFIRST) && (itm.getRegisterNumber() < 0)) {
                                    bugReporter.reportBug(new BugInstance(this, BugType.FII_USE_ANY_MATCH.name(), LOW_PRIORITY).addClass(this).addMethod(this)
                                            .addSourceLine(this));
                                }
                            }
                        }
                    break;
                }
            }
        } finally {
            stack.sawOpcode(this, seen);
            if ((userValue != null) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item itm = stack.getStackItem(0);
                itm.setUserValue(userValue);
            }
        }
    }

    @Nullable
    private BootstrapMethods getBootstrapAttribute(JavaClass clz) {
        for (Attribute att : clz.getAttributes()) {
            if (att instanceof BootstrapMethods) {
                return (BootstrapMethods) att;
            }
        }

        return null;
    }

    @Nullable
    private ConstantMethodHandle getMethodHandle(int bootstrapIndex) {
        BootstrapMethod bsMethod = bootstrapAtt.getBootstrapMethods()[bootstrapIndex];

        for (int arg : bsMethod.getBootstrapArguments()) {
            Constant c = getConstantPool().getConstant(arg);
            if (c instanceof ConstantMethodHandle) {
                return (ConstantMethodHandle) c;
            }
        }

        return null;
    }

    @Nullable
    private String getAnonymousName(ConstantMethodHandle cmh) {
        if (cmh.getReferenceKind() != Const.REF_invokeStatic) {
            return null;
        }

        ConstantPool cp = getConstantPool();
        ConstantCP methodRef = (ConstantCP) cp.getConstant(cmh.getReferenceIndex());
        String clsName = methodRef.getClass(cp);
        if (!clsName.equals(cls.getClassName())) {
            return null;
        }

        ConstantNameAndType nameAndType = (ConstantNameAndType) cp.getConstant(methodRef.getNameAndTypeIndex());

        String signature = nameAndType.getSignature(cp);
        if (signature.endsWith("V")) {
            return null;
        }

        String methodName = nameAndType.getName(cp);
        if (!isSynthetic(methodName, nameAndType.getSignature(cp))) {
            return null;
        }

        return methodName;
    }

    private boolean isSynthetic(String methodName, String methodSig) {
        for (Method m : cls.getMethods()) {
            if (methodName.equals(m.getName()) && methodSig.equals(m.getSignature())) {
                return m.isSynthetic();
            }
        }

        return false;
    }

    static class FIInfo {
        private Method method;
        private SourceLineAnnotation srcLine;

        public FIInfo(Method method, SourceLineAnnotation srcLine) {
            this.method = method;
            this.srcLine = srcLine;
        }

        public Method getMethod() {
            return method;
        }

        public SourceLineAnnotation getSrcLine() {
            return srcLine;
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }

    }
}
