/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2017 Dave Brosius
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
import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;

/**
 * looks for method calls to collection classes where the method is not defined by the Collections interface, and an equivalent method exists in the interface.
 */
public class NonCollectionMethodUse extends BytecodeScanningDetector {
    private static final Set<FQMethod> oldMethods = UnmodifiableSet.create(new FQMethod("java/util/Hashtable", "contains", SignatureBuilder.SIG_OBJECT_TO_BOOLEAN),
            new FQMethod("java/util/Hashtable", "elements", new SignatureBuilder().withReturnType("java/util/Enumeration").toString()),
            new FQMethod("java/util/Hashtable", "keys", new SignatureBuilder().withReturnType("java/util/Enumeration").toString()),
            new FQMethod("java/util/Vector", "addElement", new SignatureBuilder().withParamTypes(Values.SLASHED_JAVA_LANG_OBJECT).toString()),
            new FQMethod("java/util/Vector", "elementAt", SignatureBuilder.SIG_INT_TO_OBJECT),
            new FQMethod("java/util/Vector", "insertElementAt", new SignatureBuilder().withParamTypes(Values.SLASHED_JAVA_LANG_OBJECT, Values.SIG_PRIMITIVE_INT).toString()),
            new FQMethod("java/util/Vector", "removeAllElements", SignatureBuilder.SIG_VOID_TO_VOID),
            new FQMethod("java/util/Vector", "removeElement", SignatureBuilder.SIG_OBJECT_TO_BOOLEAN),
            new FQMethod("java/util/Vector", "removeElementAt", SignatureBuilder.SIG_INT_TO_VOID),
            new FQMethod("java/util/Vector", "setElementAt", new SignatureBuilder().withParamTypes(Values.SLASHED_JAVA_LANG_OBJECT, Values.SIG_PRIMITIVE_INT).toString()));

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

    /**
     * implements the visitor to look for method calls that are one of the old pre-collections1.2 set of methods
     *
     * @param seen
     *            the currently parsed opcode
     */
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
