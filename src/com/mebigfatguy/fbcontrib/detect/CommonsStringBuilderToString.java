package com.mebigfatguy.fbcontrib.detect;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import org.apache.bcel.classfile.Code;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.OpcodeStack.Item;
import edu.umd.cs.findbugs.bcel.OpcodeStackDetector;

/**
 * Find usage of ToStringBuilder from Apache commons, where the code invokes
 * toString() on the constructed object without invoking append().
 * 
 * Usage without invoking append is equivalent of using the Object.toString()
 * method
 * 
 * <pre>
 * new ToStringBuilder(this).toString();
 * </pre>
 */
public class CommonsStringBuilderToString extends OpcodeStackDetector {

    private final BugReporter bugReporter;
    private Stack<Pair> stackTracker = new Stack<Pair>();
    private Map<Integer, Boolean> registerTracker = new HashMap<Integer, Boolean>();

    /**
     * constructs a CSBTS detector given the reporter to report bugs on.
     * 
     * @param bugReporter
     *            the sync of bug reports
     */
    public CommonsStringBuilderToString(final BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visit(Code obj) {
        registerTracker.clear();
        stackTracker.clear();
        super.visit(obj);
    }

    @Override
    public void sawOpcode(int seen) {
        switch (seen) {
        case ALOAD:
        case ALOAD_1:
        case ALOAD_2:
        case ALOAD_3:
            // TODO : Determine class before acting out
            Integer loadReg = Integer.valueOf(getRegisterOperand());
            Boolean appendInvoked = registerTracker.get(loadReg);
            if (appendInvoked != null) {
                stackTracker.add(new Pair(loadReg.intValue(), appendInvoked
                        .booleanValue()));
            }
            break;
        case ASTORE:
        case ASTORE_1:
        case ASTORE_2:
        case ASTORE_3:
            Item si = stack.getStackItem(0);
            String signature = si.getSignature();
            if ("Lorg/apache/commons/lang3/builder/ToStringBuilder;"
                    .equals(signature)
                    || "Lorg/apache/commons/lang/builder/ToStringBuilder;"
                            .equals(signature)) {
                int storeReg = getRegisterOperand();
                Pair p = stackTracker.pop();
                registerTracker.put(
                        Integer.valueOf(storeReg),
                        p.register == -1 ? Boolean.FALSE : registerTracker
                                .get(Integer.valueOf(p.register)));
            }
            break;
        case POP:
            si = stack.getStackItem(0);
            signature = si.getSignature();
            if ("Lorg/apache/commons/lang3/builder/ToStringBuilder;"
                    .equals(signature)
                    || "Lorg/apache/commons/lang/builder/ToStringBuilder;"
                            .equals(signature)) {
                Pair p = stackTracker.pop();
                registerTracker.put(Integer.valueOf(p.register),
                        Boolean.valueOf(p.appendInvoked));
            }
            break;
        case INVOKESPECIAL:
        case INVOKEVIRTUAL:
            String loadClassName = getClassConstantOperand();
            String calledMethodName = getNameConstantOperand();
            String calledMethodSig = getSigConstantOperand();

            if ("org/apache/commons/lang3/builder/ToStringBuilder"
                    .equals(loadClassName)
                    || "org/apache/commons/lang/builder/ToStringBuilder"
                            .equals(loadClassName)) {
                if ("<init>".equals(calledMethodName)
                        && "(Ljava/lang/Object;)V".equals(calledMethodSig)) {
                    stackTracker.add(new Pair(-1, false));
                } else if ("append".equals(calledMethodName)) {
                    Pair p = stackTracker.pop();
                    stackTracker.add(new Pair(p.register, true));
                } else if ("toString".equals(calledMethodName)
                        && "()Ljava/lang/String;".equals(calledMethodSig)) {
                    Pair p = stackTracker.pop();
                    if (p.appendInvoked == false) {
                        bugReporter.reportBug(new BugInstance(this,
                                "CSBTS_COMMONS_STRING_BUILDER_TOSTRING",
                                HIGH_PRIORITY).addClass(this).addMethod(this)
                                .addSourceLine(this));
                    }
                }
            }
        }
    }

    static final class Pair {
        public final int register;
        public final boolean appendInvoked;

        Pair(int register, boolean appendInvoked) {
            this.register = register;
            this.appendInvoked = appendInvoked;
        }
    }
}
