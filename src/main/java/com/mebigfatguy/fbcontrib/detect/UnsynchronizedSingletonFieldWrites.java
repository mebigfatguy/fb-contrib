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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.ElementValue;
import org.apache.bcel.classfile.ElementValuePair;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.CollectionUtils;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for field writes to objects that are non singletons, where the write is not synchronized
 */
public class UnsynchronizedSingletonFieldWrites extends BytecodeScanningDetector {

    private static final Set<String> SPRING_CLASS_ANNOTATIONS = UnmodifiableSet.create(
    // @formatter:off
        "Lorg/springframework/stereotype/Component;",
        "Lorg/springframework/stereotype/Repository;",
        "Lorg/springframework/stereotype/Service;",
        "Lorg/springframework/stereotype/Controller;"
        // @formatter:on
    );

    private static final Set<String> IGNORABLE_METHOD_ANNOTATIONS = UnmodifiableSet.create(
    // @formatter:off
        "Ljavax/annotation/PostConstruct;",
        "Lorg/springframework/beans/factory/annotation/Autowired;"
        // @formatter:on
    );

    private static final String SPRING_SCOPE_ANNOTATION = "Lorg/springframework/context/annotation/Scope;";

    private final BugReporter bugReporter;
    private OpcodeStack stack;
    private int syncBlockCount;
    private Map<Integer, Integer> syncBlockBranchResetValues;

    /**
     * constructs a USFW detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public UnsynchronizedSingletonFieldWrites(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to look for classes that are singletons
     *
     * @param classContext
     *            the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            JavaClass cls = classContext.getJavaClass();
            if (isSingleton(cls)) {
                syncBlockBranchResetValues = new HashMap<>();
                stack = new OpcodeStack();
                super.visitClassContext(classContext);
            }
        } finally {
            syncBlockBranchResetValues = null;
            stack = null;
        }
    }

    /**
     * implements the visitor to look for methods that could possibly be 'normal' methods where a field is written to
     *
     * @param obj
     *            the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        Method m = getMethod();

        if (isIgnorableMethod(m)) {
            return;
        }

        stack.resetForMethodEntry(this);
        syncBlockBranchResetValues.clear();
        syncBlockCount = 0;
        super.visitCode(obj);
    }

    @Override
    public void sawOpcode(int seen) {
        try {
            Integer pc = Integer.valueOf(getPC());
            Integer count = syncBlockBranchResetValues.remove(pc);
            if (count != null) {
                syncBlockCount = count.intValue();
            }

            switch (seen) {
                case MONITORENTER:
                    syncBlockCount++;
                break;

                case MONITOREXIT:
                    syncBlockCount--;
                    // javac stutters the exit in catch/finally blocks in a bolux of nastyness, so guard against it
                    if (syncBlockCount < 0) {
                        syncBlockCount = 0;
                    }
                break;

                case PUTFIELD:
                    if (syncBlockCount == 0) {
                        if (stack.getStackDepth() >= 2) {
                            OpcodeStack.Item itm = stack.getStackItem(1);
                            if (itm.getRegisterNumber() == 0) {
                                bugReporter.reportBug(new BugInstance(this, BugType.USFW_UNSYNCHRONIZED_SINGLETON_FIELD_WRITES.name(), NORMAL_PRIORITY)
                                        .addClass(this).addMethod(this).addSourceLine(this));
                            }
                        }
                    }
                break;

                default:
                    if ((syncBlockCount > 0) && OpcodeUtils.isBranch(seen)) {
                        syncBlockBranchResetValues.put(Integer.valueOf(getBranchTarget()), Integer.valueOf(syncBlockCount));
                    }
                break;
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    private boolean isSingleton(JavaClass cls) {
        if (cls.isEnum()) {
            return true;
        }

        AnnotationEntry[] annotations = cls.getAnnotationEntries();
        if (CollectionUtils.isEmpty(annotations)) {
            return false;
        }

        boolean isSpringBean = false;
        for (AnnotationEntry annotation : annotations) {
            String type = annotation.getAnnotationType();
            if (SPRING_CLASS_ANNOTATIONS.contains(type)) {
                isSpringBean = true;
            } else if (SPRING_SCOPE_ANNOTATION.equals(type)) {
                ElementValuePair[] pairs = annotation.getElementValuePairs();
                if (!CollectionUtils.isEmpty(pairs)) {
                    for (ElementValuePair pair : pairs) {
                        String propName = pair.getNameString();
                        if ("value".equals(propName) || "scopeName".equals(propName)) {
                            ElementValue value = pair.getValue();
                            return "singleton".equals(value.stringifyValue());
                        }
                    }
                }
            }
        }

        return isSpringBean;
    }

    /**
     * looks for methods that should not be scanned for fields writes for a variety of reasons
     * <ul>
     * <li>Constructor</li>
     * <li>Static Initializer</li>
     * <li>static method</li>
     * <li>Has a synchronized attribute</li>
     * <li>Has a @PostConstruct annotation</li>
     * <li>Has an @Autowired annotation</li>
     * </ul>
     *
     * @param m
     *            the method to check
     * @return if the method should not be scanned
     */
    private boolean isIgnorableMethod(Method m) {
        if (m.isStatic() || m.isSynchronized()) {
            return true;
        }

        String name = m.getName();
        if (Values.CONSTRUCTOR.equals(name) || Values.STATIC_INITIALIZER.equals(name)) {
            return true;
        }

        AnnotationEntry[] annotations = m.getAnnotationEntries();
        if (CollectionUtils.isEmpty(annotations)) {
            return false;
        }

        for (AnnotationEntry annotation : annotations) {
            String type = annotation.getAnnotationType();

            if (IGNORABLE_METHOD_ANNOTATIONS.contains(type)) {
                return true;
            }
        }

        return false;
    }
}
