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

import java.util.HashSet;
import java.util.Set;

import com.mebigfatguy.fbcontrib.utils.BugType;

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
	
	
	//Any methods that should not be treated as a "will call a reset method"
	private static Set<String> whiteListMethods = new HashSet<String>();
	static 
	{
		whiteListMethods.add("execute");
		whiteListMethods.add("fatal");
		whiteListMethods.add("error");
		whiteListMethods.add("info");
		whiteListMethods.add("debug");
		whiteListMethods.add("trace");
		whiteListMethods.add("println");
		whiteListMethods.add("print");
		whiteListMethods.add("format");
		whiteListMethods.add("append");		//for when Java uses StringBuilders to append Strings
	}
	
	
	public HttpClientProblems(BugReporter bugReporter) {
		super(bugReporter);
	}

	@Override
	protected BugInstance makeFieldBugInstance() {
		return new BugInstance(this, BugType.HCP_HTTP_REQUEST_RESOURCES_NOT_FREED_FIELD.name(), NORMAL_PRIORITY);
	}

	@Override
	protected BugInstance makeLocalBugInstance() {
		return new BugInstance(this, BugType.HCP_HTTP_REQUEST_RESOURCES_NOT_FREED_LOCAL.name(), NORMAL_PRIORITY);
	}

	@Override
	protected boolean doesObjectNeedToBeWatched(String type) {
		return httpRequestClasses.contains(type);
	}

	@Override
	protected boolean isMethodThatShouldBeCalled(String methodName) {
		return resetMethods.contains(methodName);
	}
	
	@Override
	protected void processMethodParms() {
		String nameConstantOperand = getNameConstantOperand();
		//these requests are typically executed by being passed to an "execute()" method.  We know this doesn't
		//close the resource, we don't want to remove objects just because they passed into this method
		if (!whiteListMethods.contains(nameConstantOperand)) {
			super.processMethodParms();
		}
	}

}
