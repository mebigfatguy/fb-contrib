/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2017 Chris Peterson
 * Copyright (C) 2005-2017 Jean-Noel Rouvignac
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
import java.util.Map;
import java.util.Set;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;

import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for method calls that are unsafe or might indicate bugs.
 */
public class MoreDumbMethods extends BytecodeScanningDetector {
    private final static Map<FQMethod, ReportInfo> dumbMethods = new HashMap<>();

    private static final Set<ReportInfo> assertableReports = UnmodifiableSet.create(new ReportInfo("MDM_LOCK_ISLOCKED", LOW_PRIORITY));

    private static final String byteArrayToVoid = new SignatureBuilder().withParamTypes(SignatureBuilder.SIG_BYTE_ARRAY).toString();
    private static final String intToByteArray = new SignatureBuilder().withParamTypes(Values.SIG_PRIMITIVE_INT).withReturnType(SignatureBuilder.SIG_BYTE_ARRAY)
            .toString();

    static {
        dumbMethods.put(new FQMethod("java/lang/Runtime", "exit", SignatureBuilder.SIG_INT_TO_VOID), new ReportInfo("MDM_RUNTIME_EXIT_OR_HALT", LOW_PRIORITY));
        dumbMethods.put(new FQMethod("java/lang/Runtime", "halt", SignatureBuilder.SIG_INT_TO_VOID), new ReportInfo("MDM_RUNTIME_EXIT_OR_HALT", HIGH_PRIORITY));

        dumbMethods.put(new FQMethod("java/lang/Runtime", "runFinalization", SignatureBuilder.SIG_VOID_TO_VOID),
                new ReportInfo("MDM_RUNFINALIZATION", NORMAL_PRIORITY));
        dumbMethods.put(new FQMethod("java/lang/System", "runFinalization", SignatureBuilder.SIG_VOID_TO_VOID),
                new ReportInfo("MDM_RUNFINALIZATION", NORMAL_PRIORITY));

        dumbMethods.put(new FQMethod("java/math/BigDecimal", "equals", SignatureBuilder.SIG_OBJECT_TO_BOOLEAN),
                new ReportInfo("MDM_BIGDECIMAL_EQUALS", NORMAL_PRIORITY));

        //
        // Network checks
        //
        dumbMethods.put(new FQMethod("java/net/InetAddress", "getLocalHost", new SignatureBuilder().withReturnType("java/net/InetAddress").toString()),
                new ReportInfo("MDM_INETADDRESS_GETLOCALHOST", NORMAL_PRIORITY));

        dumbMethods.put(new FQMethod("java/net/ServerSocket", Values.CONSTRUCTOR, SignatureBuilder.SIG_INT_TO_VOID),
                new ReportInfo("MDM_PROMISCUOUS_SERVERSOCKET", NORMAL_PRIORITY));
        dumbMethods.put(
                new FQMethod("java/net/ServerSocket", Values.CONSTRUCTOR,
                        new SignatureBuilder().withParamTypes(Values.SIG_PRIMITIVE_INT, Values.SIG_PRIMITIVE_INT).toString()),
                new ReportInfo("MDM_PROMISCUOUS_SERVERSOCKET", NORMAL_PRIORITY));
        dumbMethods.put(
                new FQMethod("javax/net/ServerSocketFactory", "createServerSocket",
                        new SignatureBuilder().withParamTypes(Values.SIG_PRIMITIVE_INT).withReturnType("java/net/ServerSocket").toString()),
                new ReportInfo("MDM_PROMISCUOUS_SERVERSOCKET", LOW_PRIORITY));
        dumbMethods.put(
                new FQMethod("javax/net/ServerSocketFactory", "createServerSocket", new SignatureBuilder()
                        .withParamTypes(Values.SIG_PRIMITIVE_INT, Values.SIG_PRIMITIVE_INT).withReturnType("java/net/ServerSocket").toString()),
                new ReportInfo("MDM_PROMISCUOUS_SERVERSOCKET", LOW_PRIORITY));

        //
        // Random Number Generator checks
        //
        dumbMethods.put(new FQMethod("java/util/Random", Values.CONSTRUCTOR, SignatureBuilder.SIG_VOID_TO_VOID),
                new ReportInfo("MDM_RANDOM_SEED", LOW_PRIORITY));

        //
        // Thread checks
        //
        dumbMethods.put(new FQMethod("java/lang/Thread", "getPriority", SignatureBuilder.SIG_VOID_TO_INT),
                new ReportInfo("MDM_THREAD_PRIORITIES", LOW_PRIORITY));
        dumbMethods.put(new FQMethod("java/lang/Thread", "setPriority", SignatureBuilder.SIG_INT_TO_VOID),
                new ReportInfo("MDM_THREAD_PRIORITIES", LOW_PRIORITY));

        dumbMethods.put(new FQMethod("java/lang/Thread", "sleep", SignatureBuilder.SIG_LONG_TO_VOID), new ReportInfo("MDM_THREAD_YIELD", LOW_PRIORITY));
        dumbMethods.put(new FQMethod("java/lang/Thread", "sleep", SignatureBuilder.SIG_LONG_AND_INT_TO_VOID), new ReportInfo("MDM_THREAD_YIELD", LOW_PRIORITY));
        dumbMethods.put(new FQMethod("java/lang/Thread", "yield", SignatureBuilder.SIG_VOID_TO_VOID), new ReportInfo("MDM_THREAD_YIELD", NORMAL_PRIORITY));

        dumbMethods.put(new FQMethod("java/lang/Thread", "join", SignatureBuilder.SIG_VOID_TO_VOID), new ReportInfo("MDM_WAIT_WITHOUT_TIMEOUT", LOW_PRIORITY));
        dumbMethods.put(new FQMethod(Values.SLASHED_JAVA_LANG_OBJECT, "wait", SignatureBuilder.SIG_VOID_TO_VOID),
                new ReportInfo("MDM_WAIT_WITHOUT_TIMEOUT", LOW_PRIORITY));
        dumbMethods.put(new FQMethod("java/util/concurrent/locks/Condition", "await", SignatureBuilder.SIG_VOID_TO_VOID),
                new ReportInfo("MDM_WAIT_WITHOUT_TIMEOUT", LOW_PRIORITY));
        dumbMethods.put(new FQMethod("java/util/concurrent/locks/Lock", "lock", SignatureBuilder.SIG_VOID_TO_VOID),
                new ReportInfo("MDM_WAIT_WITHOUT_TIMEOUT", LOW_PRIORITY));
        dumbMethods.put(new FQMethod("java/util/concurrent/locks/Lock", "lockInterruptibly", SignatureBuilder.SIG_VOID_TO_VOID),
                new ReportInfo("MDM_WAIT_WITHOUT_TIMEOUT", LOW_PRIORITY));
        dumbMethods.put(new FQMethod("java/util/concurrent/locks/ReentrantLock", "lock", SignatureBuilder.SIG_VOID_TO_VOID),
                new ReportInfo("MDM_WAIT_WITHOUT_TIMEOUT", LOW_PRIORITY));
        dumbMethods.put(new FQMethod("java/util/concurrent/locks/ReentrantLock", "lockInterruptibly", SignatureBuilder.SIG_VOID_TO_VOID),
                new ReportInfo("MDM_WAIT_WITHOUT_TIMEOUT", LOW_PRIORITY));

        dumbMethods.put(new FQMethod("java/util/concurrent/locks/Condition", "signal", SignatureBuilder.SIG_VOID_TO_VOID),
                new ReportInfo("MDM_SIGNAL_NOT_SIGNALALL", NORMAL_PRIORITY));

        dumbMethods.put(new FQMethod("java/util/concurrent/locks/Lock", "tryLock", SignatureBuilder.SIG_VOID_TO_BOOLEAN),
                new ReportInfo("MDM_THREAD_FAIRNESS", LOW_PRIORITY));
        dumbMethods.put(new FQMethod("java/util/concurrent/locks/ReentrantLock", "tryLock", SignatureBuilder.SIG_VOID_TO_BOOLEAN),
                new ReportInfo("MDM_THREAD_FAIRNESS", LOW_PRIORITY));

        dumbMethods.put(new FQMethod("java/util/concurrent/locks/ReentrantLock", "isHeldByCurrentThread", SignatureBuilder.SIG_VOID_TO_BOOLEAN),
                new ReportInfo("MDM_LOCK_ISLOCKED", LOW_PRIORITY));
        dumbMethods.put(new FQMethod("java/util/concurrent/locks/ReentrantLock", "isLocked", SignatureBuilder.SIG_VOID_TO_BOOLEAN),
                new ReportInfo("MDM_LOCK_ISLOCKED", LOW_PRIORITY));

        //
        // String checks
        //
        dumbMethods.put(
                new FQMethod(Values.SLASHED_JAVA_LANG_STRING, Values.CONSTRUCTOR,
                        new SignatureBuilder().withParamTypes(SignatureBuilder.SIG_BYTE_ARRAY).toString()),
                new ReportInfo("MDM_STRING_BYTES_ENCODING", NORMAL_PRIORITY));
        dumbMethods.put(
                new FQMethod(Values.SLASHED_JAVA_LANG_STRING, "getBytes", new SignatureBuilder().withReturnType(SignatureBuilder.SIG_BYTE_ARRAY).toString()),
                new ReportInfo("MDM_STRING_BYTES_ENCODING", NORMAL_PRIORITY));
        dumbMethods.put(new FQMethod("java/util/Locale", "setDefault", new SignatureBuilder().withParamTypes("java/util/Locale").toString()),
                new ReportInfo("MDM_SETDEFAULTLOCALE", NORMAL_PRIORITY));
    }

    private final BugReporter bugReporter;

    private boolean sawAssertionDisabled;
    private int assertionEnd;

    /**
     * constructs an MDM detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public MoreDumbMethods(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        if (classContext.getJavaClass().getMajor() <= MAJOR_1_5) {
            dumbMethods.put(new FQMethod("java/security/SecureRandom", Values.CONSTRUCTOR, SignatureBuilder.SIG_VOID_TO_VOID),
                    new ReportInfo("MDM_SECURERANDOM", LOW_PRIORITY));
            dumbMethods.put(new FQMethod("java/security/SecureRandom", Values.CONSTRUCTOR, byteArrayToVoid), new ReportInfo("MDM_SECURERANDOM", LOW_PRIORITY));
            dumbMethods.put(new FQMethod("java/security/SecureRandom", "getSeed", intToByteArray), new ReportInfo("MDM_SECURERANDOM", LOW_PRIORITY));
        } else {
            dumbMethods.remove(new FQMethod("java/security/SecureRandom", Values.CONSTRUCTOR, SignatureBuilder.SIG_VOID_TO_VOID));
            dumbMethods.remove(new FQMethod("java/security/SecureRandom", Values.CONSTRUCTOR, byteArrayToVoid));
            dumbMethods.remove(new FQMethod("java/security/SecureRandom", "getSeed", intToByteArray));
        }

        super.visitClassContext(classContext);
    }

    @Override
    public void visitCode(Code obj) {
        sawAssertionDisabled = false;
        assertionEnd = 0;
        super.visitCode(obj);
    }

    @Override
    public void sawOpcode(int seen) {

        if (OpcodeUtils.isStandardInvoke(seen)) {
            final ReportInfo info = dumbMethods.get(getFQMethod());
            if ((info != null) && ((assertionEnd < getPC()) || !assertableReports.contains(info))) {
                reportBug(info);
            }
        } else if (seen == Const.GETSTATIC) {
            if ("$assertionsDisabled".equals(getNameConstantOperand())) {
                sawAssertionDisabled = true;
                return;
            }
        } else if ((seen == Const.IFNE) && sawAssertionDisabled) {
            assertionEnd = getBranchTarget();
        }

        sawAssertionDisabled = false;
    }

    private FQMethod getFQMethod() {
        final String className = getClassConstantOperand();
        final String methodName = getNameConstantOperand();
        final String methodSig = getSigConstantOperand();
        return new FQMethod(className, methodName, methodSig);
    }

    private void reportBug(ReportInfo info) {
        bugReporter.reportBug(
                new BugInstance(this, info.getPattern(), info.getPriority()).addClass(this).addMethod(this).addCalledMethod(this).addSourceLine(this));
    }

    private static class ReportInfo {
        private final String bugPattern;
        private final int bugPriority;

        ReportInfo(String pattern, int priority) {
            bugPattern = pattern;
            bugPriority = priority;
        }

        String getPattern() {
            return bugPattern;
        }

        int getPriority() {
            return bugPriority;
        }

        @Override
        public int hashCode() {
            return bugPattern.hashCode() ^ bugPriority;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ReportInfo)) {
                return false;
            }

            ReportInfo that = (ReportInfo) o;
            return (bugPriority == that.bugPriority) && bugPattern.equals(that.bugPattern);
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }

}
