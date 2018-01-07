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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.collect.Statistics;
import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.FieldAnnotation;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for various issues around @Autowired/@Inject fields in DI classes
 * <ul>
 * <li>Injecting the same bean twice into the same class hierarchy, even with different field names</li>
 * </ul>
 */
public class WiringIssues extends BytecodeScanningDetector {

    private static final String SPRING_AUTOWIRED = "Lorg/springframework/beans/factory/annotation/Autowired;";
    private static final String SPRING_QUALIFIER = "Lorg/springframework/beans/factory/annotation/Qualifier;";

    private BugReporter bugReporter;
    private OpcodeStack stack;

    /**
     * constructs a WI detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public WiringIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "SF_SWITCH_NO_DEFAULT", justification = "Only a few cases need special handling")
    @Override
    public void visitClassContext(ClassContext classContext) {

        try {
            JavaClass cls = classContext.getJavaClass();

            Field[] fields = cls.getFields();

            if (fields.length > 0) {

                Map<WiringType, FieldAnnotation> wiredFields = new HashMap<>();
                boolean loadedParents = false;

                for (Field field : fields) {
                    boolean hasAutowired = false;
                    String qualifier = "";
                    for (AnnotationEntry entry : field.getAnnotationEntries()) {
                        switch (entry.getAnnotationType()) {
                            case SPRING_AUTOWIRED:
                                if (!loadedParents) {
                                    loadParentAutowireds(cls.getSuperClass(), wiredFields);
                                    loadedParents = true;
                                }
                                hasAutowired = true;
                            break;

                            case SPRING_QUALIFIER:
                                qualifier = entry.getElementValuePairs()[0].getValue().stringifyValue();
                            break;
                        }
                    }

                    if (hasAutowired) {
                        WiringType wt = new WiringType(field.getSignature(), field.getGenericSignature(), qualifier);
                        FieldAnnotation existingAnnotation = wiredFields.get(wt);
                        if (existingAnnotation == null) {
                            wiredFields.put(wt, FieldAnnotation.fromBCELField(cls.getClassName(), field));
                        } else {
                            bugReporter.reportBug(new BugInstance(this, BugType.WI_DUPLICATE_WIRED_TYPES.name(), NORMAL_PRIORITY).addClass(cls)
                                    .addField(FieldAnnotation.fromBCELField(cls, field)).addField(existingAnnotation));
                            wiredFields.remove(wt);
                        }
                    }
                }
            }

            stack = new OpcodeStack();
            super.visitClassContext(classContext);
        } catch (ClassNotFoundException e) {
            bugReporter.reportMissingClass(e);
        } finally {
            stack = null;
        }
    }

    @Override
    public void visitCode(Code obj) {
        Method m = getMethod();
        for (AnnotationEntry annotation : m.getAnnotationEntries()) {
            String type = annotation.getAnnotationType();
            if (type.startsWith("Lorg/junit/") || type.startsWith("Lorg/testng/")) {
                return;
            }
        }
        stack.resetForMethodEntry(this);
        super.visitCode(obj);
    }

    @Override
    public void sawOpcode(int seen) {
        try {
            if ((seen == INVOKESPECIAL) && Values.CONSTRUCTOR.equals(getNameConstantOperand())) {
                String clsName = getClassConstantOperand();
                if (Statistics.getStatistics().isAutowiredBean(clsName.replace('/', '.'))) {
                    String signature = getSigConstantOperand();
                    int numParms = SignatureUtils.getNumParameters(signature);
                    if (stack.getStackDepth() > numParms) {
                        OpcodeStack.Item itm = stack.getStackItem(numParms);
                        if (itm.getRegisterNumber() != 0) {
                            bugReporter.reportBug(new BugInstance(this, BugType.WI_MANUALLY_ALLOCATING_AN_AUTOWIRED_BEAN.name(), NORMAL_PRIORITY).addClass(this)
                                    .addMethod(this).addSourceLine(this));
                        }
                    }
                }
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    /**
     * loads all the types that are injected by @Autowired annotations in super classes
     *
     * @param cls
     *            the class who's parents you want to load
     * @param wiredFields
     *            the collected map of autowired types
     * @throws ClassNotFoundException
     *             if a parent class can't be loaded
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "SF_SWITCH_NO_DEFAULT", justification = "Only a few cases need special handling")
    private void loadParentAutowireds(JavaClass cls, Map<WiringType, FieldAnnotation> wiredFields) throws ClassNotFoundException {

        if (Values.DOTTED_JAVA_LANG_OBJECT.equals(cls.getClassName())) {
            return;
        }

        loadParentAutowireds(cls.getSuperClass(), wiredFields);

        Field[] fields = cls.getFields();

        if (fields.length > 0) {

            for (Field field : fields) {
                boolean hasAutowired = false;
                String qualifier = "";
                for (AnnotationEntry entry : field.getAnnotationEntries()) {
                    switch (entry.getAnnotationType()) {
                        case SPRING_AUTOWIRED:
                            hasAutowired = true;
                        break;

                        case SPRING_QUALIFIER:
                            qualifier = entry.getElementValuePairs()[0].getValue().stringifyValue();
                        break;
                    }
                }

                if (hasAutowired) {
                    WiringType wt = new WiringType(field.getSignature(), field.getGenericSignature(), qualifier);
                    wiredFields.put(wt, FieldAnnotation.fromBCELField(cls.getClassName(), field));
                }
            }
        }
    }

    /**
     * represents the type of object that is to be wired in, including an optional qualifier name
     */
    static class WiringType {
        String signature;
        String genericSignature;
        String qualifier;

        public WiringType(String fieldSignature, String genSignature, String qualifierName) {
            signature = fieldSignature;
            genericSignature = genSignature;
            qualifier = qualifierName;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof WiringType)) {
                return false;
            }

            WiringType that = (WiringType) o;
            return signature.equals(that.signature) && Objects.equals(genericSignature, that.genericSignature) && qualifier.equals(that.qualifier);
        }

        @Override
        public int hashCode() {
            return signature.hashCode() ^ qualifier.hashCode() ^ ((genericSignature == null) ? 0 : genericSignature.hashCode());
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }

    }
}
