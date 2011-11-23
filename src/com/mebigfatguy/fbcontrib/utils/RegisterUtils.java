/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2011 Dave Brosius
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

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.LocalVariable;
import org.apache.bcel.classfile.LocalVariableTable;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import edu.umd.cs.findbugs.visitclass.DismantleBytecode;

/**
 * an auxillary class for managing and manipulating registers
 */
public class RegisterUtils {
	
	/**
	 * private to reinforce the helper status of the class
	 */
	private RegisterUtils() {		
	}
	
    /**
     * returns the register used to store a reference
     * 
     * @param dbc the dismantle byte code parsing the class
     * @param seen the opcode of the store
     * @return the register stored into
     */
    public static int getAStoreReg(final DismantleBytecode dbc, final int seen) {
        if (seen == Constants.ASTORE)
            return dbc.getRegisterOperand();
        if ((seen >= Constants.ASTORE_0) && (seen <= Constants.ASTORE_3))
        	return seen - Constants.ASTORE_0;
        return -1;
    }
    
    /**
     * returns the register used to load a reference
     * 
     * @param dbc the dismantle byte code parsing the class
     * @param seen the opcode of the load
     * @return the register loaded from
     */ 
    public static int getALoadReg(DismantleBytecode dbc, int seen) {
        if (seen == Constants.ALOAD)
            return dbc.getRegisterOperand();
        if ((seen >= Constants.ALOAD_0) && (seen <= Constants.ALOAD_3))
        	return seen - Constants.ALOAD_0;
        return -1;
    }  
    
    /**
     * returns the register used in a store operation
     * 
     * @param dbc the dismantle byte code parsing the class
     * @param seen the opcode of the store
     * @return the register stored into
     */
	public static int getStoreReg(DismantleBytecode dbc, int seen) {
		if ((seen == Constants.ASTORE) 
		||  (seen == Constants.ISTORE) 
		||  (seen == Constants.LSTORE) 
		||  (seen == Constants.FSTORE) 
		||  (seen == Constants.DSTORE))
			return dbc.getRegisterOperand();
		if (seen <= Constants.ISTORE_3)
			return seen - Constants.ISTORE_0;
		else if (seen <= Constants.LSTORE_3)
			return seen - Constants.LSTORE_0;
		else if (seen <= Constants.FSTORE_3)
			return seen - Constants.FSTORE_0;
		else if (seen <= Constants.DSTORE_3)
			return seen - Constants.DSTORE_0;
		return seen - Constants.ASTORE_0;
	}
	
    /**
     * returns the register used in a load operation
     * 
     * @param dbc the dismantle byte code parsing the class
     * @param seen the opcode of the load
     * @return the register stored into
     */
	public static int getLoadReg(DismantleBytecode dbc, int seen) {
		if ((seen == Constants.ALOAD) 
		||  (seen == Constants.ILOAD) 
		||  (seen == Constants.LLOAD) 
		||  (seen == Constants.FLOAD) 
		||  (seen == Constants.DLOAD))
			return dbc.getRegisterOperand();
		if (seen <= Constants.ILOAD_3)
			return seen - Constants.ILOAD_0;
		else if (seen <= Constants.LLOAD_3)
			return seen - Constants.LLOAD_0;
		else if (seen <= Constants.FLOAD_3)
			return seen - Constants.FLOAD_0;
		else if (seen <= Constants.DLOAD_3)
			return seen - Constants.DLOAD_0;
		return seen - Constants.ALOAD_0;
	}
	
    /**
     * returns the end pc of the visible range of this register at this pc
     * 
     * @param lvt the local variable table for this method
     * @param reg the register to examine
     * @param curPC the pc of the current instruction
     * @return the endpc
     */
    public static int getLocalVariableEndRange(LocalVariableTable lvt, int reg, int curPC) {
        int endRange = Integer.MAX_VALUE;
        if (lvt != null) {
            LocalVariable lv = lvt.getLocalVariable(reg, curPC);
            if (lv != null)
                endRange = lv.getStartPC() + lv.getLength();
        }
        return endRange;
    }
    
    /**
     * gets the set of registers used for parameters
     * 
     * @param obj the context object for the method to find the parameter registers of
     */
    public static int[] getParameterRegisters(Method obj) {
	    Type[] argTypes = obj.getArgumentTypes();
    	int[] regs = new int[argTypes.length];
    	
    	int curReg = obj.isStatic() ? 0 : 1;
    	for (int t = 0; t < argTypes.length; t++) {
            String sig = argTypes[t].getSignature();
            regs[t] = curReg;
            if ("J".equals(sig) || "D".equals(sig))
                curReg++;
            curReg++;    	
        }
    	return regs;
    }
}
