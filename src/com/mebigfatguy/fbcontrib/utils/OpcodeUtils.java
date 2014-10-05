/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2014 Kevin Lubick
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

import static org.apache.bcel.Constants.ALOAD;
import static org.apache.bcel.Constants.ALOAD_0;
import static org.apache.bcel.Constants.ALOAD_3;
import static org.apache.bcel.Constants.ASTORE;
import static org.apache.bcel.Constants.ASTORE_0;
import static org.apache.bcel.Constants.ASTORE_3;
import static org.apache.bcel.Constants.INVOKEINTERFACE;
import static org.apache.bcel.Constants.INVOKESPECIAL;
import static org.apache.bcel.Constants.INVOKESTATIC;
import static org.apache.bcel.Constants.INVOKEVIRTUAL;

public class OpcodeUtils {

	private OpcodeUtils(){}
	
	public static boolean isALoad(int seen) {
		return (seen == ALOAD) || ((seen >= ALOAD_0) && (seen <= ALOAD_3));
	}

	public static boolean isAStore(int seen) {
		return (seen == ASTORE) || ((seen >= ASTORE_0) && (seen <= ASTORE_3));
	}

	public static boolean isInvokeInterfaceSpecialStaticOrVirtual(int seen) {
		return (seen == INVOKESPECIAL) || (seen == INVOKEINTERFACE) || (seen == INVOKEVIRTUAL)	|| (seen == INVOKESTATIC);
	}
	
}
