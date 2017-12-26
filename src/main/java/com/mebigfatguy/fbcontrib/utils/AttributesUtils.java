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
package com.mebigfatguy.fbcontrib.utils;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.LineNumber;
import org.apache.bcel.classfile.LineNumberTable;

/**
 * a collection of static methods for working with code attribute queries
 */
public final class AttributesUtils {
    private AttributesUtils() {
    }

    /**
     * returns whether the pc is at a line number that also appears for a another byte code offset later on in the method. If this occurs we are in a jdk6
     * finally replicated block, and so don't report this. If the code has no line number table, then just report it.
     *
     * @param obj
     *            the code object to find line number attributes from
     * @param pc
     *            the pc to check
     *
     * @return whether the pc is in user code
     */
    public static boolean isValidLineNumber(Code obj, int pc) {
        LineNumberTable lnt = obj.getLineNumberTable();
        if (lnt == null)
            return true;

        LineNumber[] lineNumbers = lnt.getLineNumberTable();
        if (lineNumbers == null)
            return true;

        int lo = 0;
        int hi = lineNumbers.length - 1;
        int mid = 0;
        int linePC = 0;
        while (lo <= hi) {
            mid = (lo + hi) >>> 1;
            linePC = lineNumbers[mid].getStartPC();
            if (linePC == pc)
                break;
            if (linePC < pc)
                lo = mid + 1;
            else
                hi = mid - 1;
        }

        int lineNo = lineNumbers[mid].getLineNumber();

        for (int i = 0; i < lineNumbers.length; i++) {
            if ((mid != i) && (lineNumbers[i].getLineNumber() == lineNo))
                return false;
        }

        return true;
    }
}
