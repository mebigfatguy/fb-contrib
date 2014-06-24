package com.mebigfatguy.fbcontrib.detect;

import java.util.Set;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;

public class HttpClientProblems extends MissingMethodsDetector {

	public HttpClientProblems(BugReporter bugReporter) {
		super(bugReporter);
	}

	@Override
	protected BugInstance makeFieldBugInstance() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected Set<String> getObjectsThatNeedAMethod() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected BugInstance makeLocalBugInstance() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected boolean isMethodThatShouldBeCalled(String methodName) {
		// TODO Auto-generated method stub
		return false;
	}

}
