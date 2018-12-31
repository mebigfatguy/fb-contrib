/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2019 Kevin Lubick
 * Copyright (C) 2005-2019 Dave Brosius
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

import java.util.Set;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.internalAnnotations.DottedClassName;

/**
 * looks for problems surrounding use of HttpRequests from the Apache HttpComponents library which have have some little-known quirks about them. This is a set
 * of detectors that helps guard against resource starvation.
 */
public class HttpClientProblems extends MissingMethodsDetector {

    private static Set<String> httpRequestClasses = UnmodifiableSet.create("org.apache.http.client.methods.HttpGet", "org.apache.http.client.methods.HttpPut",
            "org.apache.http.client.methods.HttpDelete", "org.apache.http.client.methods.HttpPost", "org.apache.http.client.methods.HttpPatch");

    private static Set<String> resetMethods = UnmodifiableSet.create("reset", "releaseConnection");

    // Any methods that should not be treated as a "will call a reset method"
    private static Set<String> whiteListMethods = UnmodifiableSet.create("execute", "fatal", "error", "info", "debug", "trace", "println", "print", "format",
            "append" // for when Java uses StringBuilders to append Strings
    );

    public HttpClientProblems(BugReporter bugReporter) {
        super(bugReporter);
    }

    @Override
    protected BugInstance makeFieldBugInstance() {
        return new BugInstance(this, BugType.HCP_HTTP_REQUEST_RESOURCES_NOT_FREED_FIELD.name(), NORMAL_PRIORITY);
    }

    @Override
    protected BugInstance makeLocalBugInstance() {
        // This is reported at LOW, and also the bugrank is set high, as there have been issues brought up with GitHub Issue #59
        // If anyone wants to address these and retry, i'm all for it.
        return new BugInstance(this, BugType.HCP_HTTP_REQUEST_RESOURCES_NOT_FREED_LOCAL.name(), LOW_PRIORITY);
    }

    @Override
    protected boolean doesObjectNeedToBeWatched(@DottedClassName String type) {
        return httpRequestClasses.contains(type);
    }

    @Override
    protected boolean doesStaticFactoryReturnNeedToBeWatched(String clsName, String methodName, String signature) {
        return false;
    }

    @Override
    protected boolean isMethodThatShouldBeCalled(String methodName) {
        return resetMethods.contains(methodName);
    }

    @Override
    protected void processMethodParms() {
        String nameConstantOperand = getNameConstantOperand();
        // these requests are typically executed by being passed to an
        // "execute()" method. We know this doesn't
        // close the resource, we don't want to remove objects just because they
        // passed into this method
        if (!whiteListMethods.contains(nameConstantOperand)) {
            super.processMethodParms();
        }
    }

}
