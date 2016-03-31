package com.mebigfatguy.fbcontrib.detect;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.LocalVariable;
import org.apache.bcel.classfile.LocalVariableTable;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.collect.MethodInfo;
import com.mebigfatguy.fbcontrib.collect.Statistics;
import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for classes that aren't fully flushed out to be easily usable for various reasons. While the class will most likely work fine, it is more difficult to
 * use than necessary.
 */
public class ImmatureClass extends BytecodeScanningDetector {

    private static final Pattern ARG_PATTERN = Pattern.compile("(arg|parm|param)\\d");

    private static final int MAX_EMPTY_METHOD_SIZE = 2; // ACONST_NULL, ARETURN

    enum HEStatus {
        NOT_NEEDED, UNKNOWN, NEEDED
    };

    private BugReporter bugReporter;

    public ImmatureClass(BugReporter reporter) {
        bugReporter = reporter;
    }

    /**
     * overrides the visitor to report on classes without toStrings that have fields
     *
     * @param classContext
     *            the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        JavaClass cls = classContext.getJavaClass();

        if (cls.getPackageName().isEmpty()) {
            bugReporter.reportBug(new BugInstance(this, BugType.IMC_IMMATURE_CLASS_NO_PACKAGE.name(), LOW_PRIORITY).addClass(cls));
        }

        if ((!cls.isAbstract()) && (!cls.isEnum()) && !cls.getClassName().contains("$") && !isTestClass(cls)) {

            try {
                boolean clsHasRuntimeAnnotation = classHasRuntimeVisibleAnnotation(cls);
                HEStatus heStatus = HEStatus.UNKNOWN;

                checkIDEGeneratedParmNames(cls);

                for (Field f : cls.getFields()) {
                    if (!f.isStatic() && !f.isSynthetic()) {

                        boolean fieldHasRuntimeAnnotation = fieldHasRuntimeVisibleAnnotation(f);
                        if (!fieldHasRuntimeAnnotation) {
                            /* only report one of these, so as not to flood the report */
                            if (!hasMethodInHierarchy(cls, "toString", "()Ljava/lang/String;")) {
                                bugReporter.reportBug(new BugInstance(this, BugType.IMC_IMMATURE_CLASS_NO_TOSTRING.name(), LOW_PRIORITY).addClass(cls));
                                heStatus = HEStatus.NOT_NEEDED;
                                break;
                            }
                            if (heStatus != HEStatus.NOT_NEEDED) {
                                String fieldSig = f.getSignature();
                                if (fieldSig.startsWith("L")) {
                                    if (!fieldSig.startsWith("Ljava")) {
                                        JavaClass fieldClass = Repository.lookupClass(fieldSig.substring(1, fieldSig.length() - 1));
                                        if (!hasMethodInHierarchy(fieldClass, "equals", "(Ljava/lang/Object)Z")) {
                                            heStatus = HEStatus.NOT_NEEDED;
                                        }
                                    } else if (!fieldSig.startsWith("Ljava/lang/") && !fieldSig.startsWith("Ljava/util/")) {
                                        heStatus = HEStatus.NOT_NEEDED;
                                    }
                                } else if (!fieldSig.startsWith("[")) {
                                    heStatus = HEStatus.NEEDED;
                                }
                            }
                        } else {
                            heStatus = HEStatus.NOT_NEEDED;
                        }
                    }
                }

                if (!clsHasRuntimeAnnotation && (heStatus == HEStatus.NEEDED)) {
                    if (!hasMethodInHierarchy(cls, "equals", "(Ljava/lang/Object;)Z")) {
                        bugReporter.reportBug(new BugInstance(this, BugType.IMC_IMMATURE_CLASS_NO_EQUALS.name(), LOW_PRIORITY).addClass(cls));
                    } else if (!hasMethodInHierarchy(cls, "hashCode", "()I")) {
                        bugReporter.reportBug(new BugInstance(this, BugType.IMC_IMMATURE_CLASS_NO_HASHCODE.name(), LOW_PRIORITY).addClass(cls));
                    }
                }

            } catch (ClassNotFoundException cnfe) {
                bugReporter.reportMissingClass(cnfe);
            }
        }

        super.visitClassContext(classContext);
    }

    /**
     * implements the visitor to check for calls to Throwable.printStackTrace()
     *
     * @param seen
     *            the currently parsed opcode
     */
    @Override
    public void sawOpcode(int seen) {
        if (seen == INVOKEVIRTUAL) {
            if ("printStackTrace".equals(getNameConstantOperand()) && "()V".equals(getSigConstantOperand())) {
                bugReporter.reportBug(new BugInstance(this, BugType.IMC_IMMATURE_CLASS_PRINTSTACKTRACE.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                        .addSourceLine(this));
            }
        }
    }

    /**
     * looks to see if this class (or some class in its hierarchy (besides Object) has implemented the specified method.
     *
     * @param cls
     *            the class to look in
     * @param methodName
     *            the method name to look for
     * @param methodSig
     *            the method signature to look for
     *
     * @return when toString is found
     *
     * @throws ClassNotFoundException
     *             if a super class can't be found
     */
    private static boolean hasMethodInHierarchy(JavaClass cls, String methodName, String methodSig) throws ClassNotFoundException {
        MethodInfo mi = null;

        do {
            String clsName = cls.getClassName();
            if (Values.DOTTED_JAVA_LANG_OBJECT.equals(clsName)) {
                return false;
            }

            mi = Statistics.getStatistics().getMethodStatistics(clsName.replace('.', '/'), methodName, methodSig);
            cls = cls.getSuperClass();
        } while (mi.getNumBytes() == 0);

        return true;
    }

    /**
     * determines if class has a runtime annotation. If it does it is likely to be a singleton, or handled specially where hashCode/equals isn't of importance.
     *
     * @param cls
     *            the class to check
     *
     * @return if runtime annotations are found
     */
    private static boolean classHasRuntimeVisibleAnnotation(JavaClass cls) {
        AnnotationEntry[] annotations = cls.getAnnotationEntries();
        if (annotations != null) {
            for (AnnotationEntry annotation : annotations) {
                if (annotation.isRuntimeVisible()) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * looks to see the field has a runtime visible annotation, if it does it might be autowired or some other mechanism attached that makes them less
     * interesting for a toString call.
     *
     * @param f
     *            the field to check
     * @return if the field has a runtime visible annotation
     */
    private static boolean fieldHasRuntimeVisibleAnnotation(Field f) {
        AnnotationEntry[] annotations = f.getAnnotationEntries();
        if (annotations != null) {
            for (AnnotationEntry annotation : annotations) {
                if (annotation.isRuntimeVisible()) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * checks to see if it this class has unit test related annotations attached to methods
     *
     * @param cls
     *            the class to check
     * @return if a unit test annotation was found
     */
    private static boolean isTestClass(JavaClass cls) {
        for (Method m : cls.getMethods()) {
            for (AnnotationEntry entry : m.getAnnotationEntries()) {
                String type = entry.getAnnotationType();
                if (type.startsWith("Lorg/junit/") || type.startsWith("Lorg/testng/")) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * looks for methods that have it's parameters all follow the form arg0, arg1, arg2, or parm0, parm1, parm2 etc, where the method actually has code in it
     *
     * @param cls
     *            the class to check
     */
    private void checkIDEGeneratedParmNames(JavaClass cls) {

        methods: for (Method m : cls.getMethods()) {
            if (!m.isPublic()) {
                continue;
            }

            String name = m.getName();
            if (Values.CONSTRUCTOR.equals(name) || Values.STATIC_INITIALIZER.equals(name)) {
                continue;
            }

            LocalVariableTable lvt = m.getLocalVariableTable();
            if (lvt == null) {
                continue;
            }

            if (m.getCode().getCode().length <= MAX_EMPTY_METHOD_SIZE) {
                continue;
            }

            int numArgs = m.getArgumentTypes().length;
            if (numArgs == 0) {
                continue;
            }

            int offset = m.isStatic() ? 0 : 1;

            for (int i = 0; i < numArgs; i++) {
                LocalVariable lv = lvt.getLocalVariable(offset + i, 0);
                if ((lv == null) || (lv.getName() == null)) {
                    continue methods;
                }

                Matcher ma = ARG_PATTERN.matcher(lv.getName());
                if (!ma.matches()) {
                    continue methods;
                }
            }

            bugReporter.reportBug(
                    new BugInstance(this, BugType.IMC_IMMATURE_CLASS_IDE_GENERATED_PARAMETER_NAMES.name(), NORMAL_PRIORITY).addClass(cls).addMethod(cls, m));
            return;

        }
    }

}
