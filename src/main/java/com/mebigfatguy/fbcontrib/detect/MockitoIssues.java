package com.mebigfatguy.fbcontrib.detect;

import java.util.HashSet;
import java.util.Set;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.ArrayElementValue;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.ElementValue;
import org.apache.bcel.classfile.ElementValuePair;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

@CustomUserValue
public class MockitoIssues extends BytecodeScanningDetector {

    enum MKUserValue {
        CAPTOR, MOCK, SPY, SPIED_OBJ
    };

    private BugReporter bugReporter;
    private JavaClass cls;
    private OpcodeStack stack;
    private boolean sawMockitoExtension;
    private boolean sawAnnotatedField;
    private boolean inBeforeEach;
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
            stack = new OpcodeStack();
            sawMockitoExtension = false;
            sawAnnotatedField = false;
            outer: for (AnnotationEntry ann : cls.getAnnotationEntries()) {
                if (ann.isRuntimeVisible()) {
                    if ("org.junit.jupiter.api.extension.ExtendWith".equals(SignatureUtils.stripSignature(ann.getAnnotationType()))) {
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
                    } else if ("org.testng.annotations.Listeners".equals(SignatureUtils.stripSignature(ann.getAnnotationType()))) {
                        for (ElementValuePair evp : ann.getElementValuePairs()) {
                            if ("value".equals(evp.getNameString()) && evp.getValue() instanceof ArrayElementValue) {
                                ArrayElementValue aev = (ArrayElementValue) evp.getValue();
                                for (ElementValue sev : aev.getElementValuesArray()) {
                                    String annotationClsAttr = SignatureUtils.stripSignature(sev.stringifyValue());
                                    if ("org.mockito.testng.MockitoTestNGListener".equals(annotationClsAttr)) {
                                        sawMockitoExtension = true;
                                        break outer;
                                    }
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
            stack = null;
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
    public void visitMethod(Method obj) {
        inBeforeEach = false;
        if (sawMockitoExtension) {
            for (AnnotationEntry ae : obj.getAnnotationEntries()) {
                if ("Lorg/junit/jupiter/api/BeforeEach;".equals(ae.getAnnotationType())) {
                    inBeforeEach = true;
                    break;
                }
            }
        }
        super.visitMethod(obj);
    }

    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        super.visitCode(obj);
    }

    @Override
    public void sawOpcode(int seen) {
        MKUserValue userValue = null;
        try {
            switch (seen) {
            case Const.INVOKESTATIC: {
                String clsName = getClassConstantOperand();
                if (sawMockitoExtension) {
                    if ("org/mockito/MockitoAnnotations".equals(clsName)) {
                        String methodName = getNameConstantOperand();
                        if ("openMocks".equals(methodName)) {
                            bugReporter.reportBug(new BugInstance(this, BugType.MK_UNNEEDED_OPENMOCKS.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                                    .addSourceLine(this));
                        }
                    }
                }
                String methodName = getNameConstantOperand();
                if ("org/mockito/Mockito".equals(clsName)) {
                    if ("mock".equals(methodName)) {
                        userValue = MKUserValue.MOCK;
                    } else if ("spy".equals(methodName)) {
                        userValue = MKUserValue.SPY;
                    } else if ("when".equals(getNameConstantOperand())) {
                        if (stack.getStackDepth() > 0) {
                            OpcodeStack.Item itm = stack.getStackItem(0);
                            if (itm.getUserValue() == MKUserValue.SPIED_OBJ) {
                                bugReporter.reportBug(new BugInstance(this, BugType.MK_USE_DORETURN.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                                        .addSourceLine(this));
                            }
                        }

                        userValue = processCaptor();
                        if (userValue == MKUserValue.CAPTOR) {
                            bugReporter.reportBug(new BugInstance(this, BugType.MK_CAPTOR_IN_WHEN.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                                    .addSourceLine(this));
                        }
                    }
                } else {
                    userValue = processCaptor();
                }
                break;
            }

            case Const.INVOKEVIRTUAL: {
                String clsName = getClassConstantOperand();
                if ("org/mockito/ArgumentCaptor".equals(clsName)) {
                    String methodName = getNameConstantOperand();
                    if ("capture".equals(methodName)) {
                        userValue = MKUserValue.CAPTOR;
                    }
                } else {
                    userValue = processCaptor();
                }
                if (userValue == null) {
                    userValue = checkForSpiedObjectCall();
                }
                break;
            }

            case Const.INVOKEINTERFACE:
            case Const.INVOKESPECIAL: {
                userValue = processCaptor();
                if (userValue == null) {
                    userValue = checkForSpiedObjectCall();
                }
                break;
            }

            case Const.PUTFIELD: {
                if (sawAnnotatedField) {
                    String clsName = getClassConstantOperand().replace('/', '.');
                    if (clsName.equals(cls.getClassName())) {
                        String fieldName = getNameConstantOperand();
                        if (mockedFields.contains(fieldName)) {
                            bugReporter.reportBug(new BugInstance(this, BugType.MK_MOCKED_FIELD_REASSIGNED.name(), NORMAL_PRIORITY).addClass(this)
                                    .addMethod(this).addSourceLine(this));
                            return;
                        } else if (spiedFields.contains(fieldName)) {
                            bugReporter.reportBug(new BugInstance(this, BugType.MK_SPIED_FIELD_REASSIGNED.name(), NORMAL_PRIORITY).addClass(this)
                                    .addMethod(this).addSourceLine(this));
                            return;
                        }

                        if (inBeforeEach) {
                            if (stack.getStackDepth() > 0) {
                                OpcodeStack.Item itm = stack.getStackItem(0);
                                if (itm.getUserValue() == MKUserValue.MOCK) {
                                    bugReporter.reportBug(new BugInstance(this, BugType.MK_MOCKED_FIELD_IN_CODE.name(), LOW_PRIORITY).addClass(this)
                                            .addMethod(this).addSourceLine(this));
                                } else if (itm.getUserValue() == MKUserValue.SPY) {
                                    bugReporter.reportBug(new BugInstance(this, BugType.MK_SPIED_FIELD_IN_CODE.name(), LOW_PRIORITY).addClass(this)
                                            .addMethod(this).addSourceLine(this));
                                }
                            }
                        }
                    }
                }
                break;
            }

            case Const.GETFIELD: {
                if (!spiedFields.isEmpty()) {
                    String clsName = getClassConstantOperand().replace('/', '.');
                    if (clsName.equals(cls.getClassName())) {
                        String fieldName = getNameConstantOperand();
                        if (spiedFields.contains(fieldName)) {
                            userValue = MKUserValue.SPIED_OBJ;
                        }
                    }
                }
            }
            }
        } finally {
            stack.sawOpcode(this, seen);
            if ((userValue != null) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item item = stack.getStackItem(0);
                item.setUserValue(userValue);
            }
        }
    }

    private MKUserValue checkForSpiedObjectCall() {
        String sig = getSigConstantOperand();
        if (!SignatureUtils.getReturnSignature(sig).startsWith("L")) {
            return null;
        }

        int numParams = SignatureUtils.getNumParameters(sig);
        if (stack.getStackDepth() > numParams) {
            OpcodeStack.Item itm = stack.getStackItem(numParams);
            return (MKUserValue) itm.getUserValue();
        }
        return null;
    }

    private MKUserValue processCaptor() {
        int numParms = SignatureUtils.getNumParameters(getSigConstantOperand());
        if (numParms > 0 && stack.getStackDepth() >= numParms) {
            for (int i = 0; i < numParms; i++) {
                OpcodeStack.Item itm = stack.getStackItem(i);
                if (itm.getUserValue() == MKUserValue.CAPTOR) {
                    return MKUserValue.CAPTOR;
                }
            }
        }

        return null;
    }

}
