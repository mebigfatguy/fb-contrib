/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2014 Kevin Lubick
 * Copyright (C) 2005-2014 Dave Brosius
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
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

/**
 * looks for executors that are never shutdown, which will not allow the application to terminate
 */
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
	private Map<XField, AnnotationPriority> hangingFieldCandidates;
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
			hangingFieldCandidates = new HashMap<XField, AnnotationPriority>();
			exemptExecutors = new HashMap<XField, Integer>();
			parseFieldsForHangingCandidates(classContext);

			if (!hangingFieldCandidates.isEmpty()) {
				stack = new OpcodeStack();
				super.visitClassContext(classContext);

				reportHangingExecutorFieldBugs();
			}
		} finally {
			stack = null;
			hangingFieldCandidates = null;
			exemptExecutors = null;
		}

	}

	private void parseFieldsForHangingCandidates(ClassContext classContext) {
		JavaClass cls = classContext.getJavaClass();
		Field[] fields = cls.getFields();
		for (Field f : fields) {
			String sig = f.getSignature();
			if (hangableSig.contains(sig)) {
				hangingFieldCandidates.put(XFactory.createXField(cls.getClassName(), f.getName(), f.getSignature(), f.isStatic()), new AnnotationPriority(FieldAnnotation.fromBCELField(cls, f), NORMAL_PRIORITY));
			}
		}
	}

	private void reportHangingExecutorFieldBugs() {
		for (Entry<XField, AnnotationPriority> entry : hangingFieldCandidates.entrySet()) {
			AnnotationPriority fieldAn = entry.getValue();
			if (fieldAn != null) {
				bugReporter.reportBug(new BugInstance(this, "HES_EXECUTOR_NEVER_SHUTDOWN", fieldAn.priority)
				.addClass(this)
				.addField(fieldAn.annotation)
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

		if (!hangingFieldCandidates.isEmpty())
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
		if ("<clinit>".equals(methodName) || "<init>".equals(methodName))
		{
			lookForCustomThreadFactoriesInConstructors(seen);
			return;
		}
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
				if (f != null)
					reportOverwrittenField(f);
			}
			else if (seen == IFNONNULL) {
				//indicates a null check, which means that we get an exemption until the end of the branch
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


	private void lookForCustomThreadFactoriesInConstructors(int seen) {
		try {
			stack.precomputation(this);
			if (seen == PUTFIELD) {
				XField f = getXFieldOperand();
				if (f != null && "Ljava/util/concurrent/ExecutorService;".equals(f.getSignature())){
					//look at the top of the stack, get the arguments passed into the function that was called
					//and then pull out the types.
					//if the last type is a ThreadFactory, set the priority to low
					Type[] argumentTypes = Type.getArgumentTypes(stack.getStackItem(0).getReturnValueOf().getSignature());
					if (argumentTypes.length != 0) {
						if ("Ljava/util/concurrent/ThreadFactory;".equals(argumentTypes[argumentTypes.length-1].getSignature())) {
							AnnotationPriority ap = this.hangingFieldCandidates.get(f);
							if (ap != null) {
								ap.priority = LOW_PRIORITY;
								this.hangingFieldCandidates.put(f, ap);
							}
						}
					}
				}
			}		
		}
		finally {
			stack.sawOpcode(this, seen);
		}

	}


	private void reportOverwrittenField(XField f) {
		if ("Ljava/util/concurrent/ExecutorService;".equals(f.getSignature()) && !checkException(f)) {
			bugReporter.reportBug(new BugInstance(this, "HES_EXECUTOR_OVERWRITTEN_WITHOUT_SHUTDOWN", Priorities.NORMAL_PRIORITY)
			.addClass(this)
			.addMethod(this)
			.addField(f)
			.addSourceLine(this));
		} 
		//after it's been replaced, it no longer uses its exemption. 
		exemptExecutors.remove(f);
	}


	private boolean checkException(XField f) {
		if (!exemptExecutors.containsKey(f)) 
			return false;
		int i = exemptExecutors.get(f).intValue();

		return i == -1 || getPC() < i;
	}

	private void removeFieldsThatGetReturned() {
		if (stack.getStackDepth() > 0) {
			OpcodeStack.Item returnItem = stack.getStackItem(0); //top thing on the stack was the variable being returned
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

	private static class AnnotationPriority {

		public int priority;
		public FieldAnnotation annotation;

		public AnnotationPriority(FieldAnnotation annotation, int priority) {
			this.annotation = annotation;
			this.priority = priority;
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
		bugReporter.reportBug(new BugInstance(delegatingDetector, "HES_LOCAL_EXECUTOR_SERVICE", NORMAL_PRIORITY)
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
