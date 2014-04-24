package com.mebigfatguy.fbcontrib.utils;

import static org.apache.bcel.Constants.*;

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
