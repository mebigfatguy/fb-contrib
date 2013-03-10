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

public class UnboundMethodTemplateParameter extends PreorderVisitor implements Detector {

    private static final Pattern TEMPLATED_SIGNATURE = Pattern.compile("(\\<[^\\>]+\\>)(.+)");
    private static final Pattern TEMPLATE = Pattern.compile("\\<?([^:]+):[^;]*;");
    private BugReporter bugReporter;


    public UnboundMethodTemplateParameter(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    public void visitClassContext(ClassContext classContext) {
        JavaClass cls = classContext.getJavaClass();
        cls.accept(this);
    }

    @Override
    public void visitMethod(Method obj) {
        Attribute[] attributes = obj.getAttributes();
        for (Attribute a : attributes) {
            if (a.getName().equals("Signature")) {
                TemplateSignature ts = parseSignatureAttribute((Signature) a);
                if (ts != null) {
                    for (String templateParm : ts.templateParameters) {
                        if (!ts.signature.contains("<T" + templateParm + ";>") && !ts.signature.contains("[T" + templateParm + ";")) {
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

    static class TemplateSignature {
        String[] templateParameters;
        String signature;
    }
}
