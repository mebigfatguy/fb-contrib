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

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;

@CustomUserValue
public class WriteOnlyCollection extends MissingMethodsDetector {

	private static Set<String> collectionClasses = new HashSet<String>();
	static
	{
		collectionClasses.add(Set.class.getName());
		collectionClasses.add(Map.class.getName());
		collectionClasses.add(List.class.getName());
		collectionClasses.add(SortedSet.class.getName());
		collectionClasses.add(SortedMap.class.getName());
		collectionClasses.add(Collection.class.getName());
        collectionClasses.add(EnumSet.class.getName());
        collectionClasses.add(EnumMap.class.getName());
        collectionClasses.add(HashSet.class.getName());
        collectionClasses.add(IdentityHashMap.class.getName());
		collectionClasses.add(TreeSet.class.getName());
		collectionClasses.add(LinkedHashSet.class.getName());
		collectionClasses.add(HashMap.class.getName());
		collectionClasses.add(TreeMap.class.getName());
		collectionClasses.add(Hashtable.class.getName());
		collectionClasses.add(LinkedHashMap.class.getName());
		collectionClasses.add(Vector.class.getName());
		collectionClasses.add(ArrayList.class.getName());
		collectionClasses.add(LinkedList.class.getName());
		/* these are java 6 classes */
        collectionClasses.add("java.util.Deque");
        collectionClasses.add("java.util.Queue");
        collectionClasses.add("java.util.ArrayDeque");
        collectionClasses.add("java.util.LinkedBlockingDeque");
        collectionClasses.add("java.util.NavigableMap");
        collectionClasses.add("java.util.concurrent.ConcurrentMap");
        collectionClasses.add("java.util.concurrent.ConcurrentNavigableMap");
        collectionClasses.add("java.util.concurrent.ConcurrentSkipListMap");
        collectionClasses.add("java.util.concurrent.ConcurrentHashMap");
        collectionClasses.add("java.util.concurrent.ConcurrentSkipListSet");
        collectionClasses.add("java.util.concurrent.CopyOnWriteArrayList");
	}

	private static Set<String> nonInformationalMethods = new HashSet<String>();
	static
	{
		nonInformationalMethods.add("add");
		nonInformationalMethods.add("addAll");
		nonInformationalMethods.add("addElement");
		nonInformationalMethods.add("addFirst");
		nonInformationalMethods.add("addLast");
		nonInformationalMethods.add("clear");
		nonInformationalMethods.add("clone");
		nonInformationalMethods.add("ensureCapacity");
		nonInformationalMethods.add("insertElementAt");
		nonInformationalMethods.add("push");
		nonInformationalMethods.add("put");
		nonInformationalMethods.add("putAll");
		nonInformationalMethods.add("remove");
		nonInformationalMethods.add("removeAll");
		nonInformationalMethods.add("removeElement");
		nonInformationalMethods.add("removeElementAt");
		nonInformationalMethods.add("removeRange");
		nonInformationalMethods.add("set");
		nonInformationalMethods.add("setElementAt");
		nonInformationalMethods.add("setSize");
		nonInformationalMethods.add("trimToSize");
	}

	/**
     * constructs a WOC detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
	 */
	public WriteOnlyCollection(BugReporter bugReporter) {
		super(bugReporter);
	}
	
	@Override
	protected BugInstance makeFieldBugInstance() {
		return new BugInstance(this, "WOC_WRITE_ONLY_COLLECTION_FIELD", NORMAL_PRIORITY);
	}

	@Override
	protected BugInstance makeLocalBugInstance() {
		return new BugInstance(this, "WOC_WRITE_ONLY_COLLECTION_LOCAL", NORMAL_PRIORITY);
	}

	@Override
	protected Set<String> getObjectsThatNeedAMethod() {
		return collectionClasses;
	}

	@Override
	protected boolean isMethodThatShouldBeCalled(String methodName) {
		//If it's not a nonInformational method, i.e. something like get() or size(), then we don't need to report
		//the collection
		return !nonInformationalMethods.contains(methodName);
	}
}


