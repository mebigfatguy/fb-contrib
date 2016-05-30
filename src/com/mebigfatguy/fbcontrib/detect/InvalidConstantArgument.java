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

import java.awt.Adjustable;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;
import javax.swing.border.BevelBorder;
import javax.swing.border.EtchedBorder;

import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableList;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * Looks for jdk method calls where a parameter expects a constant value, because the api was created before enums. Reports values that are not considered valid
 * values, and may cause problems with use.
 */
public class InvalidConstantArgument extends BytecodeScanningDetector {

    private static final List<InvalidPattern> PATTERNS = UnmodifiableList.create(
    // @formatter:off
            new InvalidPattern("javax/swing/JOptionPane#showMessageDialog\\(Ljava/awt/Component;Ljava/lang/Object;Ljava/lang/String;I\\)V",
                    ParameterInfo.createIntegerParameterInfo(0, false, JOptionPane.ERROR_MESSAGE, JOptionPane.INFORMATION_MESSAGE, JOptionPane.PLAIN_MESSAGE, JOptionPane.WARNING_MESSAGE)),
            new InvalidPattern("javax/swing/BorderFactory#createBevelBorder\\(I.*\\)Ljavax/swing/border/Border;",
                    ParameterInfo.createIntegerParameterInfo(0, true, BevelBorder.LOWERED, BevelBorder.RAISED)),
            new InvalidPattern("javax/swing/BorderFactory#createEtchedBorder\\(I.*\\)Ljavax/swing/border/Border;",
                    ParameterInfo.createIntegerParameterInfo(0, true, EtchedBorder.LOWERED, EtchedBorder.RAISED)),
            new InvalidPattern("javax/swing/JScrollBar#\\<init\\>\\(I.*\\)V",
                    ParameterInfo.createIntegerParameterInfo(0, true, Adjustable.HORIZONTAL, Adjustable.VERTICAL)),
            new InvalidPattern("java/lang/Thread#setPriority\\(I\\)V",
                    new ParameterInfo<Integer>(0, true, Range.createIntegerRange(Thread.MIN_PRIORITY, Thread.MAX_PRIORITY))),
            new InvalidPattern("java/math/BigDecimal#divide\\(Ljava/math/BigDecimal;.*I\\)Ljava/math/BigDecimal;",
                    new ParameterInfo<Integer>(0, false, Range.createIntegerRange(BigDecimal.ROUND_UP, BigDecimal.ROUND_UNNECESSARY))),
            new InvalidPattern("java/math/BigDecimal#setScale\\(II\\)Ljava/math/BigDecimal;",
                    new ParameterInfo<Integer>(0, false, Range.createIntegerRange(BigDecimal.ROUND_UP, BigDecimal.ROUND_UNNECESSARY))),
            new InvalidPattern("java/sql/Connection#createStatement\\(II\\)Ljava/sql/Statement;", ParameterInfo.createIntegerParameterInfo(0, true,
                    ResultSet.TYPE_FORWARD_ONLY, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.TYPE_SCROLL_SENSITIVE)),
            new InvalidPattern("java/sql/Connection#createStatement\\(III?\\)Ljava/sql/Statement;",
                    ParameterInfo.createIntegerParameterInfo(0, true, ResultSet.TYPE_FORWARD_ONLY, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.TYPE_SCROLL_SENSITIVE),
                    ParameterInfo.createIntegerParameterInfo(1, true, ResultSet.CONCUR_READ_ONLY, ResultSet.CONCUR_UPDATABLE)),

            new InvalidPattern("java/sql/Connection#prepare[^\\(]+\\(Ljava/lang/String;III?\\)Ljava/sql/PreparedStatement;",
                    ParameterInfo.createIntegerParameterInfo(1, true, ResultSet.TYPE_FORWARD_ONLY, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.TYPE_SCROLL_SENSITIVE),
                    ParameterInfo.createIntegerParameterInfo(2, true, ResultSet.CONCUR_READ_ONLY, ResultSet.CONCUR_UPDATABLE))
    // @formatter:on
    );

    private BugReporter bugReporter;
    private OpcodeStack stack;

    /**
     * constructs a ICA detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public InvalidConstantArgument(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * overrides the visitor to initialize the opcode stack
     *
     * @param classContext
     *            the context of the currently parsed class
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
     * overrides the visitor to reset the opcode stack
     *
     * @param obj
     *            the currently parsed method
     */
    @Override
    public void visitMethod(Method obj) {
        stack.resetForMethodEntry(this);
        super.visitMethod(obj);
    }

    @Override
    public void sawOpcode(int seen) {
        try {
            switch (seen) {
                case INVOKESPECIAL:
                case INVOKESTATIC:
                case INVOKEINTERFACE:
                case INVOKEVIRTUAL:
                    String sig = getSigConstantOperand();
                    String mInfo = getClassConstantOperand() + '#' + getNameConstantOperand() + sig;
                    for (InvalidPattern entry : PATTERNS) {
                        Matcher m = entry.getPattern().matcher(mInfo);
                        if (m.matches()) {
                            for (ParameterInfo<?> info : entry.getParmInfo()) {
                                int parmOffset = info.fromStart ? Type.getArgumentTypes(sig).length - info.parameterOffset - 1 : info.parameterOffset;
                                if (stack.getStackDepth() > parmOffset) {
                                    OpcodeStack.Item item = stack.getStackItem(parmOffset);

                                    Comparable cons = (Comparable) item.getConstant();
                                    if (!info.isValid(cons)) {
                                        int badParm = 1
                                                + (info.fromStart ? info.parameterOffset : Type.getArgumentTypes(sig).length - info.parameterOffset - 1);
                                        bugReporter.reportBug(new BugInstance(this, BugType.ICA_INVALID_CONSTANT_ARGUMENT.name(), NORMAL_PRIORITY)
                                                .addClass(this).addMethod(this).addSourceLine(this).addString("Parameter " + badParm));
                                        break;
                                    }
                                }
                            }
                            break;
                        }
                    }
                break;
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    static class InvalidPattern {
        private Pattern pattern;
        List<ParameterInfo<?>> parmInfo;

        public InvalidPattern(String invalidPattern, ParameterInfo<?>... parameterInfo) {
            pattern = Pattern.compile(invalidPattern);
            parmInfo = Arrays.asList(parameterInfo);
        }

        public Pattern getPattern() {
            return pattern;
        }

        public List<ParameterInfo<?>> getParmInfo() {
            return parmInfo;
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }

    /**
     * holds information about parameters that expect constant values that should have been enums but were created pre enums. It specifies the legal values, and
     * what offset from the start or end of the method the parm is
     */
    static class ParameterInfo<T extends Comparable<T>> {
        private int parameterOffset;
        private boolean fromStart;
        private Set<T> validValues;
        private Range<T> range;

        @SafeVarargs
        public ParameterInfo(int offset, boolean start, T... values) {
            parameterOffset = offset;
            fromStart = start;
            validValues = new HashSet<T>(Arrays.asList(values));
            range = null;
        }

        public ParameterInfo(int offset, boolean start, Range<T> rng) {
            parameterOffset = offset;
            fromStart = start;
            validValues = null;
            range = rng;
        }

        public static ParameterInfo<Integer> createIntegerParameterInfo(int offset, boolean start, int... values) {
            ParameterInfo<Integer> info = new ParameterInfo<Integer>(offset, start);
            for (int v : values) {
                info.validValues.add(Integer.valueOf(v));
            }

            return info;
        }

        public boolean isValid(Comparable<T> o) {
            if (o == null) {
                return true;
            }

            if (validValues != null) {
                return validValues.contains(o);
            }

            return (o.compareTo(range.getFrom()) >= 0) && (o.compareTo(range.getTo()) <= 0);
        }
    }

    static class Range<T extends Comparable<T>> {
        T from;
        T to;

        public Range(T f, T t) {
            from = f;
            to = t;
        }

        public static Range<Integer> createIntegerRange(int f, int t) {
            return new Range<Integer>(Integer.valueOf(f), Integer.valueOf(t));
        }

        public T getFrom() {
            return from;
        }

        public T getTo() {
            return to;
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }
}
