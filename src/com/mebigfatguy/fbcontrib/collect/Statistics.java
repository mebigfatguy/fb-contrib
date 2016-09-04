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
package com.mebigfatguy.fbcontrib.collect;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.ToString;

/**
 * holds statistics about classes and methods collected in the first pass.
 */
public class Statistics implements Iterable<Map.Entry<FQMethod, MethodInfo>> {

    private static Statistics statistics = new Statistics();
    private static final MethodInfo NOT_FOUND_METHOD_INFO = new MethodInfo();

    private final Map<FQMethod, MethodInfo> methodStatistics = new HashMap<>();

    private Statistics() {
    }

    public static Statistics getStatistics() {
        return statistics;
    }

    public void clear() {
        methodStatistics.clear();
    }

    public MethodInfo addMethodStatistics(String className, String methodName, String signature, int access, int numBytes, int numMethodCalls) {
        FQMethod key = new FQMethod(className, methodName, signature);
        MethodInfo mi = methodStatistics.get(key);
        if (mi == null) {
            mi = new MethodInfo();
            methodStatistics.put(key, mi);
        }

        mi.setNumBytes(numBytes);
        mi.setNumMethodCalls(numMethodCalls);
        mi.setDeclaredAccess(access);
        return mi;
    }

    public MethodInfo getMethodStatistics(String className, String methodName, String signature) {
        MethodInfo mi = methodStatistics.get(new FQMethod(className, methodName, signature));
        if (mi == null) {
            return NOT_FOUND_METHOD_INFO;
        }
        return mi;
    }

    @Override
    public Iterator<Map.Entry<FQMethod, MethodInfo>> iterator() {
        return methodStatistics.entrySet().iterator();
    }

    public void addImmutabilityStatus(String className, String methodName, String signature, ImmutabilityType imType) {
        FQMethod key = new FQMethod(className, methodName, signature);
        MethodInfo mi = methodStatistics.get(key);
        if (mi == null) {
            mi = new MethodInfo();
            methodStatistics.put(key, mi);
        }

        mi.setImmutabilityType(imType);
    }

    @Override
    public String toString() {
        return ToString.build(this);
    }
}
