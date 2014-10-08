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
import java.util.Map;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for methods that perform arithmetic operations on values representing time
 * where the time unit is incompatible, ie adding a millisecond value to a nanosecond value.
 */
@CustomUserValue
public class ConflictingTimeUnits extends BytecodeScanningDetector {

	private enum Units { NANOS, MICROS, MILLIS, SECONDS, MINUTES, HOURS, DAYS, CALLER };
	
	private static Map<String, Units> TIME_UNIT_GENERATING_METHODS = new HashMap<String, Units>();
	static {
		TIME_UNIT_GENERATING_METHODS.put("java/lang/System.currentTimeMillis()J", Units.MILLIS);
		TIME_UNIT_GENERATING_METHODS.put("java/lang/System.nanoTime()J", Units.NANOS);
		TIME_UNIT_GENERATING_METHODS.put("java/sql/Timestamp.getTime()J", Units.MILLIS);
		TIME_UNIT_GENERATING_METHODS.put("java/sql/Timestamp.getNanos()I", Units.NANOS);
		TIME_UNIT_GENERATING_METHODS.put("java/util/Date.getTime()J", Units.MILLIS);
		TIME_UNIT_GENERATING_METHODS.put("java/util/concurrent/TimeUnit.toNanos(J)J", Units.NANOS);
		TIME_UNIT_GENERATING_METHODS.put("java/util/concurrent/TimeUnit.toMicros(J)J", Units.MICROS);
		TIME_UNIT_GENERATING_METHODS.put("java/util/concurrent/TimeUnit.toSeconds(J)J", Units.SECONDS);
		TIME_UNIT_GENERATING_METHODS.put("java/util/concurrent/TimeUnit.toMinutes(J)J", Units.MINUTES);
		TIME_UNIT_GENERATING_METHODS.put("java/util/concurrent/TimeUnit.toHours(J)J", Units.HOURS);
		TIME_UNIT_GENERATING_METHODS.put("java/util/concurrent/TimeUnit.toDays(J)J", Units.DAYS);
		TIME_UNIT_GENERATING_METHODS.put("java/util/concurrent/TimeUnit.excessNanos(JJ)I", Units.NANOS);
		TIME_UNIT_GENERATING_METHODS.put("java/util/concurrent/TimeUnit.convert(JLjava/util/concurrent/TimeUnit;)J", Units.CALLER);
		TIME_UNIT_GENERATING_METHODS.put("edu/emory/matchcs/backport/java/util/concurrent/TimeUnit.toNanos(J)J", Units.NANOS);
		TIME_UNIT_GENERATING_METHODS.put("edu/emory/matchcs/backport/java/util/concurrent/TimeUnit.toMicros(J)J", Units.MICROS);
		TIME_UNIT_GENERATING_METHODS.put("edu/emory/matchcs/backport/java/util/concurrent/TimeUnit.toSeconds(J)J", Units.SECONDS);
		TIME_UNIT_GENERATING_METHODS.put("edu/emory/matchcs/backport/java/util/concurrent/TimeUnit.toMinutes(J)J", Units.MINUTES);
		TIME_UNIT_GENERATING_METHODS.put("edu/emory/matchcs/backport/java/util/concurrent/TimeUnit.toHours(J)J", Units.HOURS);
		TIME_UNIT_GENERATING_METHODS.put("edu/emory/matchcs/backport/java/util/concurrent/TimeUnit.toDays(J)J", Units.DAYS);
		TIME_UNIT_GENERATING_METHODS.put("edu/emory/matchcs/backport/java/util/concurrent/TimeUnit.excessNanos(JJ)I", Units.NANOS);
		TIME_UNIT_GENERATING_METHODS.put("edu/emory/matchcs/backport/java/util/concurrent/TimeUnit.convert(JLjava/util/concurrent/TimeUnit;)J", Units.CALLER);
		TIME_UNIT_GENERATING_METHODS.put("org/joda/time/base/BaseDuration.getMillis()J", Units.MILLIS);
		TIME_UNIT_GENERATING_METHODS.put("org/joda/time/base/BaseInterval.getEndMillis()J", Units.MILLIS);
		TIME_UNIT_GENERATING_METHODS.put("org/joda/time/base/BaseInterval.getStartMillis()J", Units.MILLIS);
	}
	
	private static Map<String, Units> TIMEUNIT_TO_UNITS = new HashMap<String, Units>();
	static {
		TIMEUNIT_TO_UNITS.put("NANOSECONDS", Units.NANOS);
		TIMEUNIT_TO_UNITS.put("MICROSECONDS", Units.MICROS);
		TIMEUNIT_TO_UNITS.put("MILLISECONDS", Units.MILLIS);
		TIMEUNIT_TO_UNITS.put("SECONDS", Units.SECONDS);
		TIMEUNIT_TO_UNITS.put("MINUTES", Units.MINUTES);
		TIMEUNIT_TO_UNITS.put("HOURS", Units.HOURS);
		TIMEUNIT_TO_UNITS.put("DAYS", Units.DAYS);
	}
	
	private BugReporter bugReporter;
	private OpcodeStack stack;
	
	/**
	 * constructs a CTU detector given the reporter to report bugs on
	 * @param bugReporter the sync of bug reports
	 */
	public ConflictingTimeUnits(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}
	
	/**
	 * overrides the visitor to reset the stack
	 * 
	 * @param classContext the context object of the currently parsed class
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
	 * @param obj the context object for the currently parsed code block
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
					String methodCall = getClassConstantOperand() + "." + getNameConstantOperand() + getSigConstantOperand();
					unit = TIME_UNIT_GENERATING_METHODS.get(methodCall);
					if (unit == Units.CALLER) {
						int offset = Type.getArgumentTypes(getSigConstantOperand()).length;
						if (stack.getStackDepth() > offset) {
							OpcodeStack.Item item = stack.getStackItem(offset);
							unit = (Units) item.getUserValue();
						} else {
							unit = null;
						}
					}
				break;
				
				case GETSTATIC:
					String clsName = getClassConstantOperand();
					if ("java/util/concurrent/TimeUnit".equals(clsName) 
					||  "edu/emory/matchcs/backport/java/util/concurrent/TimeUnit".equals(clsName)) {
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
					if (stack.getStackDepth() > 1) {
						OpcodeStack.Item arg1 = stack.getStackItem(0);
						OpcodeStack.Item arg2 = stack.getStackItem(1);
						
						Units u1 = (Units) arg1.getUserValue();
						Units u2 = (Units) arg2.getUserValue();
						
						if ((u1 != null) && (u2 != null) && (u1 != u2)) {
							bugReporter.reportBug(new BugInstance(this, BugType.CTU_CONFLICTING_TIME_UNITS.name(), NORMAL_PRIORITY)
										.addClass(this)
										.addMethod(this)
										.addSourceLine(this)
										.addString(u1.toString())
										.addString(u2.toString()));
						}
					}
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
}
