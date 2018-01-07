/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2018 Dave Brosius
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

import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.ToString;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.Detector;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for classes that don't handle serialization of parent class member fields when the class in question is serializable but is derived from non
 * serializable classes.
 */
public class PossibleIncompleteSerialization implements Detector {

    public static final String SIG_OBJECT_OUTPUT_STREAM_TO_VOID = new SignatureBuilder().withParamTypes("java/io/ObjectOutputStream").toString();
    public static final String SIG_OBJECT_OUTPUT_TO_VOID = new SignatureBuilder().withParamTypes("java/io/ObjectOutput").toString();

    private final BugReporter bugReporter;

    /**
     * constructs a PIS detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public PossibleIncompleteSerialization(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to look for classes that are serializable, and are derived from non serializable classes and don't either implement methods in
     * Externalizable or Serializable to save parent class fields.
     *
     * @param classContext
     *            the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            JavaClass cls = classContext.getJavaClass();
            if (isSerializable(cls)) {
                JavaClass superCls = cls.getSuperClass();
                if (!isSerializable(superCls) && hasSerializableFields(superCls) && !hasSerializingMethods(cls)) {
                    bugReporter.reportBug(new BugInstance(this, BugType.PIS_POSSIBLE_INCOMPLETE_SERIALIZATION.name(), NORMAL_PRIORITY).addClass(cls));
                }
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        }
    }

    /**
     * returns if the class implements Serializable or Externalizable
     *
     * @param cls
     *            the class to check for interfaces
     *
     * @return if the class implements Serializable or Externalizable
     *
     * @throws ClassNotFoundException
     *             if a super class or super interfaces can't be found
     */
    private static boolean isSerializable(JavaClass cls) throws ClassNotFoundException {
        JavaClass[] infs = cls.getAllInterfaces();
        for (JavaClass inf : infs) {
            String clsName = inf.getClassName();
            if ("java.io.Serializable".equals(clsName) || "java.io.Externalizable".equals(clsName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * looks for fields that are candidates for serialization
     *
     * @param cls
     *            the class to look for fields
     * @return if their is a field that looks like it should be serialized
     */
    private static boolean hasSerializableFields(JavaClass cls) {
        Field[] fields = cls.getFields();
        for (Field f : fields) {
            if (!f.isStatic() && !f.isTransient() && !f.isSynthetic()) {
                return true;
            }
        }
        return false;
    }

    /**
     * looks to see if this class implements method described by Serializable or Externalizable
     *
     * @param cls
     *            the class to examine for serializing methods
     * @return whether the class handles it's own serializing/externalizing
     */
    private static boolean hasSerializingMethods(JavaClass cls) {
        Method[] methods = cls.getMethods();
        for (Method m : methods) {
            if (!m.isStatic()) {
                String methodName = m.getName();
                String methodSig = m.getSignature();
                if ("writeObject".equals(methodName) && SIG_OBJECT_OUTPUT_STREAM_TO_VOID.equals(methodSig)) {
                    return true;
                }
                if ("writeExternal".equals(methodName) && SIG_OBJECT_OUTPUT_TO_VOID.equals(methodSig)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void report() {
        // Unused, requirement of the Detector interface
    }

    @Override
    public String toString() {
        return ToString.build(this);
    }
}
