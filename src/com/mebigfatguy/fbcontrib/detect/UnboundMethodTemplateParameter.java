/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2013 Dave Brosius
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

import org.apache.bcel.classfile.Attribute;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.classfile.Signature;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.Detector;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.visitclass.PreorderVisitor;

/**
 * Looks for methods that declare method level template parameter(s) that are not bound to any of the
 * method's parameters, and thus is not adding any validation/type safety to the method, and is
 * just confusing.
 */
public class UnboundMethodTemplateParameter extends PreorderVisitor implements Detector {

    private static final Pattern TEMPLATED_SIGNATURE = Pattern.compile("(\\<[^\\>]+\\>)(.+)");
    private static final Pattern TEMPLATE = Pattern.compile("\\<?([^:]+):[^;]*;");
    private BugReporter bugReporter;


    public UnboundMethodTemplateParameter(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to accept the class for visiting
     *
     * @param classContext the context object of the currently parsed class
     */
    public void visitClassContext(ClassContext classContext) {
        JavaClass cls = classContext.getJavaClass();
        cls.accept(this);
    }

    /**
     * implements the visitor to find methods that declare template parameters
     * that are not bound to any parameter.
     *
     * @param obj the context object of the currently parsed method
     */
    @Override
    public void visitMethod(Method obj) {
        Attribute[] attributes = obj.getAttributes();
        for (Attribute a : attributes) {
            if (a.getName().equals("Signature")) {
                TemplateSignature ts = parseSignatureAttribute((Signature) a);
                if (ts != null) {
                    for (String templateParm : ts.templateParameters) {
                        if (!ts.signature.contains("T" + templateParm + ";") && !ts.signature.contains("[T" + templateParm + ";")) {
                            bugReporter.reportBug(new BugInstance(this, "UMTP_UNBOUND_METHOD_TEMPLATE_PARAMETER", NORMAL_PRIORITY)
                                        .addClass(this)
                                        .addMethod(this)
                                        .addString("Template Parameter: " + templateParm));
                            return;
                        }
                    }
                }
                return;
            }
        }
    }

    public void report() {
    }

    /**
     * builds a template signature object based on the signature attribute of the method
     *
     * @param signatureAttribute the signature attribute
     * @return a template signature if there are templates defined, otherwise null
     */
    private TemplateSignature parseSignatureAttribute(Signature signatureAttribute) {

        Matcher m = TEMPLATED_SIGNATURE.matcher(signatureAttribute.getSignature());
        if (m.matches()) {
            TemplateSignature ts = new TemplateSignature();
            ts.signature = m.group(2);

            String template = m.group(1);

            m = TEMPLATE.matcher(template);
            List<String> templates = new ArrayList<String>();
            while (m.find()) {
                templates.add(m.group(1));
            }
            ts.templateParameters = templates.toArray(new String[templates.size()]);

            return ts;
        }

        return null;
    }

    /**
     * a simple data only class for holding the template parameters and method signature
     */
    static class TemplateSignature {
        String[] templateParameters;
        String signature;
    }
}
