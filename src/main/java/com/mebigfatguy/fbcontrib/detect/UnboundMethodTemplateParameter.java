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
package com.mebigfatguy.fbcontrib.detect;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.bcel.classfile.Attribute;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.classfile.Signature;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.Detector;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.visitclass.PreorderVisitor;

/**
 * Looks for methods that declare method level template parameter(s) that are not bound to any of the method's parameters, and thus is not adding any
 * validation/type safety to the method, and is just confusing.
 */
public class UnboundMethodTemplateParameter extends PreorderVisitor implements Detector {

    private static final Pattern TEMPLATED_SIGNATURE = Pattern.compile("(\\<[^\\>]+\\>)(.+)");
    private static final Pattern TEMPLATE = Pattern.compile("\\<?([^:]+):([^;]*);");
    private final BugReporter bugReporter;

    public UnboundMethodTemplateParameter(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to accept the class for visiting
     *
     * @param classContext
     *            the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        JavaClass cls = classContext.getJavaClass();
        cls.accept(this);
    }

    /**
     * implements the visitor to find methods that declare template parameters that are not bound to any parameter.
     *
     * @param obj
     *            the context object of the currently parsed method
     */
    @Override
    public void visitMethod(Method obj) {
        Attribute[] attributes = obj.getAttributes();
        for (Attribute a : attributes) {
            if ("Signature".equals(a.getName())) {
                TemplateSignature ts = parseSignatureAttribute((Signature) a);
                if (ts != null) {
                    for (TemplateItem templateParm : ts.templateParameters) {
                        if (!ts.signature.contains(Values.SIG_GENERIC_TEMPLATE + templateParm.templateType + Values.SIG_QUALIFIED_CLASS_SUFFIX_CHAR)
                                && !isTemplateParent(templateParm.templateType, ts.templateParameters)) {
                            bugReporter.reportBug(new BugInstance(this, BugType.UMTP_UNBOUND_METHOD_TEMPLATE_PARAMETER.name(), NORMAL_PRIORITY).addClass(this)
                                    .addMethod(this).addString("Template Parameter: " + templateParm.templateType));
                            return;
                        }
                    }
                }
                return;
            }
        }
    }

    @Override
    public void report() {
        // not used, part of the Detector interface
    }

    /**
     * looks to see if this templateType is a parent of another template type
     *
     * @param templateType
     *            the type to look for
     * @param items
     *            the items to search
     * @return whether this template type is something another template type extends
     */
    private boolean isTemplateParent(String templateType, TemplateItem... items) {
        for (TemplateItem item : items) {
            if (templateType.equals(item.templateExtension)) {
                return true;
            }
        }

        return false;
    }

    /**
     * builds a template signature object based on the signature attribute of the method
     *
     * @param signatureAttribute
     *            the signature attribute
     * @return a template signature if there are templates defined, otherwise null
     */
    @Nullable
    private static TemplateSignature parseSignatureAttribute(Signature signatureAttribute) {

        Matcher m = TEMPLATED_SIGNATURE.matcher(signatureAttribute.getSignature());
        if (!m.matches()) {
            return null;
        }

        TemplateSignature ts = new TemplateSignature();
        ts.signature = m.group(2);

        m = TEMPLATE.matcher(m.group(1));
        List<TemplateItem> templates = new ArrayList<>(4);
        while (m.find()) {
            templates.add(new TemplateItem(m.group(1), m.group(2)));
        }
        ts.templateParameters = templates.toArray(new TemplateItem[0]);

        return ts;
    }

    /**
     * a simple data only class for holding the template parameters and method signature
     */
    static class TemplateSignature {
        TemplateItem[] templateParameters;
        String signature;

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }

    static class TemplateItem {
        String templateType;
        String templateExtension;

        public TemplateItem(String type, String extension) {
            templateType = type;
            templateExtension = extension.startsWith(Values.SIG_QUALIFIED_CLASS_PREFIX) ? "" : extension.substring(1);
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }
}
