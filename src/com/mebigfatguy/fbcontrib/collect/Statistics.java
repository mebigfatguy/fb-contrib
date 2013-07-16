/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2013 Dave Brosius
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
import java.util.Map;

/**
 * holds statistics about classes collected in the first pass.
 * To cut down on the size of the database, class, method, signature is not stored as a key
 * only a hash of that data is stored. This will allow some false positives, but hopefully not 
 * enough to cause issues.
 */
public class Statistics {

	private static Statistics statistics = new Statistics();
	
	private Map<Long, Long> methodStatistics = new HashMap<Long, Long>();

	private Statistics() {
		
	}
	
	public static Statistics getStatistics() {
		return statistics;
	}
	
	public void clear() {
		methodStatistics.clear();
	}
	
	public void addMethodStatistics(String className, String methodName, String signature, MethodInfo methodInfo) {
		Long key = getKey(className, methodName, signature);
		if (methodStatistics.containsKey(key))
			methodStatistics.put(key, getValue(new MethodInfo()));
		else
			methodStatistics.put(getKey(className, methodName, signature), getValue(methodInfo));
	}
	
	public MethodInfo getMethodStatistics(String className, String methodName, String signature) {
		Long v = methodStatistics.get(getKey(className, methodName, signature));
		if (v == null)
			return new MethodInfo();
		else
			return buildMethodInfo(v);
	}
	
	private Long getKey(String className, String methodName, String signature) {
		long hashCode = className.hashCode();
		hashCode <<= 16;
		hashCode |= methodName.hashCode();
		hashCode <<= 16;
		hashCode |= signature.hashCode();
		return Long.valueOf(hashCode);
	}
	
	private Long getValue(MethodInfo methodInfo) {
		long value = methodInfo.numBytes;
		value <<= 32;
		value |= methodInfo.numMethodCalls;
		return Long.valueOf(value);
	}
	
	private MethodInfo buildMethodInfo(Long value) {
		MethodInfo mi = new MethodInfo();
		long v = value.longValue();
		mi.numBytes = (int)(v >>> 32);
		mi.numMethodCalls = (int)(v & 0x7FFFFFFF);
		return mi;
	}
	
	public static class MethodInfo
	{
		public int numBytes;
		public int numMethodCalls;
		
		public MethodInfo() {
			numBytes = 0;
			numMethodCalls = 0;
		}
		
		public MethodInfo(int bytes, int calls) {
			numBytes = bytes;
			numMethodCalls = calls;
		}
	}
}
