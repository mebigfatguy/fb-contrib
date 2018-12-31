/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2019 Kevin Lubick
 * Copyright (C) 2005-2019 Dave Brosius
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
package com.mebigfatguy.fbcontrib.utils;

import java.util.BitSet;

import org.apache.bcel.Const;

/**
 * a collection of static methods for categorizing opcodes into groups
 */
public final class OpcodeUtils {

    private static final BitSet BRANCH_OPS = new BitSet();
    private static final BitSet INVOKE_OPS = new BitSet();

    static {
        BRANCH_OPS.set(Const.GOTO);
        BRANCH_OPS.set(Const.GOTO_W);
        BRANCH_OPS.set(Const.IF_ACMPEQ);
        BRANCH_OPS.set(Const.IF_ACMPNE);
        BRANCH_OPS.set(Const.IF_ICMPEQ);
        BRANCH_OPS.set(Const.IF_ICMPGE);
        BRANCH_OPS.set(Const.IF_ICMPGT);
        BRANCH_OPS.set(Const.IF_ICMPLE);
        BRANCH_OPS.set(Const.IF_ICMPLT);
        BRANCH_OPS.set(Const.IF_ICMPNE);
        BRANCH_OPS.set(Const.IFEQ);
        BRANCH_OPS.set(Const.IFGE);
        BRANCH_OPS.set(Const.IFGT);
        BRANCH_OPS.set(Const.IFLE);
        BRANCH_OPS.set(Const.IFLT);
        BRANCH_OPS.set(Const.IFNE);
        BRANCH_OPS.set(Const.IFNONNULL);
        BRANCH_OPS.set(Const.IFNULL);

        INVOKE_OPS.set(Const.INVOKEVIRTUAL);
        INVOKE_OPS.set(Const.INVOKESTATIC);
        INVOKE_OPS.set(Const.INVOKEINTERFACE);
        INVOKE_OPS.set(Const.INVOKESPECIAL);
        INVOKE_OPS.set(Const.INVOKEDYNAMIC);
    }

    private OpcodeUtils() {
    }

    public static boolean isALoad(int seen) {
        return (seen == Const.ALOAD) || ((seen >= Const.ALOAD_0) && (seen <= Const.ALOAD_3));
    }

    public static boolean isAStore(int seen) {
        return (seen == Const.ASTORE) || ((seen >= Const.ASTORE_0) && (seen <= Const.ASTORE_3));
    }

    public static boolean isILoad(int seen) {
        return (seen == Const.ILOAD) || ((seen >= Const.ILOAD_0) && (seen <= Const.ILOAD_3));
    }

    public static boolean isIStore(int seen) {
        return (seen == Const.ISTORE) || ((seen >= Const.ISTORE_0) && (seen <= Const.ISTORE_3));
    }

    public static boolean isLLoad(int seen) {
        return (seen == Const.LLOAD) || ((seen >= Const.LLOAD_0) && (seen <= Const.LLOAD_3));
    }

    public static boolean isLStore(int seen) {
        return (seen == Const.LSTORE) || ((seen >= Const.LSTORE_0) && (seen <= Const.LSTORE_3));
    }

    public static boolean isFLoad(int seen) {
        return (seen == Const.FLOAD) || ((seen >= Const.FLOAD_0) && (seen <= Const.FLOAD_3));
    }

    public static boolean isFStore(int seen) {
        return (seen == Const.FSTORE) || ((seen >= Const.FSTORE_0) && (seen <= Const.FSTORE_3));
    }

    public static boolean isDLoad(int seen) {
        return (seen == Const.DLOAD) || ((seen >= Const.DLOAD_0) && (seen <= Const.DLOAD_3));
    }

    public static boolean isDStore(int seen) {
        return (seen == Const.DSTORE) || ((seen >= Const.DSTORE_0) && (seen <= Const.DSTORE_3));
    }

    public static boolean isLoad(int seen) {
        return isALoad(seen) || isILoad(seen) || isLLoad(seen) || isFLoad(seen) || isDLoad(seen);
    }

    public static boolean isStore(int seen) {
        return isAStore(seen) || isIStore(seen) || isLStore(seen) || isFStore(seen) || isDStore(seen);
    }

    public static boolean isInvoke(int seen) {
        return INVOKE_OPS.get(seen);
    }

    public static boolean isStandardInvoke(int seen) {
        return (seen == Const.INVOKESPECIAL) || (seen == Const.INVOKEINTERFACE) || (seen == Const.INVOKEVIRTUAL) || (seen == Const.INVOKESTATIC);
    }

    public static boolean isBranch(int seen) {
        return BRANCH_OPS.get(seen);
    }

    public static boolean isReturn(int seen) {
        return ((seen == Const.ARETURN) || (seen == Const.IRETURN) || (seen == Const.LRETURN) || (seen == Const.FRETURN) || (seen == Const.DRETURN)
                || (seen == Const.RETURN));
    }

}
