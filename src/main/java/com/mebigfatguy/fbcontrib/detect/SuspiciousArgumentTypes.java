package com.mebigfatguy.fbcontrib.detect;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;
import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Constant;

public class SuspiciousArgumentTypes extends BytecodeScanningDetector {

    private static final FQMethod HAS_ENTRY = new FQMethod("org/hamcrest/Matchers", "hasEntry", new SignatureBuilder().withParamTypes(Object.class, Object.class).withReturnType(boolean.class).toString());

    private BugReporter bugReporter;
    OpcodeStack stack;

    public SuspiciousArgumentTypes(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
        }
    }

    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        super.visitCode(obj);
    }

    public void sawOpcode(int seen) {
        try {
            if (seen == Constants.INVOKESTATIC) {
                FQMethod invokedMethod = new FQMethod(getClassConstantOperand(), getNameConstantOperand(), getSigConstantOperand());
                if (HAS_ENTRY.equals(invokedMethod)) {
                    if (stack.getStackDepth() >= 2) {
                        OpcodeStack.Item itm1 = stack.getStackItem(0);
                        OpcodeStack.Item itm2 = stack.getStackItem(1);
                        if (HAS_ENTRY.getClassName().equals(itm1.getSignature()) || HAS_ENTRY.getClassName().equals(itm2.getSignature())) {
                            bugReporter.reportBug(new BugInstance(this, BugType.SAT_SUSPICIOUS_ARGUMENT_TYPES.name(), NORMAL_PRIORITY)
                                    .addClass(this)
                                    .addMethod(this)
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
