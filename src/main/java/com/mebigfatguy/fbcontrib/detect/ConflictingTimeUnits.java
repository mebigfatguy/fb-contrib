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
package com.mebigfatguy.fbcontrib.detect;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.bcel.classfile.Code;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for methods that perform arithmetic operations on values representing time where the time unit is incompatible, ie adding a millisecond value to a
 * nanosecond value.
 */
@CustomUserValue
public class ConflictingTimeUnits extends BytecodeScanningDetector {

    private enum Units {
        NANOS, MICROS, MILLIS, SECONDS, MINUTES, HOURS, DAYS, CALLER
    };

    private static final Map<FQMethod, Units> TIME_UNIT_GENERATING_METHODS;

    static {
        String voidToLong = new SignatureBuilder().withReturnType(Values.SIG_PRIMITIVE_LONG).toString();
        String longToLong = new SignatureBuilder().withParamTypes(Values.SIG_PRIMITIVE_LONG).withReturnType(Values.SIG_PRIMITIVE_LONG).toString();
        Map<FQMethod, Units> tugm = new HashMap<>(50);
        tugm.put(new FQMethod("java/lang/System", "currentTimeMillis", voidToLong), Units.MILLIS);
        tugm.put(new FQMethod("java/lang/System", "nanoTime", voidToLong), Units.NANOS);
        tugm.put(new FQMethod("java/sql/Timestamp", "getTime", voidToLong), Units.MILLIS);
        tugm.put(new FQMethod("java/sql/Timestamp", "getNanos", SignatureBuilder.SIG_VOID_TO_INT), Units.NANOS);
        tugm.put(new FQMethod("java/util/Date", "getTime", voidToLong), Units.MILLIS);
        tugm.put(new FQMethod("java/util/concurrent/TimeUnit", "toNanos", longToLong), Units.NANOS);
        tugm.put(new FQMethod("java/util/concurrent/TimeUnit", "toMicros", longToLong), Units.MICROS);
        tugm.put(new FQMethod("java/util/concurrent/TimeUnit", "toSeconds", longToLong), Units.SECONDS);
        tugm.put(new FQMethod("java/util/concurrent/TimeUnit", "toMinutes", longToLong), Units.MINUTES);
        tugm.put(new FQMethod("java/util/concurrent/TimeUnit", "toHours", longToLong), Units.HOURS);
        tugm.put(new FQMethod("java/util/concurrent/TimeUnit", "toDays", longToLong), Units.DAYS);
        tugm.put(
                new FQMethod("java/util/concurrent/TimeUnit", "excessNanos", new SignatureBuilder()
                        .withParamTypes(Values.SIG_PRIMITIVE_LONG, Values.SIG_PRIMITIVE_LONG).withReturnType(Values.SIG_PRIMITIVE_INT).toString()),
                Units.NANOS);
        tugm.put(
                new FQMethod("java/util/concurrent/TimeUnit", "convert", new SignatureBuilder()
                        .withParamTypes(Values.SIG_PRIMITIVE_LONG, "java/util/concurrent/TimeUnit").withReturnType(Values.SIG_PRIMITIVE_LONG).toString()),
                Units.CALLER);
        tugm.put(new FQMethod("edu/emory/matchcs/backport/java/util/concurrent/TimeUnit", "toNanos", longToLong), Units.NANOS);
        tugm.put(new FQMethod("edu/emory/matchcs/backport/java/util/concurrent/TimeUnit", "toMicros", longToLong), Units.MICROS);
        tugm.put(new FQMethod("edu/emory/matchcs/backport/java/util/concurrent/TimeUnit", "toSeconds", longToLong), Units.SECONDS);
        tugm.put(new FQMethod("edu/emory/matchcs/backport/java/util/concurrent/TimeUnit", "toMinutes", longToLong), Units.MINUTES);
        tugm.put(new FQMethod("edu/emory/matchcs/backport/java/util/concurrent/TimeUnit", "toHours", longToLong), Units.HOURS);
        tugm.put(new FQMethod("edu/emory/matchcs/backport/java/util/concurrent/TimeUnit", "toDays", longToLong), Units.DAYS);
        tugm.put(
                new FQMethod("edu/emory/matchcs/backport/java/util/concurrent/TimeUnit", "excessNanos", new SignatureBuilder()
                        .withParamTypes(Values.SIG_PRIMITIVE_LONG, Values.SIG_PRIMITIVE_LONG).withReturnType(Values.SIG_PRIMITIVE_INT).toString()),
                Units.NANOS);
        tugm.put(
                new FQMethod("edu/emory/matchcs/backport/java/util/concurrent/TimeUnit", "convert", new SignatureBuilder()
                        .withParamTypes(Values.SIG_PRIMITIVE_LONG, "java/util/concurrent/TimeUnit").withReturnType(Values.SIG_PRIMITIVE_LONG).toString()),
                Units.CALLER);
        tugm.put(new FQMethod("org/joda/time/base/BaseDuration", "getMillis", voidToLong), Units.MILLIS);
        tugm.put(new FQMethod("org/joda/time/base/BaseInterval", "getEndMillis", voidToLong), Units.MILLIS);
        tugm.put(new FQMethod("org/joda/time/base/BaseInterval", "getStartMillis", voidToLong), Units.MILLIS);
        tugm.put(new FQMethod("java/time/Clock", "millis", voidToLong), Units.MILLIS);
        tugm.put(new FQMethod("java/time/Duration", "getNano", SignatureBuilder.SIG_VOID_TO_INT), Units.NANOS);
        tugm.put(new FQMethod("java/time/Duration", "getSeconds", voidToLong), Units.SECONDS);
        tugm.put(new FQMethod("java/time/Duration", "toDays", voidToLong), Units.DAYS);
        tugm.put(new FQMethod("java/time/Duration", "toHours", voidToLong), Units.HOURS);
        tugm.put(new FQMethod("java/time/Duration", "toMillis", voidToLong), Units.MILLIS);
        tugm.put(new FQMethod("java/time/Duration", "toSeconds", voidToLong), Units.SECONDS);
        tugm.put(new FQMethod("java/time/Duration", "toNanos", voidToLong), Units.NANOS);
        tugm.put(new FQMethod("java/time/Instant", "getNano", SignatureBuilder.SIG_VOID_TO_INT), Units.NANOS);
        tugm.put(new FQMethod("java/time/Instant", "toEpochMilli", voidToLong), Units.MILLIS);
        tugm.put(new FQMethod("java/time/LocalDate", "getDayOfMonth", SignatureBuilder.SIG_VOID_TO_INT), Units.DAYS);
        tugm.put(new FQMethod("java/time/LocalDate", "getDayOfYear", SignatureBuilder.SIG_VOID_TO_INT), Units.DAYS);
        tugm.put(new FQMethod("java/time/LocalDate", "getEpochDay", voidToLong), Units.DAYS);
        tugm.put(new FQMethod("java/time/LocalDateTime", "getDayOfMonth", voidToLong), Units.DAYS);
        tugm.put(new FQMethod("java/time/LocalDateTime", "getDayOfYear", SignatureBuilder.SIG_VOID_TO_INT), Units.DAYS);
        tugm.put(new FQMethod("java/time/LocalDateTime", "getHour", SignatureBuilder.SIG_VOID_TO_INT), Units.HOURS);
        tugm.put(new FQMethod("java/time/LocalDateTime", "getNano", SignatureBuilder.SIG_VOID_TO_INT), Units.NANOS);
        tugm.put(new FQMethod("java/time/LocalDateTime", "getSecond", SignatureBuilder.SIG_VOID_TO_INT), Units.SECONDS);
        tugm.put(new FQMethod("java/time/LocalTime", "getHour", SignatureBuilder.SIG_VOID_TO_INT), Units.HOURS);
        tugm.put(new FQMethod("java/time/LocalTime", "getMinute", SignatureBuilder.SIG_VOID_TO_INT), Units.MINUTES);
        tugm.put(new FQMethod("java/time/LocalTime", "getNano", SignatureBuilder.SIG_VOID_TO_INT), Units.NANOS);
        tugm.put(new FQMethod("java/time/LocalTime", "getSecond", SignatureBuilder.SIG_VOID_TO_INT), Units.SECONDS);
        tugm.put(new FQMethod("java/time/LocalTime", "toNanoOfDay", voidToLong), Units.NANOS);
        tugm.put(new FQMethod("java/time/LocalTime", "toSecondOfDay", SignatureBuilder.SIG_VOID_TO_INT), Units.SECONDS);
        TIME_UNIT_GENERATING_METHODS = Collections.<FQMethod, Units> unmodifiableMap(tugm);
    }

    private static final Map<String, Units> TIMEUNIT_TO_UNITS;

    static {
        Map<String, Units> tutu = new HashMap<>();
        tutu.put("NANOSECONDS", Units.NANOS);
        tutu.put("MICROSECONDS", Units.MICROS);
        tutu.put("MILLISECONDS", Units.MILLIS);
        tutu.put("SECONDS", Units.SECONDS);
        tutu.put("MINUTES", Units.MINUTES);
        tutu.put("HOURS", Units.HOURS);
        tutu.put("DAYS", Units.DAYS);
        TIMEUNIT_TO_UNITS = Collections.<String, Units> unmodifiableMap(tutu);
    }

    private BugReporter bugReporter;
    private OpcodeStack stack;

    /**
     * constructs a CTU detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public ConflictingTimeUnits(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * overrides the visitor to reset the stack
     *
     * @param classContext
     *            the context object of the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
        }
    }

    /**
     * overrides the visitor to resets the stack for this method.
     *
     * @param obj
     *            the context object for the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        super.visitCode(obj);
    }

    /**
     * overrides the visitor to look for operations on two time unit values that are conflicting
     */
    @Override
    public void sawOpcode(int seen) {
        Units unit = null;
        try {
            stack.precomputation(this);

            switch (seen) {
                case INVOKEVIRTUAL:
                case INVOKEINTERFACE:
                case INVOKESTATIC:
                    unit = processInvoke();
                break;

                case GETSTATIC:
                    String clsName = getClassConstantOperand();
                    if ("java/util/concurrent/TimeUnit".equals(clsName) || "edu/emory/matchcs/backport/java/util/concurrent/TimeUnit".equals(clsName)) {
                        unit = TIMEUNIT_TO_UNITS.get(getNameConstantOperand());
                    }
                break;

                case L2I:
                case I2L:
                    if (stack.getStackDepth() > 0) {
                        OpcodeStack.Item item = stack.getStackItem(0);
                        unit = (Units) item.getUserValue();
                    }
                break;

                case IADD:
                case ISUB:
                case IMUL:
                case IDIV:
                case IREM:
                case LADD:
                case LSUB:
                case LMUL:
                case LDIV:
                case LREM:
                    processArithmetic();
                break;

                default:
                break;
            }
        } finally {
            stack.sawOpcode(this, seen);
            if ((unit != null) && (stack.getStackDepth() > 0)) {
                OpcodeStack.Item item = stack.getStackItem(0);
                item.setUserValue(unit);
            }
        }
    }

    private Units processInvoke() {
        String signature = getSigConstantOperand();
        FQMethod methodCall = new FQMethod(getClassConstantOperand(), getNameConstantOperand(), signature);
        Units unit = TIME_UNIT_GENERATING_METHODS.get(methodCall);
        if (unit == Units.CALLER) {
            int offset = SignatureUtils.getNumParameters(signature);
            if (stack.getStackDepth() > offset) {
                OpcodeStack.Item item = stack.getStackItem(offset);
                unit = (Units) item.getUserValue();
            } else {
                unit = null;
            }
        }

        return unit;
    }

    // false positive; we're comparing enums
    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    private void processArithmetic() {
        if (stack.getStackDepth() > 1) {
            OpcodeStack.Item arg1 = stack.getStackItem(0);
            OpcodeStack.Item arg2 = stack.getStackItem(1);

            Units u1 = (Units) arg1.getUserValue();
            Units u2 = (Units) arg2.getUserValue();

            if ((u1 != null) && (u2 != null) && (u1 != u2)) {
                bugReporter.reportBug(new BugInstance(this, BugType.CTU_CONFLICTING_TIME_UNITS.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                        .addSourceLine(this).addString(u1.toString()).addString(u2.toString()));
            }
        }
    }
}
