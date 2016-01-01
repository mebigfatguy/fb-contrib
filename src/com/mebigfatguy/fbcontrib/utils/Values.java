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
package com.mebigfatguy.fbcontrib.utils;

/**
 * a class holding common constants used throughout fb-contrib
 */
public class Values {

    public static final Integer NEGATIVE_ONE = Integer.valueOf(-1);
    public static final Integer ZERO = Integer.valueOf(0);
    public static final Integer ONE = Integer.valueOf(1);
    public static final Integer TWO = Integer.valueOf(2);
    public static final Integer THREE = Integer.valueOf(3);
    public static final Integer FOUR = Integer.valueOf(4);
    public static final Integer FIVE = Integer.valueOf(5);
    public static final Integer SIX = Integer.valueOf(6);
    public static final Integer SEVEN = Integer.valueOf(7);
    public static final Integer EIGHT = Integer.valueOf(8);

    public static final String CONSTRUCTOR = "<init>";
    public static final String STATIC_INITIALIZER = "<clinit>";

    public static final String JAVA_LANG_OBJECT = "java.lang.Object";
    public static final String JAVA_LANG_STRING = "java.lang.String";

    private Values() {
    }
}
