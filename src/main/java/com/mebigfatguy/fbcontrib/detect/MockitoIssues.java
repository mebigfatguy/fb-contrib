package com.mebigfatguy.fbcontrib.detect;

import java.util.HashSet;
import java.util.Set;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.ArrayElementValue;
import org.apache.bcel.classfile.ElementValue;
import org.apache.bcel.classfile.ElementValuePair;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.ba.ClassContext;

public class MockitoIssues extends BytecodeScanningDetector {

    private BugReporter bugReporter;
    private JavaClass cls;
    private boolean sawMockitoExtension;
    private boolean sawAnnotatedField;
    private Set<String> mockedFields;
    private Set<String> spiedFields;

    /**
     * constructs a MI detector given the reporter to report bugs on
     *
     * @param bugReporter the sync of bug reports
     */
    public MockitoIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
        mockedFields = new HashSet<>();
        spiedFields = new HashSet<>();
    }

    /**
     * implements the visitor to find various issues with use of Mockito,
     * specifically looks for classes annotated with @ExtendsWith
     *
     * @param clsContext the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext clsContext) {
        try {
            cls = clsContext.getJavaClass();
            sawMockitoExtension = false;
            sawAnnotatedField = false;
            outer: for (AnnotationEntry ann : cls.getAnnotationEntries()) {
                if (ann.isRuntimeVisible() && "org.junit.jupiter.api.extension.ExtendWith".equals(SignatureUtils.stripSignature(ann.getAnnotationType()))) {
                    for (ElementValuePair evp : ann.getElementValuePairs()) {
                        if ("value".equals(evp.getNameString()) && evp.getValue() instanceof ArrayElementValue) {
                            ArrayElementValue aev = (ArrayElementValue) evp.getValue();
                            for (ElementValue sev : aev.getElementValuesArray()) {
                                String annotationClsAttr = SignatureUtils.stripSignature(sev.stringifyValue());

                                if ("org.mockito.junit.jupiter.MockitoExtension".equals(annotationClsAttr)) {
                                    sawMockitoExtension = true;
                                    break outer;
                                }
                            }
                        }
                    }
                }
            }
            super.visitClassContext(clsContext);
        } finally {
            mockedFields.clear();
            spiedFields.clear();
        }
    }

    @Override
    public void visitField(Field obj) {
        if (!obj.getSignature().startsWith("L")) {
            return;
        }

        for (AnnotationEntry ann : obj.getAnnotationEntries()) {
            if (ann.isRuntimeVisible()) {
                if ("Lorg/mockito/Mock;".equals(ann.getAnnotationType())) {
                    mockedFields.add(obj.getName());
                    sawAnnotatedField = true;
                } else if ("Lorg/mockito/Spy;".equals(ann.getAnnotationType())) {
                    spiedFields.add(obj.getName());
                    sawAnnotatedField = true;
                }
            }
        }
    }

    @Override
    public void sawOpcode(int seen) {
        switch (seen) {
        case Const.INVOKESTATIC:
            if (sawMockitoExtension) {
                String clsName = getClassConstantOperand();
                if ("org/mockito/MockitoAnnotations".equals(clsName)) {
                    String methodName = getNameConstantOperand();
                    if ("openMocks".equals(methodName)) {
                        bugReporter.reportBug(new BugInstance(this, BugType.MK_UNNEEDED_OPENMOCKS.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                                .addSourceLine(this));
                    }
                }
            }
            break;

        case Const.PUTFIELD:
            if (sawAnnotatedField) {
                String clsName = getClassConstantOperand().replace('/', '.');
                if (clsName.equals(cls.getClassName())) {
                    String fieldName = getNameConstantOperand();
                    if (mockedFields.contains(fieldName)) {
                        bugReporter.reportBug(new BugInstance(this, BugType.MK_MOCKED_FIELD_REASSIGNED.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                                .addSourceLine(this));
                    } else if (spiedFields.contains(fieldName)) {
                        bugReporter.reportBug(new BugInstance(this, BugType.MK_SPIED_FIELD_REASSIGNED.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                                .addSourceLine(this));

                    }
                }
            }
            break;
        }
    }

}
