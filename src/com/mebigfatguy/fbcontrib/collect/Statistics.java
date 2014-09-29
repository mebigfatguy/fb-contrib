/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2014 Dave Brosius
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

/**
 * holds statistics about classes collected in the first pass.
 * To cut down on the size of the database, class, method, signature is not stored as a key
 * only a hash of that data is stored. This will allow some false positives, but hopefully not 
 * enough to cause issues.
 */
public class Statistics implements Iterable<Map.Entry<StatisticsKey, MethodInfo>> {

	private static Statistics statistics = new Statistics();
	private static final MethodInfo NOT_FOUND_METHOD_INFO = new MethodInfo();
	
	private final Map<StatisticsKey, MethodInfo> methodStatistics = new HashMap<StatisticsKey, MethodInfo>();

	private Statistics() {
	}
	
	public static Statistics getStatistics() {
		return statistics;
	}
	
	public void clear() {
		methodStatistics.clear();
	}
	
	public MethodInfo addMethodStatistics(String className, String methodName, String signature, int access, int numBytes, int numMethodCalls) {
		StatisticsKey key = new StatisticsKey(className, methodName, signature);
		MethodInfo mi = methodStatistics.get(key);
		if (mi == null) {
		    mi = new MethodInfo();
		    methodStatistics.put(key,  mi);
		}
		
		mi.setNumBytes(numBytes);
		mi.setNumMethodCalls(numMethodCalls);
		mi.setDeclaredAccess(access);
		return mi;
	}
	
	public MethodInfo getMethodStatistics(String className, String methodName, String signature) {
		MethodInfo mi = methodStatistics.get(new StatisticsKey(className, methodName, signature));
		if (mi == null)
			return NOT_FOUND_METHOD_INFO;
		return mi;
	}
	
	@Override
	public Iterator<Map.Entry<StatisticsKey, MethodInfo>> iterator() {
		return methodStatistics.entrySet().iterator();
	}
	
	public void addImmutabilityStatus(String className, String methodName, String signature, ImmutabilityType imType) {
    	StatisticsKey key = new StatisticsKey(className, methodName, signature);
        MethodInfo mi = methodStatistics.get(key);
        if (mi == null) {
            mi = new MethodInfo();
            methodStatistics.put(key,  mi);
        }
        
        mi.setImmutabilityType(imType);
    }
}
