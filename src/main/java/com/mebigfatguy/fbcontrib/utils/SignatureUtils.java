/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2017 Dave Brosius
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
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import org.apache.bcel.Const;

import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import edu.umd.cs.findbugs.ba.generic.GenericSignatureParser;
import edu.umd.cs.findbugs.internalAnnotations.DottedClassName;
import edu.umd.cs.findbugs.internalAnnotations.SlashedClassName;

/**
 * a collection of static methods for parsing signatures to find information out about them
 */
public final class SignatureUtils {

    public static final Set<String> PRIMITIVE_TYPES = UnmodifiableSet.create(Values.SIG_PRIMITIVE_BYTE, Values.SIG_PRIMITIVE_SHORT, Values.SIG_PRIMITIVE_INT,
            Values.SIG_PRIMITIVE_LONG, Values.SIG_PRIMITIVE_CHAR, Values.SIG_PRIMITIVE_FLOAT, Values.SIG_PRIMITIVE_DOUBLE, Values.SIG_PRIMITIVE_BOOLEAN,
            Values.SIG_VOID, "", null);

    private static final Set<String> TWO_SLOT_TYPES = UnmodifiableSet.create(Values.SIG_PRIMITIVE_LONG, Values.SIG_PRIMITIVE_DOUBLE);

    private static final Pattern CLASS_COMPONENT_DELIMITER = Pattern.compile("\\$");
    private static final Pattern ANONYMOUS_COMPONENT = Pattern.compile("^[1-9][0-9]{0,9}$");
    private static final String ECLIPSE_WEIRD_SIG_CHARS = "!+";

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

        String dottedPackName1 = packName1.replace('/', '.');
        String dottedPackName2 = packName2.replace('/', '.');

        int dot1 = dottedPackName1.indexOf('.');
        int dot2 = dottedPackName2.indexOf('.');
        if (dot1 < 0) {
            return (dot2 < 0);
        } else if (dot2 < 0) {
            return false;
        }

        String s1 = dottedPackName1.substring(0, dot1);
        String s2 = dottedPackName2.substring(0, dot2);

        if (!s1.equals(s2)) {
            return false;
        }

        return similarPackages(dottedPackName1.substring(dot1 + 1), dottedPackName2.substring(dot2 + 1), depth - 1);
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
            case Const.T_BOOLEAN:
                return Values.SIG_PRIMITIVE_BOOLEAN;

            case Const.T_CHAR:
                return Values.SIG_PRIMITIVE_CHAR;

            case Const.T_FLOAT:
                return Values.SIG_PRIMITIVE_FLOAT;

            case Const.T_DOUBLE:
                return Values.SIG_PRIMITIVE_DOUBLE;

            case Const.T_BYTE:
                return Values.SIG_PRIMITIVE_BYTE;

            case Const.T_SHORT:
                return Values.SIG_PRIMITIVE_SHORT;

            case Const.T_INT:
                return Values.SIG_PRIMITIVE_INT;

            case Const.T_LONG:
                return Values.SIG_PRIMITIVE_LONG;
        }

        return Values.SIG_JAVA_LANG_OBJECT;
    }

    @Nullable
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
     * @param methodIsStatic
     *            if the method is static, causes where to start counting from, slot 0 or 1
     * @param methodSignature
     *            the signature of the method to parse
     * @return a map of parameter types (expect empty slots when doubles/longs are used
     */
    public static Map<Integer, String> getParameterSlotAndSignatures(boolean methodIsStatic, String methodSignature) {

        int start = methodSignature.indexOf('(') + 1;
        int limit = methodSignature.lastIndexOf(')');

        if ((limit - start) == 0) {
            return Collections.emptyMap();
        }

        Map<Integer, String> slotIndexToParms = new LinkedHashMap<>();
        int slot = methodIsStatic ? 0 : 1;
        int sigStart = start;
        for (int i = start; i < limit; i++) {
            if (!methodSignature.startsWith(Values.SIG_ARRAY_PREFIX, i)) {
                String parmSignature = null;
                if (methodSignature.startsWith(Values.SIG_QUALIFIED_CLASS_PREFIX, i)) {
                    int semiPos = methodSignature.indexOf(Values.SIG_QUALIFIED_CLASS_SUFFIX_CHAR, i + 1);
                    parmSignature = methodSignature.substring(sigStart, semiPos + 1);
                    slotIndexToParms.put(Integer.valueOf(slot), parmSignature);
                    i = semiPos;
                } else if (isWonkyEclipseSignature(methodSignature, i)) {
                    sigStart++;
                    continue;
                } else {
                    parmSignature = methodSignature.substring(sigStart, i + 1);
                    slotIndexToParms.put(Integer.valueOf(slot), parmSignature);
                }
                sigStart = i + 1;
                slot += getSignatureSize(parmSignature);
            }
        }

        return slotIndexToParms;
    }

    /**
     * returns a List of parameter signatures
     *
     * @param methodSignature
     *            the signature of the method to parse
     * @return a list of parameter signatures
     */
    public static List<String> getParameterSignatures(String methodSignature) {

        int start = methodSignature.indexOf('(') + 1;
        int limit = methodSignature.lastIndexOf(')');

        if ((limit - start) == 0) {
            return Collections.emptyList();
        }

        List<String> parmSignatures = new ArrayList<>();
        int sigStart = start;
        for (int i = start; i < limit; i++) {
            if (!methodSignature.startsWith(Values.SIG_ARRAY_PREFIX, i)) {
                if (methodSignature.startsWith(Values.SIG_QUALIFIED_CLASS_PREFIX, i)) {
                    int semiPos = methodSignature.indexOf(Values.SIG_QUALIFIED_CLASS_SUFFIX_CHAR, i + 1);
                    parmSignatures.add(methodSignature.substring(sigStart, semiPos + 1));
                    i = semiPos;
                } else if (!isWonkyEclipseSignature(methodSignature, i)) {
                    parmSignatures.add(methodSignature.substring(sigStart, i + 1));
                }
                sigStart = i + 1;
            }
        }

        return parmSignatures;
    }

    /**
     * gets the return type signature from a method signature
     *
     * @param methodSig
     *            the signature of the method
     *
     * @return the signature of the return type, or ? if a bogus method signature is given
     *
     */
    public static String getReturnSignature(String methodSig) {
        int parenPos = methodSig.indexOf(')');
        if (parenPos < 0) {
            return "?";
        }

        return methodSig.substring(parenPos + 1);
    }

    /**
     * returns the number of parameters in this method signature
     *
     * @param methodSignature
     *            the method signature to parse
     * @return the number of parameters
     */
    public static int getNumParameters(String methodSignature) {
        int start = methodSignature.indexOf('(') + 1;
        int limit = methodSignature.lastIndexOf(')');

        if ((limit - start) == 0) {
            return 0;
        }

        int numParms = 0;
        for (int i = start; i < limit; i++) {
            if (!methodSignature.startsWith(Values.SIG_ARRAY_PREFIX, i)) {
                if (methodSignature.startsWith(Values.SIG_QUALIFIED_CLASS_PREFIX, i)) {
                    i = methodSignature.indexOf(Values.SIG_QUALIFIED_CLASS_SUFFIX_CHAR, i + 1);
                } else if (isWonkyEclipseSignature(methodSignature, i)) {
                    continue;
                }
                numParms++;
            }
        }

        return numParms;
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
                genSig = genSig.substring(0, bracketPos) + Values.SIG_QUALIFIED_CLASS_SUFFIX_CHAR;
            }

            if (!regParm.getSignature().equals(genSig) && !genSig.startsWith(Values.SIG_GENERIC_TEMPLATE)) {
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
            genReturnSig = genReturnSig.substring(0, bracketPos) + Values.SIG_QUALIFIED_CLASS_SUFFIX_CHAR;
        }

        return regReturnParms.getSignature().equals(genReturnSig) || genReturnSig.startsWith(Values.SIG_GENERIC_TEMPLATE);
    }

    public static int getSignatureSize(String signature) {
        return (TWO_SLOT_TYPES.contains(signature)) ? 2 : 1;
    }

    /**
     * converts a signature, like Ljava/lang/String; into a dotted class name.
     *
     * @param signature
     *            a class signature, must not be null
     *
     * @return the dotted class name
     */
    public static @DottedClassName String stripSignature(String signature) {
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
    public static @SlashedClassName String trimSignature(String signature) {
        if ((signature != null) && signature.startsWith(Values.SIG_QUALIFIED_CLASS_PREFIX) && signature.endsWith(Values.SIG_QUALIFIED_CLASS_SUFFIX)) {
            return signature.substring(1, signature.length() - 1);
        }

        return signature;
    }

    /**
     * returns a slashed or dotted class name into a signature, like java/lang/String -- Ljava/lang/String; Primitives and arrays are accepted.
     *
     * @param className
     *            the class name to convert
     * @return the signature format of the class
     */
    public static String classToSignature(String className) {
        if (PRIMITIVE_TYPES.contains(className) || className.endsWith(Values.SIG_QUALIFIED_CLASS_SUFFIX)) {
            return className;
        } else if (className.startsWith(Values.SIG_ARRAY_PREFIX)) {
            // convert the classname inside the array
            return Values.SIG_ARRAY_PREFIX + classToSignature(className.substring(Values.SIG_ARRAY_PREFIX.length()));
        } else {
            return Values.SIG_QUALIFIED_CLASS_PREFIX + className.replace('.', '/') + Values.SIG_QUALIFIED_CLASS_SUFFIX_CHAR;
        }
    }

    /**
     * Converts a type name into an array signature. Accepts slashed or dotted classnames, or type signatures.
     *
     * @param typeName
     *            the class name to generate an array signature from
     *
     * @return the array signature
     */
    public static String toArraySignature(String typeName) {
        String sig = classToSignature(typeName);
        if ((sig == null) || (sig.length() == 0)) {
            return sig;
        }
        return Values.SIG_ARRAY_PREFIX + sig;
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
            buffer.append(Values.INNER_CLASS_SEPARATOR).append(components[i]);
        }
        return buffer.toString();
    }

    public static boolean isPlainStringConvertableClass(String className) {
        return Values.SLASHED_JAVA_LANG_STRINGBUILDER.equals(className) || Values.SLASHED_JAVA_LANG_STRINGBUFFER.equals(className)
                || Values.SLASHED_JAVA_UTIL_UUID.equals(className);
    }

    /**
     * Eclipse makes weird class signatures.
     *
     * @param sig
     *            the signature in type table
     * @param startIndex
     *            the index into the signature where the wonkyness begins
     *
     * @return if this signature has eclipse meta chars
     */
    private static boolean isWonkyEclipseSignature(String sig, int startIndex) {
        return (sig.length() > startIndex) && (ECLIPSE_WEIRD_SIG_CHARS.indexOf(sig.charAt(startIndex)) >= 0);
    }

}
