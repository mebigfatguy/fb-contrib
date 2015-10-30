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
package com.mebigfatguy.fbcontrib.utils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * an automatic toString() builder using reflection
 */
public class ToString {

    private static class VisitedInfo {
        Set<Integer> visited = new HashSet<Integer>();
        int count = 0;
    }

    private static final ThreadLocal<VisitedInfo> visited = new ThreadLocal<VisitedInfo>() {

        @Override
        protected VisitedInfo initialValue() {
            return new VisitedInfo();
        }
    };

    private ToString() {
    }

    public static String build(Object o) {
        VisitedInfo vi = visited.get();
        try {
            vi.count++;
            return generate(o, vi.visited);
        } finally {
            if (--vi.count == 0) {
                vi.visited.clear();
            }
        }
    }

    private static String generate(Object o, Set<Integer> visitedObjects) {

        StringBuilder sb = new StringBuilder(100);
        Class<?> cls = o.getClass();
        Integer identityHC = Integer.valueOf(System.identityHashCode(o));
        sb.append(cls.getSimpleName()).append('[').append(identityHC).append("]{");

        if (!visitedObjects.contains(identityHC)) {
            try {
                visitedObjects.add(identityHC);
                String sep = "";
                for (Field f : cls.getDeclaredFields()) {
                    if (!f.isSynthetic() && !f.getName().contains("$")) {
                        sb.append(sep);
                        sep = ", ";
                        sb.append(f.getName()).append('=');
                        try {
                            f.setAccessible(true);
                            Object value = f.get(o);
                            if (value == null) {
                                sb.append((String) null);
                            } else if (value.getClass().isArray()) {
                                sb.append(Arrays.toString((Object[]) value));
                            } else {
                                sb.append(value);
                            }
                        } catch (SecurityException e) {
                            sb.append("*SECURITY_EXCEPTION*");
                        }
                    }
                }
            } catch (IllegalAccessException | RuntimeException e) {
                // if we get an exception show as much as we can get
            }
        }

        sb.append('}');
        return sb.toString();
    }
}
