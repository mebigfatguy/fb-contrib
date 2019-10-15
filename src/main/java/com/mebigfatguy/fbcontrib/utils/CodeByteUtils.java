/*
 * fb-contrib - Auxiliary detectors for Java programs
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

/**
 * a collection of static methods for working with retrieving arbitrary code
 * bytes in a code byte array
 *
 */
public final class CodeByteUtils {

    private CodeByteUtils() {
    }

    /**
     * returns the code byte at a specific offset as an int
     *
     * @param bytes  the code bytes
     * @param offset the offset into the code
     * @return the byte as an int
     */
    public static int getbyte(byte[] bytes, int offset) {
        return (0x00FF & bytes[offset]);
    }

    /**
     * returns the code short at a specific offset as an int
     *
     * @param bytes  the code bytes
     * @param offset the offset into the code
     * @return the short as an int
     */
    public static int getshort(byte[] bytes, int offset) {
        return (short) ((0x0000FFFF & (bytes[offset] << 8)) | (0x00FF & bytes[offset + 1]));
    }
}
