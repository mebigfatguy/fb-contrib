/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2015 Kevin Lubick
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

import java.util.BitSet;

import org.apache.bcel.Constants;

/**
 * a collection of static methods for categorizing opcodes into groups 
 */
public class OpcodeUtils implements Constants {

	private static final BitSet BRANCH_OPS = new BitSet();
	static {
		BRANCH_OPS.set(GOTO);
		BRANCH_OPS.set(GOTO_W);
		BRANCH_OPS.set(IF_ACMPEQ);		
		BRANCH_OPS.set(IF_ACMPNE);		
		BRANCH_OPS.set(IF_ICMPEQ);		
		BRANCH_OPS.set(IF_ICMPGE);		
		BRANCH_OPS.set(IF_ICMPGT);		
		BRANCH_OPS.set(IF_ICMPLE);
		BRANCH_OPS.set(IF_ICMPLT);
		BRANCH_OPS.set(IF_ICMPNE);
		BRANCH_OPS.set(IFEQ);
		BRANCH_OPS.set(IFGE);
		BRANCH_OPS.set(IFGT);
		BRANCH_OPS.set(IFLE);
		BRANCH_OPS.set(IFLT);
		BRANCH_OPS.set(IFNE);
		BRANCH_OPS.set(IFNONNULL);
		BRANCH_OPS.set(IFNULL);	
	}
	
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
	
	public static boolean isBranch(int seen) {
		return BRANCH_OPS.get(seen);
	}
	
	public static boolean isReturn(int seen) {
		return ((seen == ARETURN) || (seen == IRETURN) || (seen == LRETURN) || (seen == FRETURN) || (seen == DRETURN));
	}
	
}
