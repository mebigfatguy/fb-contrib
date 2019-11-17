/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2019 Dave Brosius
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

import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.internalAnnotations.DottedClassName;
import edu.umd.cs.findbugs.internalAnnotations.SlashedClassName;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.bcel.Const;
import org.apache.bcel.generic.Type;

/**
 * a class holding common Const used throughout fb-contrib
 */
public final class Values {

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
    public static final String TOSTRING = "toString";
    public static final String HASHCODE = "hashCode";

    public static final String SIG_PRIMITIVE_BOOLEAN = Type.BOOLEAN.getSignature();
    public static final String SIG_PRIMITIVE_CHAR = Type.CHAR.getSignature();
    public static final String SIG_PRIMITIVE_FLOAT = Type.FLOAT.getSignature();
    public static final String SIG_PRIMITIVE_DOUBLE = Type.DOUBLE.getSignature();
    public static final String SIG_PRIMITIVE_BYTE = Type.BYTE.getSignature();
    public static final String SIG_PRIMITIVE_SHORT = Type.SHORT.getSignature();
    public static final String SIG_PRIMITIVE_INT = Type.INT.getSignature();
    public static final String SIG_PRIMITIVE_LONG = Type.LONG.getSignature();
    public static final Map<Byte, String> PRIMITIVE_TYPE_CODE_SIGS;

    public static final String SIG_VOID = Type.VOID.getSignature();
    public static final String SIG_GENERIC_TEMPLATE = "T";
    public static final String SIG_QUALIFIED_CLASS_PREFIX = "L";
    public static final String SIG_QUALIFIED_CLASS_SUFFIX = ";";
    public static final char SIG_QUALIFIED_CLASS_SUFFIX_CHAR = ';';
    public static final String SIG_ARRAY_PREFIX = "[";
    public static final String SIG_ARRAY_OF_ARRAYS_PREFIX = "[[";

    @DottedClassName
    public static final String DOTTED_JAVA_LANG_OBJECT = "java.lang.Object";
    @DottedClassName
    public static final String DOTTED_JAVA_LANG_STRING = "java.lang.String";
    @DottedClassName
    public static final String DOTTED_JAVA_LANG_CLASS = "java.lang.Class";
    @DottedClassName
    public static final String DOTTED_JAVA_LANG_ENUM = "java.lang.Enum";
    @DottedClassName
    public static final String DOTTED_JAVA_LANG_THROWABLE = "java.lang.Throwable";
    @DottedClassName
    public static final String DOTTED_JAVA_LANG_EXCEPTION = "java.lang.Exception";
    @DottedClassName
    public static final String DOTTED_JAVA_LANG_RUNTIMEEXCEPTION = "java.lang.RuntimeException";
    @DottedClassName
    public static final String DOTTED_JAVA_LANG_ERROR = "java.lang.Error";
    @DottedClassName
    public static final String DOTTED_JAVA_LANG_INTEGER = "java.lang.Integer";
    @DottedClassName
    public static final String DOTTED_JAVA_LANG_STRINGBUILDER = "java.lang.StringBuilder";
    @DottedClassName
    public static final String DOTTED_JAVA_UTIL_MAP = "java.util.Map";

    @SlashedClassName
    public static final String SLASHED_JAVA_LANG_OBJECT = "java/lang/Object";
    public static final String SIG_JAVA_LANG_OBJECT = SIG_QUALIFIED_CLASS_PREFIX + SLASHED_JAVA_LANG_OBJECT
            + SIG_QUALIFIED_CLASS_SUFFIX_CHAR;
    @SlashedClassName
    public static final String SLASHED_JAVA_LANG_STRING = "java/lang/String";
    public static final String SIG_JAVA_LANG_STRING = SIG_QUALIFIED_CLASS_PREFIX + SLASHED_JAVA_LANG_STRING
            + SIG_QUALIFIED_CLASS_SUFFIX_CHAR;
    @SlashedClassName
    public static final String SLASHED_JAVA_LANG_VOID = "java/lang/Void";
    public static final String SIG_JAVA_LANG_VOID = SIG_QUALIFIED_CLASS_PREFIX + SLASHED_JAVA_LANG_VOID
            + SIG_QUALIFIED_CLASS_SUFFIX_CHAR;

    @SlashedClassName
    public static final String SLASHED_JAVA_LANG_STRINGBUILDER = "java/lang/StringBuilder";
    @SlashedClassName
    public static final String SLASHED_JAVA_LANG_STRINGBUFFER = "java/lang/StringBuffer";
    @SlashedClassName
    public static final String SLASHED_JAVA_LANG_SYSTEM = "java/lang/System";
    @SlashedClassName
    public static final String SLASHED_JAVA_LANG_CLASS = "java/lang/Class";
    @SlashedClassName
    public static final String SLASHED_JAVA_LANG_THROWABLE = "java/lang/Throwable";
    @SlashedClassName
    public static final String SLASHED_JAVA_LANG_EXCEPTION = "java/lang/Exception";
    @SlashedClassName
    public static final String SLASHED_JAVA_LANG_RUNTIMEEXCEPTION = "java/lang/RuntimeException";
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
    public static final String SLASHED_JAVA_LANG_CHARACTER = "java/lang/Character";
    @SlashedClassName
    public static final String SLASHED_JAVA_LANG_BOOLEAN = "java/lang/Boolean";
    public static final String SIG_JAVA_LANG_BOOLEAN = SIG_QUALIFIED_CLASS_PREFIX + SLASHED_JAVA_LANG_BOOLEAN
            + SIG_QUALIFIED_CLASS_SUFFIX_CHAR;
    @SlashedClassName
    public static final String SLASHED_JAVA_UTIL_COMPARATOR = "java/util/Comparator";
    @SlashedClassName
    public static final String SLASHED_JAVA_UTIL_COLLECTION = "java/util/Collection";
    @SlashedClassName
    public static final String SLASHED_JAVA_UTIL_LIST = "java/util/List";
    @SlashedClassName
    public static final String SLASHED_JAVA_UTIL_SET = "java/util/Set";
    @SlashedClassName
    public static final String SLASHED_JAVA_UTIL_MAP = "java/util/Map";
    @SlashedClassName
    public static final String SLASHED_JAVA_UTIL_QUEUE = "java/util/Queue";
    @SlashedClassName
    public static final String SLASHED_JAVA_UTIL_UUID = "java/util/UUID";

    public static final String SIG_JAVA_UTIL_STRINGBUFFER = "Ljava/lang/StringBuffer;";
    public static final String SIG_JAVA_UTIL_STRINGBUILDER = "Ljava/lang/StringBuilder;";

    public static final char INNER_CLASS_SEPARATOR = '$';
    public static final char SYNTHETIC_MEMBER_CHAR = '$';

    public static final Integer JAVA_1_1 = Integer.valueOf(Const.MAJOR_1_1);
    public static final Integer JAVA_5 = Integer.valueOf(Const.MAJOR_1_5);

    public static final Integer NORMAL_BUG_PRIORITY = Integer.valueOf(BytecodeScanningDetector.NORMAL_PRIORITY);
    public static final Integer LOW_BUG_PRIORITY = Integer.valueOf(BytecodeScanningDetector.LOW_PRIORITY);

    public static final String WHITESPACE_COMMA_SPLIT = "\\s*,\\s*";

    public static final String JAVA = "java";

    static {
        Map<Byte, String> typeCodeSigs = new HashMap<>(8, 1.0F);
        typeCodeSigs.put(Const.T_BOOLEAN, SIG_PRIMITIVE_BOOLEAN);
        typeCodeSigs.put(Const.T_CHAR, SIG_PRIMITIVE_CHAR);
        typeCodeSigs.put(Const.T_FLOAT, SIG_PRIMITIVE_FLOAT);
        typeCodeSigs.put(Const.T_DOUBLE, SIG_PRIMITIVE_DOUBLE);
        typeCodeSigs.put(Const.T_BYTE, SIG_PRIMITIVE_BYTE);
        typeCodeSigs.put(Const.T_SHORT, SIG_PRIMITIVE_SHORT);
        typeCodeSigs.put(Const.T_INT, SIG_PRIMITIVE_INT);
        typeCodeSigs.put(Const.T_LONG, SIG_PRIMITIVE_LONG);
        PRIMITIVE_TYPE_CODE_SIGS = Collections.unmodifiableMap(typeCodeSigs);
    }

    private Values() {
    }
}
