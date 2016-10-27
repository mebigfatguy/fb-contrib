/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2016 Dave Brosius
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
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;

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
        Map<FQMethod, Units> tugm = new HashMap<FQMethod, Units>(50);
        tugm.put(new FQMethod("java/lang/System", "currentTimeMillis", "()J"), Units.MILLIS);
        tugm.put(new FQMethod("java/lang/System", "nanoTime", "()J"), Units.NANOS);
        tugm.put(new FQMethod("java/sql/Timestamp", "getTime", "()J"), Units.MILLIS);
        tugm.put(new FQMethod("java/sql/Timestamp", "getNanos", "()I"), Units.NANOS);
        tugm.put(new FQMethod("java/util/Date", "getTime", "()J"), Units.MILLIS);
        tugm.put(new FQMethod("java/util/concurrent/TimeUnit", "toNanos", "(J)J"), Units.NANOS);
        tugm.put(new FQMethod("java/util/concurrent/TimeUnit", "toMicros", "(J)J"), Units.MICROS);
        tugm.put(new FQMethod("java/util/concurrent/TimeUnit", "toSeconds", "(J)J"), Units.SECONDS);
        tugm.put(new FQMethod("java/util/concurrent/TimeUnit", "toMinutes", "(J)J"), Units.MINUTES);
        tugm.put(new FQMethod("java/util/concurrent/TimeUnit", "toHours", "(J)J"), Units.HOURS);
        tugm.put(new FQMethod("java/util/concurrent/TimeUnit", "toDays", "(J)J"), Units.DAYS);
        tugm.put(new FQMethod("java/util/concurrent/TimeUnit", "excessNanos", "(JJ)I"), Units.NANOS);
        tugm.put(new FQMethod("java/util/concurrent/TimeUnit", "convert", "(JLjava/util/concurrent/TimeUnit;)J"), Units.CALLER);
        tugm.put(new FQMethod("edu/emory/matchcs/backport/java/util/concurrent/TimeUnit", "toNanos", "(J)J"), Units.NANOS);
        tugm.put(new FQMethod("edu/emory/matchcs/backport/java/util/concurrent/TimeUnit", "toMicros", "(J)J"), Units.MICROS);
        tugm.put(new FQMethod("edu/emory/matchcs/backport/java/util/concurrent/TimeUnit", "toSeconds", "(J)J"), Units.SECONDS);
        tugm.put(new FQMethod("edu/emory/matchcs/backport/java/util/concurrent/TimeUnit", "toMinutes", "(J)J"), Units.MINUTES);
        tugm.put(new FQMethod("edu/emory/matchcs/backport/java/util/concurrent/TimeUnit", "toHours", "(J)J"), Units.HOURS);
        tugm.put(new FQMethod("edu/emory/matchcs/backport/java/util/concurrent/TimeUnit", "toDays", "(J)J"), Units.DAYS);
        tugm.put(new FQMethod("edu/emory/matchcs/backport/java/util/concurrent/TimeUnit", "excessNanos", "(JJ)I"), Units.NANOS);
        tugm.put(new FQMethod("edu/emory/matchcs/backport/java/util/concurrent/TimeUnit", "convert", "(JLjava/util/concurrent/TimeUnit;)J"), Units.CALLER);
        tugm.put(new FQMethod("org/joda/time/base/BaseDuration", "getMillis", "()J"), Units.MILLIS);
        tugm.put(new FQMethod("org/joda/time/base/BaseInterval", "getEndMillis", "()J"), Units.MILLIS);
        tugm.put(new FQMethod("org/joda/time/base/BaseInterval", "getStartMillis", "()J"), Units.MILLIS);
        tugm.put(new FQMethod("java/time/Clock", "millis", "()J"), Units.MILLIS);
        tugm.put(new FQMethod("java/time/Duration", "getNano", "()I"), Units.NANOS);
        tugm.put(new FQMethod("java/time/Duration", "getSeconds", "()J"), Units.SECONDS);
        tugm.put(new FQMethod("java/time/Duration", "toDays", "()J"), Units.DAYS);
        tugm.put(new FQMethod("java/time/Duration", "toHours", "()J"), Units.HOURS);
        tugm.put(new FQMethod("java/time/Duration", "toMillis", "()J"), Units.MILLIS);
        tugm.put(new FQMethod("java/time/Duration", "toSeconds", "()J"), Units.SECONDS);
        tugm.put(new FQMethod("java/time/Duration", "toNanos", "()J"), Units.NANOS);
        tugm.put(new FQMethod("java/time/Instant", "getNano", "()I"), Units.NANOS);
        tugm.put(new FQMethod("java/time/Instant", "toEpochMmilli", "()J"), Units.MILLIS);
        tugm.put(new FQMethod("java/time/LocalDate", "getDayOfMonth", "()I"), Units.DAYS);
        tugm.put(new FQMethod("java/time/LocalDate", "getDayOfYear", "()I"), Units.DAYS);
        tugm.put(new FQMethod("java/time/LocalDate", "getEpochDay", "()J"), Units.DAYS);
        tugm.put(new FQMethod("java/time/LocalDateTime", "getDayOfMonth", "()J"), Units.DAYS);
        tugm.put(new FQMethod("java/time/LocalDateTime", "getDayOfYear", "()I"), Units.DAYS);
        tugm.put(new FQMethod("java/time/LocalDateTime", "getHour", "()I"), Units.HOURS);
        tugm.put(new FQMethod("java/time/LocalDateTime", "getNano", "()I"), Units.NANOS);
        tugm.put(new FQMethod("java/time/LocalDateTime", "getSecond", "()I"), Units.SECONDS);
        tugm.put(new FQMethod("java/time/LocalTime", "getHour", "()I"), Units.HOURS);
        tugm.put(new FQMethod("java/time/LocalTime", "getMinute", "()I"), Units.MINUTES);
        tugm.put(new FQMethod("java/time/LocalTime", "getNano", "()I"), Units.NANOS);
        tugm.put(new FQMethod("java/time/LocalTime", "getSecond", "()I"), Units.SECONDS);
        tugm.put(new FQMethod("java/time/LocalTime", "toNanoOfDay", "()J"), Units.NANOS);
        tugm.put(new FQMethod("java/time/LocalTime", "toSecondOfDay", "()I"), Units.SECONDS);
        TIME_UNIT_GENERATING_METHODS = Collections.<FQMethod, Units> unmodifiableMap(tugm);
    }

    private static final Map<String, Units> TIMEUNIT_TO_UNITS;

    static {
        Map<String, Units> tutu = new HashMap<String, Units>();
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
            int offset = Type.getArgumentTypes(signature).length;
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
