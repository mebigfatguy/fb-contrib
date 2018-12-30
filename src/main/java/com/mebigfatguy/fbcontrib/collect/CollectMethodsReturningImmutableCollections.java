/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2018 Dave Brosius
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
package com.mebigfatguy.fbcontrib.collect;

import java.util.Set;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.CollectionUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.NonReportingDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * collects methods that return a collection that could be created thru an immutable method such as Arrays.aslist, etc.
 */
@CustomUserValue
public class CollectMethodsReturningImmutableCollections extends BytecodeScanningDetector implements NonReportingDetector {

    private static final Set<String> IMMUTABLE_PRODUCING_METHODS = UnmodifiableSet.create(
    //@formatter:off
            "com/google/common/Collect/Maps.immutableEnumMap", "com/google/common/Collect/Maps.unmodifiableMap",
            "com/google/common/Collect/Sets.immutableEnumSet", "com/google/common/Collect/Sets.immutableCopy",
            "java/util/Arrays.asList", "java/util/Collections.unmodifiableCollection",
            "java/util/Collections.unmodifiableSet", "java/util/Collections.unmodifiableSortedSet",
            "java/util/Collections.unmodifiableMap", "java/util/Collections.unmodifiableList",
            "edu/emory/mathcs/backport/java/util/Arrays.asList",
            "edu/emory/mathcs/backport/java/util/Collections.unmodifiableCollection",
            "edu/emory/mathcs/backport/java/util/Collections.unmodifiableSet",
            "edu/emory/mathcs/backport/java/util/Collections.unmodifiableSortedSet",
            "edu/emory/mathcs/backport/java/util/Collections.unmodifiableMap",
            "edu/emory/mathcs/backport/java/util/Collections.unmodifiableList"
    // @formatter:on
    );

    private BugReporter bugReporter;
    private OpcodeStack stack;
    private String clsName;
    private ImmutabilityType imType;

    /**
     * constructs a CMRIC detector given the reporter to report bugs on
     *
     * @param reporter
     *            the sync of bug reports
     */
    public CollectMethodsReturningImmutableCollections(BugReporter reporter) {
        bugReporter = reporter;
    }

    @Override
    public void visitClassContext(ClassContext context) {
        try {
            stack = new OpcodeStack();
            clsName = context.getJavaClass().getClassName();
            super.visitClassContext(context);
        } finally {
            stack = null;
        }
    }

    /**
     * overrides the visitor to reset the stack for the new method, then checks if the immutability field is set to immutable and if so reports it
     *
     * @param obj
     *            the context object of the currently parsed method
     */
    @Override
    public void visitCode(Code obj) {
        try {
            String signature = SignatureUtils.getReturnSignature(getMethod().getSignature());
            if (signature.startsWith(Values.SIG_QUALIFIED_CLASS_PREFIX) && CollectionUtils.isListSetMap(SignatureUtils.stripSignature(signature))) {
                stack.resetForMethodEntry(this);
                imType = ImmutabilityType.UNKNOWN;

                super.visitCode(obj);

                if ((imType == ImmutabilityType.IMMUTABLE) || (imType == ImmutabilityType.POSSIBLY_IMMUTABLE)) {
                    Method m = getMethod();
                    Statistics.getStatistics().addImmutabilityStatus(clsName, m.getName(), m.getSignature(), imType);
                }
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        }
    }

    /**
     * overrides the visitor to look for calls to static methods that are known to return immutable collections It records those variables, and documents if
     * what the method returns is one of those objects.
     */
    @Override
    public void sawOpcode(int seen) {
        ImmutabilityType seenImmutable = null;
        try {
            stack.precomputation(this);

            switch (seen) {
                case Const.INVOKESTATIC: {
                    String className = getClassConstantOperand();
                    String methodName = getNameConstantOperand();

                    if (IMMUTABLE_PRODUCING_METHODS.contains(className + '.' + methodName)) {
                        seenImmutable = ImmutabilityType.IMMUTABLE;
                        break;
                    }
                }
                //$FALL-THROUGH$
                case Const.INVOKEINTERFACE:
                case Const.INVOKESPECIAL:
                case Const.INVOKEVIRTUAL: {
                    String className = getClassConstantOperand();
                    String methodName = getNameConstantOperand();
                    String signature = getSigConstantOperand();

                    MethodInfo mi = Statistics.getStatistics().getMethodStatistics(className, methodName, signature);
                    seenImmutable = mi.getImmutabilityType();
                    if (seenImmutable == ImmutabilityType.UNKNOWN) {
                        seenImmutable = null;
                    }
                }
                break;

                case Const.ARETURN: {
                    processARreturn();
                    break;
                }
                default:
                break;
            }

        } finally {
            stack.sawOpcode(this, seen);
            if ((seenImmutable != null) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item item = stack.getStackItem(0);
                item.setUserValue(seenImmutable);
            }
        }
    }

    private void processARreturn() {
        if (stack.getStackDepth() > 0) {
            OpcodeStack.Item item = stack.getStackItem(0);
            ImmutabilityType type = (ImmutabilityType) item.getUserValue();
            if (type == null) {
                type = ImmutabilityType.UNKNOWN;
            }

            switch (imType) {
                case UNKNOWN:

                    switch (type) {
                        case IMMUTABLE:
                            imType = ImmutabilityType.IMMUTABLE;
                        break;
                        case POSSIBLY_IMMUTABLE:
                            imType = ImmutabilityType.POSSIBLY_IMMUTABLE;
                        break;
                        default:
                            imType = ImmutabilityType.MUTABLE;
                        break;
                    }
                break;

                case IMMUTABLE:
                    if (type != ImmutabilityType.IMMUTABLE) {
                        imType = ImmutabilityType.POSSIBLY_IMMUTABLE;
                    }
                break;

                case POSSIBLY_IMMUTABLE:
                break;

                case MUTABLE:
                    if (type == ImmutabilityType.IMMUTABLE) {
                        imType = ImmutabilityType.POSSIBLY_IMMUTABLE;
                    }
                break;
            }
        }
    }
}
