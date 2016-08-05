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
import java.util.Set;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.FieldAnnotation;
import edu.umd.cs.findbugs.SourceLineAnnotation;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for tag libraries that are not recycleable because backing members of taglib attributes are set in areas besides the setter method for the attribute.
 */
public class NonRecycleableTaglibs extends BytecodeScanningDetector {
    private static final int MAX_ATTRIBUTE_CODE_LENGTH = 60;

    private static final Set<String> tagClasses = UnmodifiableSet.create("javax.servlet.jsp.tagext.TagSupport", "javax.servlet.jsp.tagext.BodyTagSupport");

    private static final Set<String> validAttrTypes = UnmodifiableSet.create("B", "C", "D", "F", "I", "J", "S", "Z", "Ljava/lang/String;", "Ljava/util/Date;");

    private final BugReporter bugReporter;
    /**
     * methodname:methodsig to type of setter methods
     */
    private Map<String, String> attributes;
    /**
     * methodname:methodsig to (fieldname:fieldtype)s
     */
    private Map<String, Map<String, SourceLineAnnotation>> methodWrites;
    private Map<String, FieldAnnotation> fieldAnnotations;

    /**
     * constructs a NRTL detector given the reporter to report bugs on.
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public NonRecycleableTaglibs(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to look for classes that extend the TagSupport or BodyTagSupport class
     *
     * @param classContext
     *            the context object for the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            JavaClass cls = classContext.getJavaClass();
            JavaClass[] superClasses = cls.getSuperClasses();
            for (JavaClass superCls : superClasses) {
                if (tagClasses.contains(superCls.getClassName())) {
                    attributes = getAttributes(cls);

                    if (attributes.size() > 0) {
                        methodWrites = new HashMap<>();
                        fieldAnnotations = new HashMap<>();
                        super.visitClassContext(classContext);
                        reportBugs();
                    }
                    break;
                }
            }

        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        } finally {
            attributes = null;
            methodWrites = null;
            fieldAnnotations = null;
        }
    }

    /**
     * collect all possible attributes given the name of methods available.
     *
     * @param cls the class to look for setter methods to infer properties
     * @return the map of possible attributes/types
     */
    private static Map<String, String> getAttributes(JavaClass cls) {
        Map<String, String> atts = new HashMap<>();
        Method[] methods = cls.getMethods();
        for (Method m : methods) {
            String name = m.getName();
            if (name.startsWith("set") && m.isPublic() && !m.isStatic()) {
                String sig = m.getSignature();
                Type ret = Type.getReturnType(sig);
                Type[] args = Type.getArgumentTypes(sig);
                if ((args.length == 1) && ret.equals(Type.VOID)) {
                    String parmSig = args[0].getSignature();
                    if (validAttrTypes.contains(parmSig)) {
                        Code code = m.getCode();
                        if ((code != null) && (code.getCode().length < MAX_ATTRIBUTE_CODE_LENGTH)) {
                            atts.put(name + ':' + sig, parmSig);
                        }
                    }
                }
            }
        }
        return atts;
    }

    /**
     * implements the visitor to
     *
     * @param obj
     *            the context object for the currently parsed code object
     */
    @Override
    public void visitCode(Code obj) {
        Method m = getMethod();
        if (!Values.CONSTRUCTOR.equals(m.getName())) {
            super.visitCode(obj);
        }
    }

    /**
     * implements the visitor to record storing of fields, and where they occur
     *
     * @param seen
     *            the currently parsed opcode
     */
    @Override
    public void sawOpcode(int seen) {
        if (seen == PUTFIELD) {
            String methodInfo = getMethodName() + ':' + getMethodSig();
            Map<String, SourceLineAnnotation> fields = methodWrites.get(methodInfo);
            if (fields == null) {
                fields = new HashMap<>();
                methodWrites.put(methodInfo, fields);
            }
            String fieldName = getNameConstantOperand();
            String fieldSig = getSigConstantOperand();

            FieldAnnotation fa = new FieldAnnotation(getDottedClassName(), fieldName, fieldSig, false);
            fieldAnnotations.put(fieldName, fa);
            fields.put(fieldName + ':' + fieldSig, SourceLineAnnotation.fromVisitedInstruction(this));
        }
    }

    /**
     * generates all the bug reports for attributes that are not recycleable
     */
    private void reportBugs() {
        for (Map.Entry<String, String> attEntry : attributes.entrySet()) {
            String methodInfo = attEntry.getKey();
            String attType = attEntry.getValue();

            Map<String, SourceLineAnnotation> fields = methodWrites.get(methodInfo);
            if ((fields == null) || (fields.size() != 1)) {
                continue;
            }

            String fieldInfo = fields.keySet().iterator().next();
            int colonPos = fieldInfo.indexOf(':');
            String fieldName = fieldInfo.substring(0, colonPos);
            String fieldType = fieldInfo.substring(colonPos + 1);

            if (!attType.equals(fieldType)) {
                continue;
            }

            for (Map.Entry<String, Map<String, SourceLineAnnotation>> fwEntry : methodWrites.entrySet()) {
                if (fwEntry.getKey().equals(methodInfo)) {
                    continue;
                }

                SourceLineAnnotation sla = fwEntry.getValue().get(fieldInfo);
                if (sla != null) {
                    bugReporter.reportBug(new BugInstance(this, BugType.NRTL_NON_RECYCLEABLE_TAG_LIB.name(), NORMAL_PRIORITY).addClass(this)
                            .addField(fieldAnnotations.get(fieldName)).addSourceLine(sla));
                    break;
                }
            }
        }
    }
}
