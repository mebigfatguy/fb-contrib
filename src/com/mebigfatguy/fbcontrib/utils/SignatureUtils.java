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

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import edu.umd.cs.findbugs.ba.generic.GenericSignatureParser;

/**
 * a collection of static methods for parsing signatures to find information out about them
 */
public class SignatureUtils {

    private static final Set<String> TWO_SLOT_TYPES = UnmodifiableSet.create("J", "D");

    private static final Pattern CLASS_COMPONENT_DELIMITER = Pattern.compile("\\$");
    private static final Pattern ANONYMOUS_COMPONENT = Pattern.compile("^[1-9][0-9]{0,9}$");

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
            if (Values.DOTTED_JAVA_LANG_OBJECT.equals(supers[i].getClassName())) {
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
     * returns whether or not the two packages have the same first 'depth' parts, if they exist
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
                    if (!m.isPrivate() && m.getName().equals(methodName) && m.getSignature().equals(signature)) {
                        return cls;
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
     * @return a map of parameter types (expect empty slots when doubles/longs are used
     */
    public static Map<Integer, String> getParameterSignatures(Method m) {
        Type[] parms = m.getArgumentTypes();
        if (parms.length == 0) {
            return Collections.emptyMap();
        }

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
        return (TWO_SLOT_TYPES.contains(signature)) ? 2 : 1;
    }

    /**
     * converts a signature, like Ljava/lang/String; into a dotted class name.
     *
     * @param signature
     *            a class signature
     *
     * @return the dotted class name
     */
    public static String stripSignature(String signature) {
        return trimSignature(signature).replace('/', '.');
    }

    /**
     * converts a signature, like Ljava/lang/String; into a slashed class name.
     *
     * @param signature
     *            the class signature
     *
     * @return the slashed class name
     */
    public static String trimSignature(String signature) {
        if (signature.startsWith("L") && signature.endsWith(";")) {
            return signature.substring(1, signature.length() - 1);
        }

        return signature;
    }

    /**
     * returns a slashed or dotted class name into a signature, like java/lang/String -- Ljava/lang/String;
     *
     * @param className
     *            the class name to convert
     * @return the signature format of the class
     */
    public static String classToSignature(String className) {
        return 'L' + className.replace('.', '/') + ';';
    }

    /**
     * @param className
     *            the name of the class
     *
     * @return the class name, discarding any anonymous component
     */
    public static String getNonAnonymousPortion(String className) {
        String[] components = CLASS_COMPONENT_DELIMITER.split(className);
        StringBuilder buffer = new StringBuilder(className.length()).append(components[0]);
        for (int i = 1; (i < components.length) && !ANONYMOUS_COMPONENT.matcher(components[i]).matches(); i++) {
            buffer.append('$').append(components[i]);
        }
        return buffer.toString();
    }

}
