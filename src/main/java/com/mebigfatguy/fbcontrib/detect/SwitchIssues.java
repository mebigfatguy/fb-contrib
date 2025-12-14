package com.mebigfatguy.fbcontrib.detect;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

public class SwitchIssues extends BytecodeScanningDetector {

    private BugReporter bugReporter;
    private JavaClass cls;
    private OpcodeStack stack;
    private int ifNullReg;

    /**
     * constructs a SI detector given the reporter to report bugs on
     *
     * @param bugReporter the sync of bug reports
     */
    public SwitchIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        cls = classContext.getJavaClass();
        if (cls.getMajor() < Const.MAJOR_14) {
            return;
        }

        try {
            stack = new OpcodeStack();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            cls = null;
        }
    }

    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        ifNullReg = -1;
        super.visitCode(obj);
    }

    @Override
    public void sawOpcode(int seen) {
        try {
            switch (seen) {
            case Const.IFNULL: {
                if (stack.getStackDepth() > 0) {
                    ifNullReg = stack.getStackItem(0).getRegisterNumber();
                }
                break;
            }
            case Const.ALOAD_0:
            case Const.ALOAD_1:
            case Const.ALOAD_2:
            case Const.ALOAD_3:
            case Const.ALOAD: {
                int reg = RegisterUtils.getALoadReg(this, seen);
                if (reg != ifNullReg) {
                    ifNullReg = -1;
                }
                break;
            }

            case Const.ASTORE_0:
            case Const.ASTORE_1:
            case Const.ASTORE_2:
            case Const.ASTORE_3:
            case Const.ASTORE: {
                break;
            }

            case Const.DUP: {
                break;
            }

            case Const.INVOKEVIRTUAL: {
                if (!Values.HASHCODE.equals(getNameConstantOperand()) || !SignatureBuilder.SIG_VOID_TO_INT.equals(getSigConstantOperand())) {
                    ifNullReg = -1;
                } else {
                    if (ifNullReg >= 0 && stack.getStackDepth() > 0) {
                        OpcodeStack.Item item = stack.getStackItem(0);
                        if (item.getRegisterNumber() != ifNullReg) {
                            ifNullReg = -1;
                        }
                    }
                }
                break;
            }

            case Const.LOOKUPSWITCH: {
                if (ifNullReg != -1 && stack.getStackDepth() > 0) {
                    if (cls.getMajor() >= Const.MAJOR_17) {
                        bugReporter.reportBug(
                                new BugInstance(this, BugType.SI_USE_NULL_CASE.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
                    }
                }
                ifNullReg = -1;
                break;
            }
            default: {
                ifNullReg = -1;
                break;
            }
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }
}
