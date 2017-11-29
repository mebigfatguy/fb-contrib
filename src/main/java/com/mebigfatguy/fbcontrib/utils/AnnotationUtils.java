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
package com.mebigfatguy.fbcontrib.utils;

import java.util.Set;

import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.collect.MethodInfo;
import com.mebigfatguy.fbcontrib.collect.Statistics;

import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.XMethod;
import edu.umd.cs.findbugs.internalAnnotations.SlashedClassName;

public class AnnotationUtils {

    public static final Set<String> NULLABLE_ANNOTATIONS = UnmodifiableSet.create(
    // @formatter:off
        "Lorg/jetbrains/annotations/Nullable;",
        "Ljavax/annotation/Nullable;",
        "Ljavax/annotation/CheckForNull;",
        "Lcom/sun/istack/Nullable;",
        "Ledu/umd/cs/findbugs/annotations/Nullable;",
        "Landroid/support/annotations/Nullable;"
    // @formatter:on
    );

    public enum NULLABLE {
        TRUE
    };

    private AnnotationUtils() {
    }

    public static boolean methodHasNullableAnnotation(Method m) {
        for (AnnotationEntry entry : m.getAnnotationEntries()) {
            String annotationType = entry.getAnnotationType();
            if (NULLABLE_ANNOTATIONS.contains(annotationType)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isStackElementNullable(String className, Method method, OpcodeStack.Item itm) {
        if (itm.isNull() || (itm.getUserValue() instanceof NULLABLE)) {
            MethodInfo mi = Statistics.getStatistics().getMethodStatistics(className, method.getName(), method.getSignature());
            if (mi != null) {
                mi.setCanReturnNull(true);
            }
            return true;
        } else {
            XMethod xm = itm.getReturnValueOf();
            if (xm != null) {
                MethodInfo mi = Statistics.getStatistics().getMethodStatistics(xm.getClassName().replace('.', '/'), xm.getName(), xm.getSignature());
                if ((mi != null) && mi.getCanReturnNull()) {
                    mi = Statistics.getStatistics().getMethodStatistics(className, method.getName(), method.getSignature());
                    if (mi != null) {
                        mi.setCanReturnNull(true);
                    }
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean isMethodNullable(@SlashedClassName String className, String methodName, String methodSignature) {
        char returnTypeChar = methodSignature.charAt(methodSignature.indexOf(')') + 1);
        if ((returnTypeChar != 'L') && (returnTypeChar != '[')) {
            return false;
        }
        MethodInfo mi = Statistics.getStatistics().getMethodStatistics(className, methodName, methodSignature);
        return ((mi != null) && mi.getCanReturnNull());

        // can we check if it has @Nullable on it? hmm need to convert to Method
    }
}
