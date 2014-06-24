package com.mebigfatguy.fbcontrib.detect;

import java.util.HashSet;
import java.util.Set;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;

public class HttpClientProblems extends MissingMethodsDetector {

	private static Set<String> httpRequestClasses = new HashSet<String>();
	static
	{
        httpRequestClasses.add("org.apache.http.client.methods.HttpGet");
        httpRequestClasses.add("org.apache.http.client.methods.HttpPut");
        httpRequestClasses.add("org.apache.http.client.methods.HttpDelete");
        httpRequestClasses.add("org.apache.http.client.methods.HttpPost");
        httpRequestClasses.add("org.apache.http.client.methods.HttpPatch");
	}

	private static Set<String> resetMethods = new HashSet<String>();
	static
	{
		resetMethods.add("reset");
		resetMethods.add("releaseConnection");
	}
	
	
	public HttpClientProblems(BugReporter bugReporter) {
		super(bugReporter);
	}

	@Override
	protected BugInstance makeFieldBugInstance() {
		return new BugInstance(this, "HCP_HTTP_REQUEST_RESOURCES_NOT_FREED_FIELD", NORMAL_PRIORITY);
	}

	@Override
	protected BugInstance makeLocalBugInstance() {
		return new BugInstance(this, "HCP_HTTP_REQUEST_RESOURCES_NOT_FREED_LOCAL", NORMAL_PRIORITY);
	}

	@Override
	protected Set<String> getObjectsThatNeedAMethod() {
		return httpRequestClasses;
	}

	@Override
	protected boolean isMethodThatShouldBeCalled(String methodName) {
		return resetMethods.contains(methodName);
	}

}
