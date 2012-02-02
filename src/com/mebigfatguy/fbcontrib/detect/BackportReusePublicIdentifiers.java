package com.mebigfatguy.fbcontrib.detect;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.bcel.OpcodeStackDetector;

/**
 * Detects use of Backport concurrent classes. Updated/Efficient version of
 * these classes are available in versions of the JDK 5.0 and higher, and these
 * classes should only be used if you are targeting JDK 1.4 and lower.
 * 
 * Finds usage of classes from backport utils package.
 */
public class BackportReusePublicIdentifiers extends OpcodeStackDetector {

    private final BugReporter bugReporter;

    public BackportReusePublicIdentifiers(final BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void sawOpcode(int seen) {
        switch (seen) {
        case INVOKESTATIC: {
            String className = getClassConstantOperand();
            if (className.startsWith("edu/emory/mathcs/backport/")) {
                reportBug();
            }
        }
            break;
        case INVOKESPECIAL: {
            String className = getClassConstantOperand();
            String methodName = getNameConstantOperand();
            if (className.startsWith("edu/emory/mathcs/backport/")
                    && methodName.equals("<init>")) {
                reportBug();
            }
        }
            break;
        }
    }

    private void reportBug() {
        bugReporter.reportBug(new BugInstance(this,
                "BRPI_BACKPORT_REUSE_PUBLIC_IDENTIFIERS", NORMAL_PRIORITY)
                .addClass(this).addMethod(this).addSourceLine(this));
    }
}
