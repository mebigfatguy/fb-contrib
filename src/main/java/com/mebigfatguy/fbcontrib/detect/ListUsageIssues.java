package com.mebigfatguy.fbcontrib.detect;

import java.util.List;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for odd usage patterns when using Lists
 */
@CustomUserValue
public class ListUsageIssues extends BytecodeScanningDetector {

    private static final FQMethod ARRAYS_ASLIST_METHOD = new FQMethod("java/util/Arrays", "asList",
            new SignatureBuilder().withParamTypes(Object[].class).withReturnType(List.class).build());

    private BugReporter bugReporter;
    private OpcodeStack stack;
    private int clsVersion;

    /**
     * constructs a LUI detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public ListUsageIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            clsVersion = classContext.getJavaClass().getMajor();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
        }
    }

    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        super.visitCode(obj);
    }

    @Override
    public void sawOpcode(int seen) {
        try {
            if (seen == INVOKESTATIC) {
                FQMethod fqm = new FQMethod(getClassConstantOperand(), getNameConstantOperand(), getSigConstantOperand());
                if (ARRAYS_ASLIST_METHOD.equals(fqm)) {
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item itm = stack.getStackItem(0);
                        if ((clsVersion >= Constants.MAJOR_1_8) && Values.ONE.equals(itm.getConstant())) {
                            bugReporter.reportBug(new BugInstance(this, BugType.LUI_USE_SINGLETON_LIST.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                                    .addSourceLine(this));
                        }
                    }
                }
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }
}
