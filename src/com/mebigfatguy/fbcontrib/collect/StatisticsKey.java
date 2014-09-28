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

public class StatisticsKey {
	
	private String className;
	private String methodName;
	private String signature;
	
	public StatisticsKey(String clsName, String mName, String sig) {
		className = clsName;
		methodName = mName;
		signature = sig;
	}

	public String getClassName() {
		return className;
	}

	public String getMethodName() {
		return methodName;
	}

	public String getSignature() {
		return signature;
	}
	
	@Override
	public int hashCode() {
		return className.hashCode() ^ methodName.hashCode() ^ signature.hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof StatisticsKey)) {
			return false;
		}
		
		StatisticsKey that = (StatisticsKey) o;
		
		return className.equals(that.className) && methodName.equals(that.methodName) && signature.equals(that.signature); 
	}
}
