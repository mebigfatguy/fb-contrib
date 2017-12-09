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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.collect.MethodInfo;
import com.mebigfatguy.fbcontrib.collect.Statistics;

import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.XMethod;
import edu.umd.cs.findbugs.internalAnnotations.SlashedClassName;

public final class AnnotationUtils {

    private static final String USER_NULLABLE_ANNOTATIONS = "fb-contrib.ai.annotations";

    public static final Set<String> NULLABLE_ANNOTATIONS = new HashSet<>();

    static {
        NULLABLE_ANNOTATIONS.add("Lorg/jetbrains/annotations/Nullable;");
        NULLABLE_ANNOTATIONS.add("Ljavax/annotation/Nullable;");
        NULLABLE_ANNOTATIONS.add("Ljavax/annotation/CheckForNull;");
        NULLABLE_ANNOTATIONS.add("Lcom/sun/istack/Nullable;");
        NULLABLE_ANNOTATIONS.add("Ledu/umd/cs/findbugs/annotations/Nullable;");
        NULLABLE_ANNOTATIONS.add("Lorg/springframework/lang/Nullable;");
        NULLABLE_ANNOTATIONS.add("Landroid/support/annotations/Nullable");

        String userAnnotations = System.getProperty(USER_NULLABLE_ANNOTATIONS);
        if ((userAnnotations != null) && userAnnotations.isEmpty()) {
            String[] annotations = userAnnotations.split("\\s*,\\s*");
            for (String annotation : annotations) {
                NULLABLE_ANNOTATIONS.add("L" + annotation.replace('.', '/') + ";");
            }
        }
    }

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

    /**
     * the map is keyed by register, and value by when an assumption holds to a byte offset if we have passed when the assumption holds, clear the item from the
     * map
     *
     * @param assumptionTill
     *            the map of assumptions
     * @param pc
     *            the current pc
     */
    public static void clearAssumptions(Map<Integer, Integer> assumptionTill, int pc) {
        Iterator<Integer> it = assumptionTill.values().iterator();
        while (it.hasNext()) {
            if (it.next().intValue() <= pc) {
                it.remove();
            }
        }
    }
}
