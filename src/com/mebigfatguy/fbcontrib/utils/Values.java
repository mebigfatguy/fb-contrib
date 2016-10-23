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

import org.apache.bcel.Constants;

import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.internalAnnotations.DottedClassName;
import edu.umd.cs.findbugs.internalAnnotations.SlashedClassName;

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

    @DottedClassName
    public static final String DOTTED_JAVA_LANG_OBJECT = "java.lang.Object";
    @DottedClassName
    public static final String DOTTED_JAVA_LANG_STRING = "java.lang.String";
    @DottedClassName
    public static final String DOTTED_JAVA_LANG_CLASS = "java.lang.Class";
    @DottedClassName
    public static final String DOTTED_JAVA_LANG_EXCEPTION = "java.lang.Exception";
    @DottedClassName
    public static final String DOTTED_JAVA_LANG_ERROR = "java.lang.Error";
    @DottedClassName
    public static final String DOTTED_JAVA_LANG_INTEGER = "java.lang.Integer";

    @SlashedClassName
    public static final String SLASHED_JAVA_LANG_OBJECT = "java/lang/Object";
    @SlashedClassName
    public static final String SLASHED_JAVA_LANG_STRING = "java/lang/String";
    @SlashedClassName
    public static final String SLASHED_JAVA_LANG_CLASS = "java/lang/Class";
    @SlashedClassName
    public static final String SLASHED_JAVA_LANG_EXCEPTION = "java/lang/Exception";
    @SlashedClassName
    public static final String SLASHED_JAVA_LANG_ERROR = "java/lang/Error";
    @SlashedClassName
    public static final String SLASHED_JAVA_LANG_INTEGER = "java/lang/Integer";
    @SlashedClassName
    public static final String SLASHED_JAVA_LANG_LONG = "java/lang/Long";
    @SlashedClassName
    public static final String SLASHED_JAVA_LANG_FLOAT = "java/lang/Float";
    @SlashedClassName
    public static final String SLASHED_JAVA_LANG_DOUBLE = "java/lang/Double";
    @SlashedClassName
    public static final String SLASHED_JAVA_LANG_SHORT = "java/lang/Short";
    @SlashedClassName
    public static final String SLASHED_JAVA_LANG_BYTE = "java/lang/Byte";
    @SlashedClassName
    public static final String SLASHED_JAVA_LANG_BOOLEAN = "java/lang/Boolean";
    @SlashedClassName
    public static final String SLASHED_JAVA_UTIL_LIST = "java/util/List";
    @SlashedClassName
    public static final String SLASHED_JAVA_UTIL_SET = "java/util/Set";
    @SlashedClassName
    public static final String SLASHED_JAVA_UTIL_MAP = "java/util/Map";

    public static final Integer JAVA_5 = Integer.valueOf(Constants.MAJOR_1_5);

    public static final Integer NORMAL_BUG_PRIORITY = Integer.valueOf(BytecodeScanningDetector.NORMAL_PRIORITY);
    public static final Integer LOW_BUG_PRIORITY = Integer.valueOf(BytecodeScanningDetector.LOW_PRIORITY);

    private Values() {
    }
}
