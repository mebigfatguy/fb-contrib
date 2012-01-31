package com.mebigfatguy.fbcontrib.detect;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.LocalVariableTable;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.OpcodeStack.Item;
import edu.umd.cs.findbugs.bcel.OpcodeStackDetector;

/**
 * In a JVM, Two classes are the same class (and consequently the same type) if
 * they are loaded by the same class loader, and they have the same fully
 * qualified name [JVMSpec 1999].
 * 
 * Two classes with the same name but different package names are distinct, as
 * are two classes with the same fully qualified name loaded by different class
 * loaders.
 * 
 * Find usage involving comparison of class names, rather than the class itself.
 * 
 */
public class CompareClassNameEquals extends OpcodeStackDetector {
    private boolean flag = false;
    private final BugReporter bugReporter;

    public CompareClassNameEquals(final BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public boolean shouldVisitCode(Code obj) {
        flag = false;
        LocalVariableTable lvt = getMethod().getLocalVariableTable();
        return lvt != null;
    }

    @Override
    public void afterOpcode(int seen) {
        super.afterOpcode(seen);
        if (flag == true) {
            stack.getStackItem(0).setUserValue(Boolean.TRUE);
            flag = false;
        }
    }

    @Override
    public void sawOpcode(int seen) {
        switch (seen) {
        case INVOKEVIRTUAL:
            if ("getName".equals(getNameConstantOperand())
                    && "()Ljava/lang/String;".equals(getSigConstantOperand())
                    && "java/lang/Class".equals(getClassConstantOperand())) {
                flag = true;
            } else if ("equals".equals(getNameConstantOperand())
                    && "(Ljava/lang/Object;)Z".equals(getSigConstantOperand())
                    && "java/lang/String".equals(getClassConstantOperand())) {
                Item item = stack.getItemMethodInvokedOn(this);
                Object userValue = item.getUserValue();
                if (userValue != null && userValue == Boolean.TRUE) {
                    bugReporter
                            .reportBug(new BugInstance(this,
                                    "CCNE_COMPARE_CLASS_EQUALS_NAME",
                                    NORMAL_PRIORITY).addClass(this)
                                    .addMethod(this).addSourceLine(this));
                }
            }
            break;
        }
    }
}
