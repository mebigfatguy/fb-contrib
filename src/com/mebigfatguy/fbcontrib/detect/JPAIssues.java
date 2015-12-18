package com.mebigfatguy.fbcontrib.detect;

import java.util.HashSet;
import java.util.Set;

import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.ElementValuePair;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.FieldOrMethod;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for various issues around the use of the Java Persistence API (JPA)
 */
public class JPAIssues extends BytecodeScanningDetector {
    
    private BugReporter bugReporter;
    
    private JavaClass cls;
    private OpcodeStack stack;
    private Set<FQMethod> transactionalMethods;
    private boolean isEntity;
    private boolean hasId;
    private boolean hasGeneratedValue;
    private boolean hasEagerOneToMany;
    private boolean hasFetch;
    private boolean hasHCEquals;
    
    public JPAIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }
    
    @Override
    public void visitClassContext(ClassContext clsContext) {
        try {
            cls = clsContext.getJavaClass();
            catalogClass(cls);
            
            if (isEntity) {
                if (hasHCEquals && hasId && hasGeneratedValue) {
                    bugReporter.reportBug(new BugInstance(this, BugType.JPAI_HC_EQUALS_ON_MANAGED_ENTITY.name(), LOW_PRIORITY)
                            .addClass(cls));
                }
                if (hasEagerOneToMany && !hasFetch) {
                    bugReporter.reportBug(new BugInstance(this, BugType.JPAI_INEFFICIENT_EAGER_FETCH.name(), LOW_PRIORITY)
                            .addClass(cls));
                }
            }
            
            if (!transactionalMethods.isEmpty()) {
                stack = new OpcodeStack();
                super.visitClassContext(clsContext);
            }
        } finally {
            transactionalMethods = null;
            stack = null;
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
    
    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        super.visitCode(obj);
    }
    
    @Override
    public void sawOpcode(int seen) {
        try {
            if ((seen == INVOKEVIRTUAL) || (seen == INVOKEINTERFACE)) {
                if (transactionalMethods.contains(new FQMethod(getClassConstantOperand(), getNameConstantOperand(), getSigConstantOperand()))) {
                    Type[] parmTypes = Type.getArgumentTypes(getSigConstantOperand());
                    if (stack.getStackDepth() > parmTypes.length) {
                        OpcodeStack.Item itm = stack.getStackItem(parmTypes.length);
                        if (itm.getRegisterNumber() == 0) {
                            bugReporter.reportBug(new BugInstance(this, BugType.JPAI_NON_PROXIED_TRANSACTION_CALL.name(), NORMAL_PRIORITY)
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
    
    private void catalogClass(JavaClass cls) {
        transactionalMethods = new HashSet<FQMethod>();
        isEntity = false;
        hasId = false;
        hasGeneratedValue = false;
        hasEagerOneToMany = false;
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
                
            case "Ljavax/persistence/OneToMany;":
                for (ElementValuePair pair : entry.getElementValuePairs()) {
                    if ("fetch".equals(pair.getNameString()) && "EAGER".equals(pair.getValue().stringifyValue())) {
                        hasEagerOneToMany = true;
                        break;
                    }
                }
                break;
                
            case "Lorg/hibernate/annotations/Fetch;":
            case "Lorg/eclipse/persistence/annotations/JoinFetch;":
            case "Lorg/eclipse/persistence/annotations/BatchFetch;":
                hasFetch = true;
                break;
            }
        }
    }
}
