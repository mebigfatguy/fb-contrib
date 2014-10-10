package com.mebigfatguy.fbcontrib.debug;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

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
	 * @param pc the program counter
	 * @param obj the object to output
	 */
	public static void println(int pc, Object obj) {
		out.printf("[PC:%d] %s%n", pc,obj);
	}
	
	
	
	
}