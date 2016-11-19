/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2016 Dave Brosius
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

import java.util.Set;

import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.ElementValue;
import org.apache.bcel.classfile.ElementValuePair;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.CollectionUtils;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for field writes to object that are non singletons, where the write is not synchronized
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

    private static final String SPRING_SCOPE_ANNOTATION = "Lorg.springframework/context/annotation/Scope;";

    private final BugReporter bugReporter;
    private OpcodeStack stack;
    private int syncBlockCount;

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
                stack = new OpcodeStack();
                super.visitClassContext(classContext);
            }
        } finally {
            stack = null;
        }
    }

    @Override
    public void visitCode(Code obj) {
        Method m = getMethod();
        if (m.isSynchronized()) {
            return;
        }

        String name = m.getName();
        if (Values.CONSTRUCTOR.equals(name) || Values.STATIC_INITIALIZER.equals(name)) {
            return;
        }

        stack.resetForMethodEntry(this);
        syncBlockCount = 0;
        super.visitCode(obj);
    }

    @Override
    public void sawOpcode(int seen) {
        try {
            switch (seen) {
                case MONITORENTER:
                    syncBlockCount++;
                break;

                case MONITOREXIT:
                    syncBlockCount--;
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
                        if ("value".equals(propName) || "propName".equals(propName)) {
                            ElementValue value = pair.getValue();
                            return ("singleton".equals(value));
                        }
                    }
                }
            }
        }

        return isSpringBean;
    }
}
