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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * a factory for creating unmodifiable HashSets by presenting values to a var-args creator method.
 */
public class UnmodifiableSet {

    private UnmodifiableSet() {
    }

    @SafeVarargs
    public static <T> Set<T> create(T... elements) {
        Set<T> s = new HashSet<T>(elements.length + (elements.length / 3) + 1);
        for (T t : elements) {
            s.add(t);
        }

        return Collections.<T> unmodifiableSet(s);
    }
}
