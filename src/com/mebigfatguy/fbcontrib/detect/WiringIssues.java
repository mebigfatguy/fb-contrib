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
import java.util.Map;

import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.Detector;
import edu.umd.cs.findbugs.FieldAnnotation;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.visitclass.PreorderVisitor;

public class WiringIssues extends PreorderVisitor implements Detector {

    private static final String SPRING_AUTOWIRED = "Lorg/springframework/beans/factory/annotation/Autowired;";
    private BugReporter bugReporter;

    public WiringIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {

        try {
            JavaClass cls = classContext.getJavaClass();

            Field[] fields = cls.getFields();

            if (fields.length > 0) {

                Map<WiringType, FieldAnnotation> wiredFields = new HashMap<>();
                boolean loadedParents = false;

                for (Field field : fields) {
                    for (AnnotationEntry entry : field.getAnnotationEntries()) {
                        if (SPRING_AUTOWIRED.equals(entry.getAnnotationType())) {
                            if (!loadedParents) {
                                loadParentAutowireds(cls.getSuperClass(), wiredFields);
                                loadedParents = true;
                            }
                            WiringType wt = new WiringType(field.getSignature(), false, null);
                            FieldAnnotation existingAnnotation = wiredFields.get(wt);
                            if (existingAnnotation != null) {
                                bugReporter.reportBug(new BugInstance(this, BugType.WI_DUPLICATE_WIRED_TYPES.name(), NORMAL_PRIORITY).addClass(this)
                                        .addField(existingAnnotation));
                                wiredFields.remove(wt);
                            } else {
                                wiredFields.put(wt, FieldAnnotation.fromBCELField(cls.getClassName(), field));
                            }
                        }
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            bugReporter.reportMissingClass(e);
        }
    }

    @Override
    public void report() {
    }

    private void loadParentAutowireds(JavaClass cls, Map<WiringType, FieldAnnotation> wiredFields) throws ClassNotFoundException {

        if (Values.DOTTED_JAVA_LANG_OBJECT.equals(cls.getClassName())) {
            return;
        }

        loadParentAutowireds(cls.getSuperClass(), wiredFields);

        Field[] fields = cls.getFields();

        if (fields.length > 0) {

            for (Field field : fields) {
                for (AnnotationEntry entry : field.getAnnotationEntries()) {
                    if (SPRING_AUTOWIRED.equals(entry.getAnnotationType())) {
                        WiringType wt = new WiringType(field.getSignature(), false, null);
                        wiredFields.put(wt, FieldAnnotation.fromBCELField(cls.getClassName(), field));
                    }
                }
            }
        }
    }

    static class WiringType {
        String signature;
        boolean singleton;
        String qualifier;

        public WiringType(String fieldSignature, boolean isSingleton, String qualifierName) {
            signature = fieldSignature;
            singleton = isSingleton;
            qualifier = qualifierName;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof WiringType)) {
                return false;
            }

            WiringType that = (WiringType) o;
            return signature.equals(that.signature);
        }

        @Override
        public int hashCode() {
            return signature.hashCode();
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }

    }
}
