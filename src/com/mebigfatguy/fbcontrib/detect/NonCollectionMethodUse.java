/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2015 Dave Brosius
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
import com.mebigfatguy.fbcontrib.utils.FQMethod;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;

/**
 * looks for method calls to collection classes where the method is not defined
 * by the Collections interface, and an equivalent method exists in the
 * interface.
 */
public class NonCollectionMethodUse extends BytecodeScanningDetector {
    private static Set<FQMethod> oldMethods = new HashSet<FQMethod>();

    static {
        oldMethods.add(new FQMethod("java/util/Hashtable", "contains", "(java/lang/Object)Z"));
        oldMethods.add(new FQMethod("java/util/Hashtable", "elements", "()Ljava/util/Enumeration;"));
        oldMethods.add(new FQMethod("java/util/Hashtable", "keys", "()Ljava/util/Enumeration;"));
        oldMethods.add(new FQMethod("java/util/Vector", "addElement", "(Ljava/lang/Object;)V"));
        oldMethods.add(new FQMethod("java/util/Vector", "elementAt", "(I)Ljava/lang/Object;"));
        oldMethods.add(new FQMethod("java/util/Vector", "insertElementAt", "(Ljava/lang/Object;I)V"));
        oldMethods.add(new FQMethod("java/util/Vector", "removeAllElements", "()V"));
        oldMethods.add(new FQMethod("java/util/Vector", "removeElement", "(Ljava/lang/Object;)Z"));
        oldMethods.add(new FQMethod("java/util/Vector", "removeElementAt", "(I)V"));
        oldMethods.add(new FQMethod("java/util/Vector", "setElementAt", "(Ljava/lang/Object;I)V"));
    }

    private BugReporter bugReporter;

    /**
     * constructs a NCMU detector given the reporter to report bugs on
     * 
     * @param bugReporter
     *            the sync of bug reports
     */
    public NonCollectionMethodUse(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void sawOpcode(int seen) {
        if (seen == INVOKEVIRTUAL) {
            String className = getClassConstantOperand();
            String methodName = getNameConstantOperand();
            String methodSig = getSigConstantOperand();

            FQMethod methodInfo = new FQMethod(className, methodName, methodSig);
            if (oldMethods.contains(methodInfo)) {
                bugReporter.reportBug(new BugInstance(this, BugType.NCMU_NON_COLLECTION_METHOD_USE.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                        .addSourceLine(this));
            }
        }
    }
}
