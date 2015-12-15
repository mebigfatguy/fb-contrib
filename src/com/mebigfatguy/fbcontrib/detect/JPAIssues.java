package com.mebigfatguy.fbcontrib.detect;

import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;

/**
 * looks for various issues around the use of the Java Persistence API (JPA)
 */
public class JPAIssues extends BytecodeScanningDetector {
    
    private BugReporter bugReporter;
    
    public JPAIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }
    
    @Override
    public void visitMethod(Method obj) {
        if (!obj.isPublic()) {
            for (AnnotationEntry entry : obj.getAnnotationEntries()) {
                if ("Lorg/springframework/transaction/annotation/Transactional;".equals(entry.getAnnotationType())) {
                    bugReporter.reportBug(new BugInstance(this, BugType.JPAI_TRANSACTION_ON_NON_PUBLIC_METHOD.name(), NORMAL_PRIORITY)
                                .addClass(this)
                                .addMethod(this));
                }
            }
        }
    }
}
