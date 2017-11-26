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

public class AnnotationUtils {

    public static final Set<String> NULLABLE_ANNOTATIONS = UnmodifiableSet.create(
    // @formatter:off
        "org/jetbrains/annotations/Nullable",
        "javax/annotation/Nullable",
        "javax/annotation/CheckForNull",
        "edu/umd/cs/findbugs/annotations/Nullable",
        "android/support/annotations/Nullable"
    // @formatter:on
    );

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
}
