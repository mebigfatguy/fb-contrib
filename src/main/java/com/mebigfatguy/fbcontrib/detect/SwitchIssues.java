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

    private enum State {
        // @formatter:off
        NOTHING, ALOAD_1STA, IFNULL, ALOAD_1STB, ASTORE_2ND, ICONST, ISTORE_3RD, ALOAD_2ND, HASHCODE, 
        INVOKE_ST, INVOKE_ORDINAL, IALOAD
        // @formatter:on
    }

    private BugReporter bugReporter;
    private JavaClass cls;
    private OpcodeStack stack;
    private int ifNullReg;
    private State state;
    private int aReg1;

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
        if (cls.getMajor() < 58) { // java 14
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
        reset();
        super.visitCode(obj);
    }

    @Override
    public void sawOpcode(int seen) {
        try {
            switch (seen) {
            case Const.ALOAD_0:
            case Const.ALOAD_1:
            case Const.ALOAD_2:
            case Const.ALOAD_3:
            case Const.ALOAD: {
                int reg = RegisterUtils.getALoadReg(this, seen);
                if (reg == -1) {
                    reset();
                } else if (state == State.NOTHING) {
                    aReg1 = reg;
                    state = State.ALOAD_1STA;
                } else if (state == State.IFNULL && (reg == aReg1)) {
                    state = State.ALOAD_1STB;
                } else if (state == State.ISTORE_3RD) {
                    state = State.ALOAD_2ND;
                } else if (state == State.INVOKE_ST && (reg == aReg1)) {
                    state = State.ALOAD_1STB;
                } else {
                    reset();
                }
                break;
            }

            case Const.IFNULL: {
                if (state == State.ALOAD_1STA && stack.getStackDepth() > 0) {
                    ifNullReg = stack.getStackItem(0).getRegisterNumber();
                    if (ifNullReg >= 0) {
                        state = State.IFNULL;
                    } else {
                        reset();
                    }
                } else {
                    reset();
                }
                break;
            }

            case Const.ASTORE_0:
            case Const.ASTORE_1:
            case Const.ASTORE_2:
            case Const.ASTORE_3:
            case Const.ASTORE: {
                int reg = RegisterUtils.getAStoreReg(this, seen);
                if (reg == -1) {
                    reset();
                } else if (state == State.ALOAD_1STB) {
                    state = State.ASTORE_2ND;
                } else {
                    reset();
                }
                break;
            }

            case Const.ICONST_M1:
            case Const.ICONST_0:
            case Const.ICONST_1:
            case Const.ICONST_2:
            case Const.ICONST_3: {
                if (state == State.ASTORE_2ND) {
                    state = State.ICONST;
                } else {
                    reset();
                }
                break;
            }

            case Const.ISTORE_0:
            case Const.ISTORE_1:
            case Const.ISTORE_2:
            case Const.ISTORE_3:
            case Const.ISTORE: {
                int reg = RegisterUtils.getStoreReg(this, seen);
                if (reg == -1) {
                    reset();
                } else if (state == State.ICONST) {
                    state = State.ISTORE_3RD;
                } else {
                    reset();
                }
                break;
            }

            case Const.INVOKEVIRTUAL: {
                if (state == State.ALOAD_2ND) {
                    if (Values.HASHCODE.equals(getNameConstantOperand()) && SignatureBuilder.SIG_VOID_TO_INT.equals(getSigConstantOperand())) {
                        if (ifNullReg >= 0 && stack.getStackDepth() > 0) {
                            OpcodeStack.Item item = stack.getStackItem(0);
                            if (item.getRegisterNumber() != ifNullReg) {
                                reset();
                            } else {
                                state = State.HASHCODE;
                            }
                        }
                    } else {
                        reset();
                    }
                } else if (state == State.ALOAD_1STB) {
                    if ("ordinal".equals(getNameConstantOperand()) && SignatureBuilder.SIG_VOID_TO_INT.equals(getSigConstantOperand())) {
                        state = State.INVOKE_ORDINAL;
                    } else {
                        reset();
                    }
                } else {
                    reset();
                }
                break;
            }

            case Const.LOOKUPSWITCH: {
                if (state == State.HASHCODE) {
                    if (ifNullReg != -1 && stack.getStackDepth() > 0) {
                        if (cls.getMajor() >= 61) { //java 17
                            bugReporter.reportBug(
                                    new BugInstance(this, BugType.SI_USE_NULL_CASE.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
                        }
                    }
                }
                reset();
                break;
            }

            case Const.INVOKESTATIC: {
                if (getNameConstantOperand().startsWith("$SWITCH_TABLE")) {
                    state = State.INVOKE_ST;
                } else {
                    reset();
                }
                break;
            }

            case Const.IALOAD: {
                if (state == State.INVOKE_ORDINAL) {
                    state = State.IALOAD;
                } else {
                    reset();
                }
                break;
            }

            case Const.TABLESWITCH: {
                if (state == State.IALOAD || (state == State.INVOKE_ORDINAL)) {
                    if (ifNullReg != -1 && stack.getStackDepth() > 0) {
                        if (cls.getMajor() >= 61) { // java 17
                            bugReporter.reportBug(
                                    new BugInstance(this, BugType.SI_USE_NULL_CASE.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
                        }
                    }
                }
                reset();
            }

            default: {
                reset();
                break;
            }
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    private void reset() {
        state = State.NOTHING;
        aReg1 = -1;
        ifNullReg = -1;
    }
}
