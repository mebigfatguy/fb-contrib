/*
 * fb-contrib - Auxiliary detectors for Java programs
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
import java.util.Set;

import org.apache.bcel.Constants;

import com.mebigfatguy.fbcontrib.utils.BugType;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;

/**
 * looks for allocations of synchronized collections that are stored in local variables, and 
 * never stored in fields or returned from methods. As local variables are by definition
 * thread safe, using synchronized collections in this context makes no sense.
 */
@CustomUserValue
public class LocalSynchronizedCollection extends LocalTypeDetector
{
	private static final Map<String, Integer> syncCtors = new HashMap<String, Integer>();
	static {
		syncCtors.put("java/util/Vector", Integer.valueOf(Constants.MAJOR_1_1));
		syncCtors.put("java/util/Hashtable", Integer.valueOf(Constants.MAJOR_1_1));
		syncCtors.put("java/lang/StringBuffer", Integer.valueOf(Constants.MAJOR_1_5));		
	}

	private static final Map<String, Set<String>> synchClassMethods = new HashMap<String, Set<String>>();

	static {
		Set<String> syncMethods = new HashSet<String>();
		syncMethods.add("synchronizedCollection");
		syncMethods.add("synchronizedList");
		syncMethods.add("synchronizedMap");
		syncMethods.add("synchronizedSet");
		syncMethods.add("synchronizedSortedMap");
		syncMethods.add("synchronizedSortedSet");

		synchClassMethods.put("java/util/Collections", syncMethods);
	}

	private BugReporter bugReporter;
	/**
	 * constructs a LSYC detector given the reporter to report bugs on
	 * @param bugReporter the sync of bug reports
	 */
	public LocalSynchronizedCollection(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}

	

	@Override
	protected Map<String, Integer> getWatchedConstructors() {
		return syncCtors;
	}



	@Override
	protected Map<String, Set<String>> getWatchedClassMethods() {
		return synchClassMethods;
	}

	@Override
	protected void reportBug(RegisterInfo cri) {
		bugReporter.reportBug(new BugInstance(this, BugType.LSYC_LOCAL_SYNCHRONIZED_COLLECTION.name(), cri.getPriority())
		.addClass(this)
		.addMethod(this)
		.addSourceLine(cri.getSourceLineAnnotation()));
	}
}
