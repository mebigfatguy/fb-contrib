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
package com.mebigfatguy.fbcontrib.utils;

import java.util.Collection;
import java.util.Map;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.JavaClass;

/**
 * a collection of static methods for determining if a class belongs to one or more collection types.
 */
public final class CollectionUtils {

    private static JavaClass LIST_CLASS = null;
    private static JavaClass SET_CLASS = null;
    private static JavaClass MAP_CLASS = null;

    static {
        try {
            LIST_CLASS = Repository.lookupClass(Values.SLASHED_JAVA_UTIL_LIST);
            SET_CLASS = Repository.lookupClass(Values.SLASHED_JAVA_UTIL_SET);
            MAP_CLASS = Repository.lookupClass(Values.SLASHED_JAVA_UTIL_MAP);
        } catch (ClassNotFoundException cnfe) {
            // no bugReporter at present, so nothing to do
        }
    }

    /**
     * private to reinforce the helper status of the class
     */
    private CollectionUtils() {
    }

    /**
     * determines if the current class name is derived from List, Set or Map
     *
     * @param clsName
     *            the class to determine it's parentage
     * @return if the class is a List, Set or Map
     *
     * @throws ClassNotFoundException
     *             if the cls parameter can't be found
     */
    public static boolean isListSetMap(String clsName) throws ClassNotFoundException {
        JavaClass cls = Repository.lookupClass(clsName);
        return (cls.implementationOf(LIST_CLASS) || cls.implementationOf(SET_CLASS) || cls.implementationOf(MAP_CLASS));
    }

    public static boolean isEmpty(Collection<?> c) {
        return (c == null) || c.isEmpty();
    }

    public static boolean isEmpty(Map<?, ?> m) {
        return (m == null) || m.isEmpty();
    }

    @SafeVarargs
    public static <T> boolean isEmpty(T... a) {
        return (a == null) || (a.length == 0);
    }
}
