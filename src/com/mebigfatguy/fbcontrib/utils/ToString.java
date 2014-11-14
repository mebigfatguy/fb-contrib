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
package com.mebigfatguy.fbcontrib.utils;

import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * an automatic toString() builder using reflection
 */
public class ToString {

	private ToString() {
	}
	
	public static String build(Object o) {
		StringBuilder sb = new StringBuilder(100);
		Class<?> cls = o.getClass();
		sb.append(cls.getSimpleName()).append('[');
		
		try {
			String sep = "";
			for (Field f : cls.getDeclaredFields()) {
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
		} catch (Exception e) {
		}
		
		sb.append(']');
		return sb.toString();
	}
}
