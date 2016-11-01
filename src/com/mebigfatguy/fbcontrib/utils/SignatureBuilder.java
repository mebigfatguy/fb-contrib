/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2016 ThrawnCA
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

import java.util.ArrayList;
import java.util.List;

/**
 * Constructs a method signature.
 */
public class SignatureBuilder {

    public static final String SIG_VOID_TO_VOID = new SignatureBuilder().toString();
    public static final String SIG_VOID_TO_BOOLEAN = new SignatureBuilder().withReturnType(Values.SIG_PRIMITIVE_BOOLEAN).toString();
    public static final String SIG_VOID_TO_INT = new SignatureBuilder().withReturnType(Values.SIG_PRIMITIVE_INT).toString();
    public static final String SIG_INT_TO_VOID = new SignatureBuilder().withParamTypes(Values.SIG_PRIMITIVE_INT).toString();
    public static final String SIG_INT_TO_OBJECT = new SignatureBuilder().withParamTypes(Values.SIG_PRIMITIVE_INT)
            .withReturnType(Values.SLASHED_JAVA_LANG_OBJECT).toString();
    public static final String SIG_OBJECT_TO_OBJECT = new SignatureBuilder().withParamTypes(Values.SLASHED_JAVA_LANG_OBJECT)
            .withReturnType(Values.SLASHED_JAVA_LANG_OBJECT).toString();
    public static final String SIG_OBJECT_TO_BOOLEAN = new SignatureBuilder().withParamTypes(Values.SLASHED_JAVA_LANG_OBJECT)
            .withReturnType(Values.SIG_PRIMITIVE_BOOLEAN).toString();
    public static final String SIG_OBJECT_TO_STRING = new SignatureBuilder().withParamTypes(Values.SLASHED_JAVA_LANG_OBJECT)
            .withReturnType(Values.SLASHED_JAVA_LANG_STRING).toString();
    public static final String SIG_TWO_OBJECTS_TO_OBJECT = new SignatureBuilder()
            .withParamTypes(Values.SLASHED_JAVA_LANG_OBJECT, Values.SLASHED_JAVA_LANG_OBJECT).withReturnType(Values.SLASHED_JAVA_LANG_OBJECT).toString();
    public static final String SIG_STRING_TO_VOID = new SignatureBuilder().withParamTypes(Values.SLASHED_JAVA_LANG_STRING).toString();
    public static final String SIG_VOID_TO_STRING = new SignatureBuilder().withReturnType(Values.SLASHED_JAVA_LANG_STRING).toString();
    public static final String SIG_COLLECTION_TO_BOOLEAN = new SignatureBuilder().withParamTypes(Values.SLASHED_JAVA_UTIL_COLLECTION)
            .withReturnType(Values.SIG_PRIMITIVE_BOOLEAN).toString();

    private String methodName;
    private List<String> paramTypes;
    private String returnType;

    /**
     * Defaults assume that the method name is not included, there are no parameters, and the method returns void.
     */
    public SignatureBuilder() {
        methodName = "";
        paramTypes = null;
        returnType = Values.SIG_VOID;
    }

    public SignatureBuilder withMethodName(String name) {
        methodName = name;
        return this;
    }

    public SignatureBuilder withParamTypes(String... types) {
        if (paramTypes == null) {
            paramTypes = new ArrayList<>(types.length);
        }

        for (String type : types) {
            paramTypes.add(SignatureUtils.classToSignature(type));
        }
        return this;
    }

    public SignatureBuilder withParamTypes(Class<?>... types) {
        if (paramTypes == null) {
            paramTypes = new ArrayList<>(types.length);
        }

        for (Class<?> type : types) {
            paramTypes.add(SignatureUtils.classToSignature(type.getName()));
        }
        return this;
    }

    public SignatureBuilder withReturnType(String type) {
        if ((type == null) || (type.length() == 0)) {
            throw new IllegalArgumentException("Missing return type; did you mean 'withoutReturnType'?");
        }
        returnType = SignatureUtils.classToSignature(type);
        return this;
    }

    public SignatureBuilder withReturnType(Class<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("Missing return type; did you mean 'withoutReturnType'?");
        }
        return withReturnType(type.getName());
    }

    public SignatureBuilder withoutReturnType() {
        returnType = "";
        return this;
    }

    @Override
    public String toString() {
        return methodName + '(' + join(paramTypes) + ')' + returnType;
    }

    private String join(List<String> strings) {
        if (paramTypes == null) {
            return "";
        }

        StringBuilder returnValue = new StringBuilder();
        for (String s : strings) {
            returnValue.append(s);
        }
        return returnValue.toString();
    }
}
