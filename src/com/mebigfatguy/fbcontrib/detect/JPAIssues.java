package com.mebigfatguy.fbcontrib.detect;

import java.util.HashSet;
import java.util.Set;

import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.FieldOrMethod;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.Detector;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.visitclass.DismantleBytecode;

/**
 * looks for various issues around the use of the Java Persistence API (JPA)
 */
public class JPAIssues extends DismantleBytecode implements Detector {
    
    private BugReporter bugReporter;
    
    private JavaClass cls;
    private Set<FQMethod> transactionalMethods;
    private boolean isEntity;
    private boolean hasId;
    private boolean hasGeneratedValue;
    private boolean hasHCEquals;
    
    public JPAIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }
    
    @Override
    public void visitClassContext(ClassContext clsContext) {
        try {
            cls = clsContext.getJavaClass();
            catalogClass(cls);
            
            if (isEntity && hasHCEquals && hasId && hasGeneratedValue) {
                bugReporter.reportBug(new BugInstance(this, BugType.JPAI_HC_EQUALS_ON_MANAGED_ENTITY.name(), LOW_PRIORITY)
                        .addClass(this));
            }
            
            if (!transactionalMethods.isEmpty()) {
                cls.accept(this);
            }
        } finally {
            transactionalMethods = null;
        }
    }
    
    @Override
    public void visitMethod(Method obj) {
        if (!obj.isPublic()) {
            if (transactionalMethods.contains(new FQMethod(cls.getClassName(), obj.getName(), obj.getSignature()))) {
                bugReporter.reportBug(new BugInstance(this, BugType.JPAI_TRANSACTION_ON_NON_PUBLIC_METHOD.name(), NORMAL_PRIORITY)
                        .addClass(this)
                        .addMethod(this));
            }
        }
    }
    
    private void catalogClass(JavaClass cls) {
        transactionalMethods = new HashSet<FQMethod>();
        isEntity = false;
        hasId = false;
        hasGeneratedValue = false;
        hasHCEquals = false;
        
        for (AnnotationEntry entry : cls.getAnnotationEntries()) {
            if ("Ljavax/persistence/Entity;".equals(entry.getAnnotationType())) {
                isEntity = true;
                break;
            }
        }
        
        for (Method m : cls.getMethods()) {
            catalogFieldOrMethod(m);
            
            if (("equals".equals(m.getName()) && "(Ljava/lang/Object;)Z".equals(m.getSignature())) 
            ||  ("hashCode".equals(m.getName()) && "()I".equals(m.getSignature()))) {
                hasHCEquals = true;
            }
        }
        
        for (Field f : cls.getFields()) {
            catalogFieldOrMethod(f);
        }
    }
    
    private void catalogFieldOrMethod(FieldOrMethod fm) {
        for (AnnotationEntry entry : fm.getAnnotationEntries()) {
            String type = entry.getAnnotationType();
            switch (type) {
            case "Lorg/springframework/transaction/annotation/Transactional;":
                if (fm instanceof Method) {
                    transactionalMethods.add(new FQMethod(cls.getClassName(), fm.getName(), fm.getSignature()));
                }
                break;
                
            case "Ljavax/persistence/Id;":
                hasId = true;
                break;
                
            case "Ljavax/persistence/GeneratedValue;":
                hasGeneratedValue = true;
                break;
            }
        }
    }

    @Override
    public void report() {
        //required by interface
    }
}
