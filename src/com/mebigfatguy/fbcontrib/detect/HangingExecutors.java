package com.mebigfatguy.fbcontrib.detect;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.Detector;
import edu.umd.cs.findbugs.FieldAnnotation;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.Priorities;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XFactory;
import edu.umd.cs.findbugs.ba.XField;

public class HangingExecutors extends BytecodeScanningDetector {
	
	private static final Set<String> hangableSig = new HashSet<String>();
	
	static {
		hangableSig.add("Ljava/util/concurrent/ExecutorService;");
	}
	
	
	private static final Set<String> shutdownMethods = new HashSet<String>();
	
	static {
		shutdownMethods.add("shutdown");
		shutdownMethods.add("shutdownNow");
	}
	
	
	private final BugReporter bugReporter;
	private Map<XField, FieldAnnotation> hangingFieldCandidates;
	private Map<XField, Integer> exemptExecutors;
	private OpcodeStack stack;
	private String methodName;
	
	private LocalHangingExecutor localHEDetector;
	
	
	
	public HangingExecutors(BugReporter reporter) {
		this.bugReporter=reporter;
		this.localHEDetector = new LocalHangingExecutor(this, reporter);
	}
	
	
	/**
	 * finds ExecutorService objects that don't get a call to the terminating methods,
	 * and thus, never appear to be shutdown properly (the threads exist until shutdown is called)
	 * 
	 * @param classContext the class context object of the currently parsed java class
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		localHEDetector.visitClassContext(classContext);
		try {
			hangingFieldCandidates = new HashMap<XField, FieldAnnotation>();
			exemptExecutors = new HashMap<XField, Integer>();
			parseFieldsForHangingCandidates(classContext);

			if (hangingFieldCandidates.size() > 0) {
				stack = new OpcodeStack();
				super.visitClassContext(classContext);

				reportHangingExecutorFieldBugs();
			}
		} finally {
			stack = null;
			if (hangingFieldCandidates != null)
				hangingFieldCandidates.clear();
			hangingFieldCandidates = null;
			if (exemptExecutors != null)
				exemptExecutors.clear();
			exemptExecutors = null;
		}
		
	}
	
	private void parseFieldsForHangingCandidates(ClassContext classContext) {
		JavaClass cls = classContext.getJavaClass();
		Field[] fields = cls.getFields();
		for (Field f : fields) {
			String sig = f.getSignature();
			//Debug.println(sig);
			if (hangableSig.contains(sig)) {
				//Debug.println("yes");
				hangingFieldCandidates.put(XFactory.createXField(cls.getClassName(), f.getName(), f.getSignature(), f.isStatic()), FieldAnnotation.fromBCELField(cls, f));
			}
		}
	}
	
	private void reportHangingExecutorFieldBugs() {
		for (Entry<XField, FieldAnnotation> entry : hangingFieldCandidates.entrySet()) {
			FieldAnnotation fieldAn = entry.getValue();
			if (fieldAn != null) {
				bugReporter.reportBug(new BugInstance(this, "HE_EXECUTOR_NEVER_SHUTDOWN", NORMAL_PRIORITY)
				.addClass(this)
				.addField(fieldAn)
				.addField(entry.getKey()));
			}
		}
	}
	
	/**
	 * implements the visitor to reset the opcode stack
	 * 
	 * @param obj the context object of the currently parsed code block
	 */
	@Override
	public void visitCode(Code obj) {
		stack.resetForMethodEntry(this);
		exemptExecutors.clear();
		if ("<clinit>".equals(methodName) || "<init>".equals(methodName))
			return;

		if (hangingFieldCandidates.size() > 0)
			super.visitCode(obj);
	}
	
	/**
	 * implements the visitor to collect the method name
	 * 
	 * @param obj the context object of the currently parsed method
	 */
	@Override
	public void visitMethod(Method obj) {
		methodName = obj.getName();
	}

	/**
	 * Browses for calls to shutdown() and shutdownNow(), and if they happen, remove
	 * the hanging candidate, as there is a chance it will be called.
	 * 
	 * @param seen the opcode of the currently parsed instruction
	 */
	@Override
	public void sawOpcode(int seen) {
		try {
			stack.precomputation(this);

			if ((seen == INVOKEVIRTUAL) || (seen == INVOKEINTERFACE)) {
				String sig = getSigConstantOperand();
				int argCount = Type.getArgumentTypes(sig).length;
				if (stack.getStackDepth() > argCount) {
					OpcodeStack.Item invokeeItem = stack.getStackItem(argCount);
					XField fieldOnWhichMethodIsInvoked = invokeeItem.getXField();
					if (fieldOnWhichMethodIsInvoked != null) {		
						removeCandidateIfShutdownCalled(fieldOnWhichMethodIsInvoked);
						addExemptionIfShutdownCalled(fieldOnWhichMethodIsInvoked);
					} 
				}
			}
			//TODO Should not include private methods
			else if (seen == ARETURN) {
				removeFieldsThatGetReturned();
			}
			else if (seen == PUTFIELD) {
	            XField f = getXFieldOperand();
				if ("Ljava/util/concurrent/ExecutorService;".equals(f.getSignature()) && !checkException(f)) {
					bugReporter.reportBug(new BugInstance(this, "HE_EXECUTOR_OVERWRITTEN_WITHOUT_SHUTDOWN", Priorities.HIGH_PRIORITY)
					.addClass(this)
					.addMethod(this)
					.addField(f)
					.addSourceLine(this));
				} 
				//after it's been replaced, it no longer uses its exemption. 
				exemptExecutors.remove(f);
			}
			else if (seen == IFNONNULL) {
				OpcodeStack.Item nullCheckItem = stack.getStackItem(0);
				XField fieldWhichWasNullChecked = nullCheckItem.getXField();
				if (fieldWhichWasNullChecked != null) {
					exemptExecutors.put(fieldWhichWasNullChecked, getPC() + getBranchOffset());
				}
			}
		}
		finally {
			stack.sawOpcode(this, seen);
		}
	}


	private boolean checkException(XField f) {
		if (!exemptExecutors.containsKey(f)) 
			return false;
		int i = exemptExecutors.get(f).intValue();
		
		return i == -1 || getPC() < i;
	}

	private void removeFieldsThatGetReturned() {
		if (stack.getStackDepth() > 0) {
			OpcodeStack.Item returnItem = stack.getStackItem(0);
			XField field = returnItem.getXField();
			if (field != null) {
				hangingFieldCandidates.remove(field);
			}
		}
	}

	private void addExemptionIfShutdownCalled(XField fieldOnWhichMethodIsInvoked) {
		String methodBeingInvoked = getNameConstantOperand();
		if (shutdownMethods.contains(methodBeingInvoked)) {
			exemptExecutors.put(fieldOnWhichMethodIsInvoked, -1);
		}
	}


	private void removeCandidateIfShutdownCalled(XField fieldOnWhichMethodIsInvoked) {
		if (hangingFieldCandidates.containsKey(fieldOnWhichMethodIsInvoked)) {
			String methodBeingInvoked = getNameConstantOperand();
			if (shutdownMethods.contains(methodBeingInvoked)) {
				hangingFieldCandidates.remove(fieldOnWhichMethodIsInvoked);
			}
		}
	}
	
}


class LocalHangingExecutor extends LocalTypeDetector {
	
	private static final Map<String, Set<String>> watchedClassMethods = new HashMap<String, Set<String>>();
	private static final Map<String, Integer> syncCtors = new HashMap<String, Integer>();
	static {
		Set<String> forExecutors = new HashSet<String>();
		forExecutors.add("newCachedThreadPool");
		forExecutors.add("newFixedThreadPool");
		forExecutors.add("newScheduledThreadPool");
		forExecutors.add("newSingleThreadExecutor");
		
		
		watchedClassMethods.put("java/util/concurrent/Executors", forExecutors);
	
	
		syncCtors.put("java/util/concurrent/ThreadPoolExecutor", Integer.valueOf(Constants.MAJOR_1_5));
		syncCtors.put("java/util/concurrent/ScheduledThreadPoolExecutor", Integer.valueOf(Constants.MAJOR_1_5));
	}

	private BugReporter bugReporter;
	private Detector delegatingDetector;

	public LocalHangingExecutor(Detector delegatingDetector, BugReporter reporter) {
		this.bugReporter = reporter;
		this.delegatingDetector = delegatingDetector;
	}

	@Override
	protected Map<String, Integer> getWatchedConstructors() {
		return syncCtors;
	}

	@Override
	protected Map<String, Set<String>> getWatchedClassMethods() {
		return watchedClassMethods;
	}

	@Override
	protected void reportBug(RegisterInfo cri) {
		//very important to report the bug under the top, parent detector, otherwise it gets filtered out
		bugReporter.reportBug(new BugInstance(delegatingDetector, "HE_LOCAL_EXECUTOR_SERVICE", Priorities.HIGH_PRIORITY)
		.addClass(this)
		.addMethod(this)
		.addSourceLine(cri.getSourceLineAnnotation()));
		
		
	}
	@Override
	public void visitClassContext(ClassContext classContext) {
		super.visitClassContext(classContext);
	}
	
	@Override
	public void visitCode(Code obj) {
		super.visitCode(obj);
	}
	
	@Override
	public void visitMethod(Method obj) {
		super.visitMethod(obj);
	}
	
}
