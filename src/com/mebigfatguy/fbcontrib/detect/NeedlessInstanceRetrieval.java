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
package com.mebigfatguy.fbcontrib.detect;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.LineNumberTable;
import org.apache.bcel.generic.Type;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;

/**
 * looks for methods that call a method to retrieve a reference to an object, 
 * to use to load a constant. It is simpler and more performant to access the
 * static variable directly from the class itself.
 */
public class NeedlessInstanceRetrieval extends BytecodeScanningDetector
{
	enum State {SEEN_NOTHING, SEEN_INVOKE, SEEN_POP}
	
	private final BugReporter bugReporter;
    private LineNumberTable lnTable;
	private State state;
    private int invokePC;
    private String returnType;
	/**
     * constructs a NIR detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
	 */
	public NeedlessInstanceRetrieval(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}
	
    /**
     * overrides the interface to collect the line number table, and reset state
     * 
     * @param obj the content object of the currently parsed code
     */
    @Override
    public void visitCode(Code obj) {
        try {
            lnTable = obj.getLineNumberTable();
            if (lnTable != null) {
                state = State.SEEN_NOTHING;
                invokePC = -1;
                returnType = null;
                super.visitCode(obj);
            }
        } finally {
            lnTable = null;
        }
    }
    
	/**
	 * overrides the interface to find accesses of static variables off of an instance
	 * immediately fetched from a method call.
	 * 
	 * @param seen the opcode of the currently visited instruction
	 */
	@Override
	public void sawOpcode(int seen) {
		switch (state)
		{
			case SEEN_NOTHING:
				if (seen == INVOKEINTERFACE
				||  seen == INVOKEVIRTUAL) {
					String sig = getSigConstantOperand();
					Type retType = Type.getReturnType(sig);
					if (retType.getSignature().startsWith("L")) {
						String clsName = getClassConstantOperand();
						if (!"java/lang/Object".equals(clsName)
						&&  !"java/lang/String".equals(clsName)) {
						    returnType = retType.getSignature();
						    returnType = returnType.substring(1, returnType.length() - 1);
	                        invokePC = getPC();
							state = State.SEEN_INVOKE;
						}
                    }
				}
			break;
			
			case SEEN_INVOKE:
				if (seen == POP)
					state = State.SEEN_POP;
				else {
					state = State.SEEN_NOTHING;
					returnType = null;
				}
			break;
			
			case SEEN_POP:
				if ((seen >= ACONST_NULL && seen <= DCONST_1) || (seen == GETFIELD)) {
				    state = State.SEEN_POP;
				} else if ((seen == INVOKESTATIC) || (seen == GETSTATIC)) {
				    if (getClassConstantOperand().equals(returnType)) {
                        if (lnTable.getSourceLine(invokePC) == lnTable.getSourceLine(getPC())) {
        					bugReporter.reportBug(new BugInstance(this, "NIR_NEEDLESS_INSTANCE_RETRIEVAL", NORMAL_PRIORITY)
        									.addClass(this)
        									.addMethod(this)
        									.addSourceLine(this));
                        }
				    }
                    state = State.SEEN_NOTHING;
                    returnType = null;
				} else {
				    state = State.SEEN_NOTHING;
                    returnType = null;
				}
			break;
            
            default:
                state = State.SEEN_NOTHING;
                returnType = null;
            break;
		}
	}
}
 