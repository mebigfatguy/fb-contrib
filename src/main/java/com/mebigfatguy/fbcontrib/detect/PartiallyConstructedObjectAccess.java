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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.StopOpcodeParsingException;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.SourceLineAnnotation;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for constructors of non final classes that make method calls to non final methods. As these methods could be overridden, the overridden method will be
 * accessing an object that is only partially constructed, perhaps causing problems.
 */
public class PartiallyConstructedObjectAccess extends BytecodeScanningDetector {
    private final BugReporter bugReporter;
    private OpcodeStack stack;
    private Map<Method, Map<Method, SourceLineAnnotation>> methodToCalledMethods;
    private boolean isCtor;

    /**
     * constructs a PCOA detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public PartiallyConstructedObjectAccess(final BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to set up the stack and methodToCalledmethods map reports calls to public non final methods from methods called from constructors.
     *
     * @param classContext
     *            the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(final ClassContext classContext) {
        try {
            JavaClass cls = classContext.getJavaClass();
            if (!cls.isFinal()) {
                stack = new OpcodeStack();
                methodToCalledMethods = new HashMap<>();
                super.visitClassContext(classContext);

                if (!methodToCalledMethods.isEmpty()) {
                    reportChainedMethods();
                }
            }
        } finally {
            stack = null;
            methodToCalledMethods = null;
        }
    }

    @Override
    public void visitCode(final Code obj) {
        stack.resetForMethodEntry(this);
        String methodName = getMethodName();
        isCtor = Values.CONSTRUCTOR.equals(methodName);

        if (!Values.STATIC_INITIALIZER.equals(methodName)) {
            Method m = getMethod();
            methodToCalledMethods.put(m, new HashMap<Method, SourceLineAnnotation>());

            try {
                super.visitCode(obj);
                if (methodToCalledMethods.get(m).isEmpty()) {
                    methodToCalledMethods.remove(getMethod());
                }
            } catch (StopOpcodeParsingException e) {
                methodToCalledMethods.remove(getMethod());
            }
        }
    }

    @Override
    public void sawOpcode(int seen) {
        try {
            stack.precomputation(this);

            if ((seen == INVOKEVIRTUAL) || (seen == INVOKEINTERFACE) || (seen == INVOKESPECIAL)) {
                int parmCount = SignatureUtils.getNumParameters(getSigConstantOperand());
                if (stack.getStackDepth() > parmCount) {
                    OpcodeStack.Item itm = stack.getStackItem(parmCount);
                    if (itm.getRegisterNumber() == 0) {
                        JavaClass cls = itm.getJavaClass();
                        if (cls != null) {
                            Method m = findMethod(cls, getNameConstantOperand(), getSigConstantOperand());
                            if ((m != null) && (!m.isFinal())) {
                                if (isCtor && (seen != INVOKESPECIAL)) {
                                    bugReporter.reportBug(new BugInstance(this, BugType.PCOA_PARTIALLY_CONSTRUCTED_OBJECT_ACCESS.name(), NORMAL_PRIORITY)
                                            .addClass(this).addMethod(this).addSourceLine(this, getPC()));
                                    throw new StopOpcodeParsingException();
                                } else {
                                    if (!Values.CONSTRUCTOR.equals(m.getName())) {
                                        Map<Method, SourceLineAnnotation> calledMethods = methodToCalledMethods.get(getMethod());
                                        calledMethods.put(m, SourceLineAnnotation.fromVisitedInstruction(this));
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
            stack.sawOpcode(this, seen);
        }
    }

    @Nullable
    private static Method findMethod(final JavaClass cls, final String methodName, final String methodSig) {
        Method[] methods = cls.getMethods();
        for (Method m : methods) {
            if (methodName.equals(m.getName()) && methodSig.equals(m.getSignature())) {
                return m;
            }
        }

        return null;
    }

    private void reportChainedMethods() {
        Set<Method> checkedMethods = new HashSet<>();

        JavaClass cls = getClassContext().getJavaClass();
        for (Map.Entry<Method, Map<Method, SourceLineAnnotation>> entry : methodToCalledMethods.entrySet()) {
            Method m = entry.getKey();
            if (Values.CONSTRUCTOR.equals(m.getName())) {
                checkedMethods.clear();
                Deque<SourceLineAnnotation> slas = foundPrivateInChain(m, checkedMethods);
                if (slas != null) {
                    BugInstance bi = new BugInstance(this, BugType.PCOA_PARTIALLY_CONSTRUCTED_OBJECT_ACCESS.name(), LOW_PRIORITY).addClass(cls).addMethod(cls,
                            m);
                    for (SourceLineAnnotation sla : slas) {
                        bi.addSourceLine(sla);
                    }
                    bugReporter.reportBug(bi);
                }
            }
        }
    }

    @Nullable
    private Deque<SourceLineAnnotation> foundPrivateInChain(Method m, Set<Method> checkedMethods) {
        Map<Method, SourceLineAnnotation> calledMethods = methodToCalledMethods.get(m);
        if (calledMethods != null) {
            for (Map.Entry<Method, SourceLineAnnotation> entry : calledMethods.entrySet()) {
                Method cm = entry.getKey();
                if (checkedMethods.contains(cm)) {
                    continue;
                }

                if (!cm.isPrivate() && !cm.isFinal()) {
                    Deque<SourceLineAnnotation> slas = new ArrayDeque<>();
                    slas.addLast(entry.getValue());
                    return slas;
                }

                checkedMethods.add(cm);
                Deque<SourceLineAnnotation> slas = foundPrivateInChain(cm, checkedMethods);
                if (slas != null) {
                    slas.addFirst(entry.getValue());
                    return slas;
                }
            }
        }

        return null;
    }
}
