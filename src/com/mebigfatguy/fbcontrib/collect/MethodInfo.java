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
package com.mebigfatguy.fbcontrib.collect;

public class MethodInfo {
    
    private short numMethodBytes;
    private byte numMethodCalls;
    private byte immutabilityOrdinal;
    
    public int getNumBytes() {
        return 0x0000FFFF & numMethodBytes;
    }

    public void setNumBytes(int numBytes) {
        numMethodBytes = (short) numBytes;
    }

    public int getNumMethodCalls() {
        return 0x000000FF & numMethodCalls;
    }

    public void setNumMethodCalls(int numCalls) {
        if (numCalls > 255) {
            numCalls = 255;
        }
        numMethodCalls = (byte) numCalls;
    }

    public  ImmutabilityType getImmutabilityType() {
        return ImmutabilityType.values()[immutabilityOrdinal];
    }

    public void setImmutabilityType(ImmutabilityType imType) {
        immutabilityOrdinal = (byte) imType.ordinal();
    }
    
    @Override
    public String toString() {
        return "NumMethodBytes: " + getNumBytes() + " NumMethodCalls: " + getNumMethodCalls() + " ImmutabilityType: " + getImmutabilityType();
    }
}