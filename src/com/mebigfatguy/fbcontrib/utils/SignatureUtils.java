/*
 * fb-contrib - Auxiliary detectors for Java programs
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

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import edu.umd.cs.findbugs.ba.generic.GenericSignatureParser;

/**
 * a collection of static methods for parsing signatures to find information out
 * about them
 */
public class SignatureUtils {

    /**
     * private to reinforce the helper status of the class
     */
    private SignatureUtils() {
    }

    public static boolean isInheritedMethod(JavaClass cls, String methodName, String signature) throws ClassNotFoundException {
        JavaClass[] infs = cls.getAllInterfaces();
        if (findInheritedMethod(infs, methodName, signature) != null) {
            return true;
        }

        JavaClass[] supers = cls.getSuperClasses();
        for (int i = 0; i < supers.length; i++) {
            if ("java.lang.Object".equals(supers[i].getClassName())) {
                supers[i] = null;
            }
        }
        return findInheritedMethod(supers, methodName, signature) != null;
    }

    /**
     * parses the package name from a fully qualified class name
     *
     * @param className
     *            the class in question
     *
     * @return the package of the class
     */
    public static String getPackageName(final String className) {
        int dotPos = className.lastIndexOf('.');
        if (dotPos < 0) {
            return "";
        }
        return className.substring(0, dotPos);
    }

    /**
     * returns whether or not the two packages have the same first 'depth'
     * parts, if they exist
     *
     * @param packName1
     *            the first package to check
     * @param packName2
     *            the second package to check
     * @param depth
     *            the number of package parts to check
     *
     * @return if they are similar
     */
    public static boolean similarPackages(String packName1, String packName2, int depth) {
        if (depth == 0) {
            return true;
        }

        packName1 = packName1.replace('/', '.');
        packName2 = packName2.replace('/', '.');

        int dot1 = packName1.indexOf('.');
        int dot2 = packName2.indexOf('.');
        if (dot1 < 0) {
            return (dot2 < 0);
        } else if (dot2 < 0) {
            return false;
        }

        String s1 = packName1.substring(0, dot1);
        String s2 = packName2.substring(0, dot2);

        if (!s1.equals(s2)) {
            return false;
        }

        return similarPackages(packName1.substring(dot1 + 1), packName2.substring(dot2 + 1), depth - 1);
    }

    /**
     * converts a primitive type code to a signature
     *
     * @param typeCode
     *            the raw JVM type value
     * @return the signature of the type
     */
    public static String getTypeCodeSignature(int typeCode) {
        switch (typeCode) {
        case Constants.T_BOOLEAN:
            return "Z";

        case Constants.T_CHAR:
            return "C";

        case Constants.T_FLOAT:
            return "F";

        case Constants.T_DOUBLE:
            return "D";

        case Constants.T_BYTE:
            return "B";

        case Constants.T_SHORT:
            return "S";

        case Constants.T_INT:
            return "I";

        case Constants.T_LONG:
            return "L";
        }

        return "Ljava/lang/Object;";
    }

    private static JavaClass findInheritedMethod(JavaClass[] classes, String methodName, String signature) {
        for (JavaClass cls : classes) {
            if (cls != null) {
                Method[] methods = cls.getMethods();
                for (Method m : methods) {
                    if (!m.isPrivate()) {
                        if (m.getName().equals(methodName)) {
                            if (m.getSignature().equals(signature)) {
                                return cls;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * returns a Map that represents the type of the parameter in slot x
     *
     * @param m
     *            the method for which you want the parameters
     * @return a map of parameter types (expect empty slots when doubles/longs
     *         are used
     */
    public static Map<Integer, String> getParameterSignatures(Method m) {
        Type[] parms = m.getArgumentTypes();

        Map<Integer, String> parmSigs = new LinkedHashMap<Integer, String>(parms.length);

        int slot = m.isStatic() ? 0 : 1;
        for (Type t : parms) {
            String signature = t.getSignature();
            parmSigs.put(Integer.valueOf(slot), signature);
            slot += getSignatureSize(signature);
        }

        return parmSigs;
    }

    /**
     * returns the first open register slot after parameters
     *
     * @param m
     *            the method for which you want the parameters
     * @return the first available register slot
     */
    public static int getFirstRegisterSlot(Method m) {
        Type[] parms = m.getArgumentTypes();

        int first = m.isStatic() ? 0 : 1;
        for (Type t : parms) {
            first += getSignatureSize(t.getSignature());
        }

        return first;
    }

    public static boolean compareGenericSignature(String genericSignature, String regularSignature) {
        Type[] regParms = Type.getArgumentTypes(regularSignature);

        GenericSignatureParser genParser = new GenericSignatureParser(genericSignature);
        Iterator<String> genIt = genParser.parameterSignatureIterator();

        for (Type regParm : regParms) {
            if (!genIt.hasNext()) {
                return false;
            }

            String genSig = genIt.next();
            int bracketPos = genSig.indexOf('<');
            if (bracketPos >= 0) {
                genSig = genSig.substring(0, bracketPos) + ';';
            }

            if (!regParm.getSignature().equals(genSig) && !genSig.startsWith("T")) {
                return false;
            }
        }

        if (genIt.hasNext()) {
            return false;
        }

        Type regReturnParms = Type.getReturnType(regularSignature);
        String genReturnSig = genParser.getReturnTypeSignature();
        int bracketPos = genReturnSig.indexOf('<');
        if (bracketPos >= 0) {
            genReturnSig = genReturnSig.substring(0, bracketPos) + ';';
        }

        if (!regReturnParms.getSignature().equals(genReturnSig) && !genReturnSig.startsWith("T")) {
            return false;
        }

        return true;
    }

    public static int getSignatureSize(String signature) {
        return (signature.equals("J") || signature.equals("D")) ? 2 : 1;
    }

}
