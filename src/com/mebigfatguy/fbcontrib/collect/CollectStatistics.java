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
package com.mebigfatguy.fbcontrib.collect;

import java.util.HashSet;
import java.util.Set;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.NonReportingDetector;
import edu.umd.cs.findbugs.ba.ClassContext;

public class CollectStatistics extends BytecodeScanningDetector implements NonReportingDetector {
    private static Set<String> COMMON_METHOD_SIGS = new HashSet<String>();

    static {
        COMMON_METHOD_SIGS.add("\\<init\\>\\(\\)V");
        COMMON_METHOD_SIGS.add("toString\\(\\)Ljava/lang/String;");
        COMMON_METHOD_SIGS.add("hashCode\\(\\)I");
        COMMON_METHOD_SIGS.add("clone\\(\\).*");
        COMMON_METHOD_SIGS.add("values\\(\\).*");
        COMMON_METHOD_SIGS.add("main\\(\\[Ljava/lang/String;\\)V");
    }

    private int numMethodCalls;
    private boolean classHasAnnotation;

    public CollectStatistics(@SuppressWarnings("unused") BugReporter bugReporter) {
        Statistics.getStatistics().clear();
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        JavaClass cls = classContext.getJavaClass();
        AnnotationEntry[] annotations = cls.getAnnotationEntries();
        classHasAnnotation = (annotations != null) && (annotations.length > 0);
        super.visitClassContext(classContext);
    }

    @Override
    public void visitCode(Code obj) {

        numMethodCalls = 0;

        byte[] code = obj.getCode();
        if (code != null) {
            super.visitCode(obj);
            String clsName = getClassName();
            Method method = getMethod();
            int accessFlags = method.getAccessFlags();
            MethodInfo mi = Statistics.getStatistics().addMethodStatistics(clsName, getMethodName(), getMethodSig(), accessFlags, obj.getLength(),
                    numMethodCalls);
            if (clsName.contains("$") || ((accessFlags & (ACC_ABSTRACT | ACC_INTERFACE | ACC_ANNOTATION)) != 0)) {
                mi.addCallingAccess(Constants.ACC_PUBLIC);
            } else if ((accessFlags & Constants.ACC_PRIVATE) == 0) {
                if (isAssociationedWithAnnotations(method)) {
                    mi.addCallingAccess(Constants.ACC_PUBLIC);
                } else {
                    String methodSig = getMethodName() + getMethodSig();
                    for (String sig : COMMON_METHOD_SIGS) {
                        if (methodSig.matches(sig)) {
                            mi.addCallingAccess(Constants.ACC_PUBLIC);
                        }
                    }
                }
            }
            
            if ("equals".equals(getMethodName()) && "(Ljava/lang/Object;)Z".equals(getMethodSig())) {
                mi.setHasEquals();
            }
            if ("hashCode".equals(getMethodName()) && "()I".equals(getMethodSig())) {
                mi.setHasHashCode();
            }
            if ("toString".equals(getMethodName()) && "()Ljava/lang/String;".equals(getMethodSig())) {
                mi.setHasToString();
            }
        }
    }

    @Override
    public void sawOpcode(int seen) {
        switch (seen) {
        case INVOKEVIRTUAL:
        case INVOKEINTERFACE:
        case INVOKESPECIAL:
        case INVOKESTATIC:
            numMethodCalls++;
            break;
        default:
            break;
        }
    }

    private boolean isAssociationedWithAnnotations(Method m) {
        if (classHasAnnotation) {
            return true;
        }

        AnnotationEntry[] annotations = m.getAnnotationEntries();
        return (annotations != null) && (annotations.length > 0);
    }
}
