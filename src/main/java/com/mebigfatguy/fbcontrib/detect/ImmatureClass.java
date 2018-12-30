package com.mebigfatguy.fbcontrib.detect;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantLong;
import org.apache.bcel.classfile.ConstantValue;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.LocalVariable;
import org.apache.bcel.classfile.LocalVariableTable;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.collect.Statistics;
import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.SerialVersionCalc;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for classes that aren't fully flushed out to be easily usable for
 * various reasons. While the class will most likely work fine, it is more
 * difficult to use than necessary.
 */
public class ImmatureClass extends BytecodeScanningDetector {

	private static final Pattern ARG_PATTERN = Pattern.compile("(arg|parm|param)\\d");
	private static final String PACKAGE_INFO = "package-info";

	private static final int MAX_EMPTY_METHOD_SIZE = 2; // ACONST_NULL, ARETURN

	private static final int MANUAL_SERIALVERSION_ID_LOWER_BOUND = 0;
	private static final int MANUAL_SERIALVERSION_ID_UPPER_BOUND = 10000;

	private static JavaClass serializableClass;

	static {
		try {
			serializableClass = Repository.lookupClass("java.io.Serializable");
		} catch (ClassNotFoundException e) {
		}
	}

	enum HEStatus {
		NOT_NEEDED, UNKNOWN, NEEDED
	};

	enum FieldStatus {
		NONE, SAW_INSTANCE, REPORTED
	}

	private BugReporter bugReporter;
	private FieldStatus fieldStatus = FieldStatus.NONE;
	private boolean classIsJPAEntity;
	private boolean isDefaultSerializableConstructor;

	public ImmatureClass(BugReporter reporter) {
		bugReporter = reporter;
	}

	/**
	 * overrides the visitor to report on classes without toStrings that have fields
	 *
	 * @param classContext the context object of the currently parsed class
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		JavaClass cls = classContext.getJavaClass();
		fieldStatus = FieldStatus.NONE;

		String packageName = cls.getPackageName();
		if (packageName.isEmpty()) {
			bugReporter.reportBug(
					new BugInstance(this, BugType.IMC_IMMATURE_CLASS_NO_PACKAGE.name(), LOW_PRIORITY).addClass(cls));
		}

		if (!packageName.equals(packageName.toLowerCase(Locale.ENGLISH))) {
			bugReporter.reportBug(
					new BugInstance(this, BugType.IMC_IMMATURE_CLASS_UPPER_PACKAGE.name(), LOW_PRIORITY).addClass(cls));
		}

		String simpleClassName = cls.getClassName();
		int dotPos = simpleClassName.lastIndexOf('.');
		if (dotPos >= 0) {
			simpleClassName = simpleClassName.substring(dotPos + 1);
		}
		if (!Character.isUpperCase(simpleClassName.charAt(0))
				&& (simpleClassName.indexOf(Values.INNER_CLASS_SEPARATOR) < 0)
				&& !PACKAGE_INFO.equals(simpleClassName)) {
			bugReporter.reportBug(
					new BugInstance(this, BugType.IMC_IMMATURE_CLASS_LOWER_CLASS.name(), LOW_PRIORITY).addClass(cls));
		}

		if ((!cls.isAbstract()) && (!cls.isEnum()) && (cls.getClassName().indexOf(Values.INNER_CLASS_SEPARATOR) < 0)
				&& !isTestClass(cls)) {

			try {
				boolean clsHasRuntimeAnnotation = classHasRuntimeVisibleAnnotation(cls);
				if (clsHasRuntimeAnnotation) {
					classIsJPAEntity = classIsJPAEntity(cls);
				} else {
					classIsJPAEntity = false;
				}
				HEStatus heStatus = HEStatus.UNKNOWN;

				checkIDEGeneratedParmNames(cls);

				for (Field f : cls.getFields()) {
					if (!f.isStatic() && !f.isSynthetic()) {

						boolean fieldHasRuntimeAnnotation = fieldHasRuntimeVisibleAnnotation(f);
						if (!fieldHasRuntimeAnnotation) {
							/* only report one of these, so as not to flood the report */
							if (!classIsJPAEntity && !hasMethodInHierarchy(cls, Values.TOSTRING,
									SignatureBuilder.SIG_VOID_TO_STRING)) {
								bugReporter.reportBug(new BugInstance(this,
										BugType.IMC_IMMATURE_CLASS_NO_TOSTRING.name(), LOW_PRIORITY).addClass(cls));
								heStatus = HEStatus.NOT_NEEDED;
								break;
							}
							if (heStatus != HEStatus.NOT_NEEDED) {
								String fieldSig = f.getSignature();
								if (fieldSig.startsWith(Values.SIG_QUALIFIED_CLASS_PREFIX)) {
									if (!fieldSig.startsWith("Ljava")) {
										JavaClass fieldClass = Repository
												.lookupClass(SignatureUtils.trimSignature(fieldSig));
										if (!hasMethodInHierarchy(fieldClass, "equals",
												SignatureBuilder.SIG_OBJECT_TO_BOOLEAN)) {
											heStatus = HEStatus.NOT_NEEDED;
										}
									} else if (!fieldSig.startsWith("Ljava/lang/")
											&& !fieldSig.startsWith("Ljava/util/")) {
										heStatus = HEStatus.NOT_NEEDED;
									}
								} else if (!fieldSig.startsWith(Values.SIG_ARRAY_PREFIX)) {
									heStatus = HEStatus.NEEDED;
								}
							}
						} else {
							heStatus = HEStatus.NOT_NEEDED;
						}
					}
				}

				if (!clsHasRuntimeAnnotation && (heStatus == HEStatus.NEEDED)) {
					if (!hasMethodInHierarchy(cls, "equals", SignatureBuilder.SIG_OBJECT_TO_BOOLEAN)) {
						bugReporter.reportBug(
								new BugInstance(this, BugType.IMC_IMMATURE_CLASS_NO_EQUALS.name(), LOW_PRIORITY)
										.addClass(cls));
					} else if (!hasMethodInHierarchy(cls, Values.HASHCODE, SignatureBuilder.SIG_VOID_TO_INT)) {
						bugReporter.reportBug(
								new BugInstance(this, BugType.IMC_IMMATURE_CLASS_NO_HASHCODE.name(), LOW_PRIORITY)
										.addClass(cls));
					}
				}

			} catch (ClassNotFoundException cnfe) {
				bugReporter.reportMissingClass(cnfe);
			}
		}

		super.visitClassContext(classContext);
	}

	@Override
	public void visitField(Field f) {
		if (!f.isSynthetic() && (f.getName().indexOf(Values.SYNTHETIC_MEMBER_CHAR) < 0)) {
			switch (fieldStatus) {
			case NONE:
				if (!f.isStatic()) {
					fieldStatus = FieldStatus.SAW_INSTANCE;
				}
				break;

			case SAW_INSTANCE:
				if (f.isStatic()) {
					bugReporter.reportBug(
							new BugInstance(this, BugType.IMC_IMMATURE_CLASS_WRONG_FIELD_ORDER.name(), LOW_PRIORITY)
									.addClass(this).addField(this));
					fieldStatus = FieldStatus.REPORTED;
				}
				break;

			case REPORTED:
				break;
			}

			try {
				if ("serialVersionUID".equals(f.getName())
						&& getClassContext().getJavaClass().instanceOf(serializableClass)) {
					ConstantValue cv = f.getConstantValue();
					if (cv != null) {
						Constant c = cv.getConstantPool().getConstant(cv.getConstantValueIndex());
						if (c instanceof ConstantLong) {
							long definedUUID = ((ConstantLong) c).getBytes();
							if (definedUUID < MANUAL_SERIALVERSION_ID_LOWER_BOUND
									|| definedUUID > MANUAL_SERIALVERSION_ID_UPPER_BOUND) {
								try {
									long computedUUID = SerialVersionCalc.uuid(getClassContext().getJavaClass());
									if (computedUUID != definedUUID) {
										bugReporter.reportBug(new BugInstance(this,
												BugType.IMC_IMMATURE_CLASS_BAD_SERIALVERSIONUID.name(), NORMAL_PRIORITY)
														.addClass(this).addField(this));
									}
								} catch (IOException e) {
								}
							}
						}
					}
				}
			} catch (ClassNotFoundException e) {
				bugReporter.reportMissingClass(e);
			}

		}

	}

	/**
	 * implements the visitor to check for calls to Throwable.printStackTrace()
	 *
	 * @param seen the currently parsed opcode
	 */
	@Override
	public void sawOpcode(int seen) {
		if ((seen == INVOKEVIRTUAL) && "printStackTrace".equals(getNameConstantOperand())
				&& SignatureBuilder.SIG_VOID_TO_VOID.equals(getSigConstantOperand())) {
			bugReporter
					.reportBug(new BugInstance(this, BugType.IMC_IMMATURE_CLASS_PRINTSTACKTRACE.name(), NORMAL_PRIORITY)
							.addClass(this).addMethod(this).addSourceLine(this));
		}
	}

	/**
	 * looks to see if this class (or some class in its hierarchy (besides Object)
	 * has implemented the specified method.
	 *
	 * @param cls        the class to look in
	 * @param methodName the method name to look for
	 * @param methodSig  the method signature to look for
	 *
	 * @return when toString is found
	 *
	 * @throws ClassNotFoundException if a super class can't be found
	 */
	private static boolean hasMethodInHierarchy(JavaClass cls, String methodName, String methodSig)
			throws ClassNotFoundException {
		String clsName = cls.getClassName();
		if (Values.DOTTED_JAVA_LANG_OBJECT.equals(clsName)) {
			return false;
		}

		if (Statistics.getStatistics().getMethodStatistics(clsName.replace('.', '/'), methodName, methodSig)
				.getNumBytes() == 0) {
			return hasMethodInHierarchy(cls.getSuperClass(), methodName, methodSig);
		}
		return true;
	}

	/**
	 * determines if class has a runtime annotation. If it does it is likely to be a
	 * singleton, or handled specially where hashCode/equals isn't of importance.
	 *
	 * @param cls the class to check
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
	 * returns whether this class is a JPA Entity, as such it shouldn't really have
	 * a toString()
	 *
	 * @param cls the class to check
	 * @return if the class is a jpa entity
	 */
	private static boolean classIsJPAEntity(JavaClass cls) {
		AnnotationEntry[] annotations = cls.getAnnotationEntries();
		if (annotations != null) {
			for (AnnotationEntry annotation : annotations) {
				if ("Ljavax/persistence/Entity;".equals(annotation.getAnnotationType())) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * looks to see the field has a runtime visible annotation, if it does it might
	 * be autowired or some other mechanism attached that makes them less
	 * interesting for a toString call.
	 *
	 * @param f the field to check
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
	 * checks to see if it this class has unit test related annotations attached to
	 * methods
	 *
	 * @param cls the class to check
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
	 * looks for methods that have it's parameters all follow the form arg0, arg1,
	 * arg2, or parm0, parm1, parm2 etc, where the method actually has code in it
	 *
	 * @param cls the class to check
	 */
	private void checkIDEGeneratedParmNames(JavaClass cls) {
		for (Method m : cls.getMethods()) {
			if (isIDEGeneratedMethodWithCode(m)) {
				bugReporter.reportBug(
						new BugInstance(this, BugType.IMC_IMMATURE_CLASS_IDE_GENERATED_PARAMETER_NAMES.name(),
								NORMAL_PRIORITY).addClass(cls).addMethod(cls, m));
				return;
			}
		}
	}

	private boolean isIDEGeneratedMethodWithCode(Method m) {
		if (!m.isPublic()) {
			return false;
		}

		String name = m.getName();
		if (Values.CONSTRUCTOR.equals(name) || Values.STATIC_INITIALIZER.equals(name)) {
			return false;
		}

		LocalVariableTable lvt = m.getLocalVariableTable();
		if (lvt == null) {
			return false;
		}

		if (m.getCode().getCode().length <= MAX_EMPTY_METHOD_SIZE) {
			return false;
		}

		int numArgs = m.getArgumentTypes().length;
		if (numArgs == 0) {
			return false;
		}

		int offset = m.isStatic() ? 0 : 1;

		for (int i = 0; i < numArgs; i++) {
			LocalVariable lv = lvt.getLocalVariable(offset + i, 0);
			if ((lv == null) || (lv.getName() == null)) {
				return false;
			}

			Matcher ma = ARG_PATTERN.matcher(lv.getName());
			if (!ma.matches()) {
				return false;
			}
		}
		return true;
	}

}
