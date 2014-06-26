package com.mebigfatguy.fbcontrib.debug;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

public class Debug {

	
	private static PrintStream out;

	static {
		try {
			out = new PrintStream(new FileOutputStream("/tmp/findbugsConsole.txt"),true, "UTF-8");
			out.println("Hello findbugs");
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
	
	private Debug(){}

	public static void println() {
		out.println();
	}
	
	public static void println(Object x) {
		out.println(x);
	}

	/**
	 * Like println, but will print PC, if it's passed in
	 * 
	 * e.g. Debug.println(getPC(), "Hello world");
	 * will print
	 * [PC:42] Hello world
	 * 
	 * @param pc
	 * @param obj
	 */
	public static void println(int pc, Object obj) {
		out.printf("[PC:%d] %s%n", pc,obj);
	}
	
	
	
	
}