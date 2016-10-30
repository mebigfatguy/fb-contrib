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
package com.mebigfatguy.fbcontrib.detect;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.Detector;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.visitclass.PreorderVisitor;

/**
 * looks for methods that have the same signature, except where one uses a Character parameter, and the other uses an int, long, float, double parameter. Since
 * autoboxing is available in 1.5 one might assume that
 *
 * <pre>
 * test('a')
 * </pre>
 *
 * would map to
 *
 * <pre>
 * public void test(Character c)
 * </pre>
 *
 * but instead maps to one that takes an int, long, float or double.
 */
public class ConfusingAutoboxedOverloading extends PreorderVisitor implements Detector {
    private static final int JDK15_MAJOR = 49;

    private static final Set<String> primitiveSigs = UnmodifiableSet.create("I", "J", "D", "F");

    private final BugReporter bugReporter;

    /**
     * constructs a CAO detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public ConfusingAutoboxedOverloading(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * overrides the visitor to look for confusing signatures
     *
     * @param classContext
     *            the context object that holds the JavaClass currently being parsed
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        JavaClass cls = classContext.getJavaClass();

        if (cls.isClass() && (cls.getMajor() >= JDK15_MAJOR)) {

            Map<String, Set<String>> methodInfo = new HashMap<>();
            populateMethodInfo(cls, methodInfo);

            Method[] methods = cls.getMethods();
            for (Method m : methods) {
                String name = m.getName();
                String signature = m.getSignature();

                Set<String> sigs = methodInfo.get(name);
                if (sigs != null) {
                    for (String sig : sigs) {
                        if (confusingSignatures(sig, signature)) {
                            bugReporter.reportBug(new BugInstance(this, BugType.CAO_CONFUSING_AUTOBOXED_OVERLOADING.name(), NORMAL_PRIORITY)
                                    .addClass(cls.getClassName()).addString(name + signature).addString(name + sig));
                        }
                    }
                }
            }
        }
    }

    /**
     * returns if one signature is a Character and the other is a primitive
     *
     * @param sig1
     *            the first method signature
     * @param sig2
     *            the second method signature
     *
     * @return if one signature is a Character and the other a primitive
     */
    private static boolean confusingSignatures(String sig1, String sig2) {
        if (sig1.equals(sig2)) {
            return false;
        }

        List<String> type1 = SignatureUtils.getParameterSignatures(sig1);
        List<String> type2 = SignatureUtils.getParameterSignatures(sig2);

        if (type1.size() != type2.size()) {
            return false;
        }

        boolean foundParmDiff = false;
        for (int i = 0; i < type1.size(); i++) {
            String typeOneSig = type1.get(i);
            String typeTwoSig = type2.get(i);

            if (!typeOneSig.equals(typeTwoSig)) {
                if ("Ljava/lang/Character;".equals(typeOneSig)) {
                    if (!primitiveSigs.contains(typeTwoSig)) {
                        return false;
                    }
                } else if ("Ljava/lang/Character;".equals(typeTwoSig)) {
                    if (!primitiveSigs.contains(typeOneSig)) {
                        return false;
                    }
                } else {
                    return false;
                }
                foundParmDiff = true;
            }
        }

        return foundParmDiff;
    }

    /**
     * fills out a set of method details for possibly confusing method signatures
     *
     * @param cls
     *            the current class being parsed
     * @param methodInfo
     *            a collection to hold possibly confusing methods
     */
    private void populateMethodInfo(JavaClass cls, Map<String, Set<String>> methodInfo) {
        try {
            if (Values.DOTTED_JAVA_LANG_OBJECT.equals(cls.getClassName())) {
                return;
            }

            Method[] methods = cls.getMethods();
            for (Method m : methods) {
                String sig = m.getSignature();
                if (isPossiblyConfusingSignature(sig)) {

                    String name = m.getName();
                    Set<String> sigs = methodInfo.get(name);
                    if (sigs == null) {
                        sigs = new HashSet<>(3);
                        methodInfo.put(name, sigs);
                    }
                    sigs.add(m.getSignature());
                }
            }

            populateMethodInfo(cls.getSuperClass(), methodInfo);
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        }
    }

    /**
     * returns whether a method signature has either a Character or primitive
     *
     * @param sig
     *            the method signature
     *
     * @return whether a method signature has either a Character or primitive
     */
    private static boolean isPossiblyConfusingSignature(String sig) {
        List<String> types = SignatureUtils.getParameterSignatures(sig);
        for (String typeSig : types) {
            if (primitiveSigs.contains(typeSig) || SignatureUtils.classToSignature(Values.SLASHED_JAVA_LANG_CHARACTER).equals(typeSig)) {
                return true;
            }
        }
        return false;
    }

    /**
     * implements the detector with null implementation
     */
    @Override
    public void report() {
        // nothing to do here
    }
}
