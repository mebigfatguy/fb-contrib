/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2015 Dave Brosius
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

import java.util.ArrayList;
import java.util.List;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.CodeException;
import org.apache.bcel.classfile.ExceptionTable;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.ToString;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * find methods that return or throw exception from a finally block. Doing so short-circuits the
 * return or exception thrown from the try block, and masks it.
 */
public class AbnormalFinallyBlockReturn extends BytecodeScanningDetector {
	private final BugReporter bugReporter;
	private List<FinallyBlockInfo> fbInfo;
    private int loadedReg;

    /**
     * constructs a AFBR detector given the reporter to report bugs on.

     * @param bugReporter the sync of bug reports
     */
	public AbnormalFinallyBlockReturn(final BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}

	/**
	 * overrides the visitor to check for java class version being as good or better than 1.4
	 *
	 * @param classContext the context object that holds the JavaClass parsed
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		//TODO: Look at method calls in a finally block to see if they throw exceptions
		//    : and those exceptions are not caught in the finally block
		//    : Only do it if effort is on, ie: boolean fullAnalysis = AnalysisContext.currentAnalysisContext().getBoolProperty(FindBugsAnalysisFeatures.INTERPROCEDURAL_ANALYSIS_OF_REFERENCED_CLASSES);

		try {
	        int majorVersion = classContext.getJavaClass().getMajor();
	        if (majorVersion >= MAJOR_1_4) {
	        	fbInfo = new ArrayList<FinallyBlockInfo>();
	        	super.visitClassContext(classContext);
	        }
		} finally {
        	fbInfo = null;
		}
    }

	/**
	 * overrides the visitor to collect finally block info.
	 *
	 * @param obj the code object to scan for finally blocks
	 */
	@Override
	public void visitCode(Code obj) {
		fbInfo.clear();
        loadedReg = -1;

		CodeException[] exc = obj.getExceptionTable();
		if (exc != null) {
			for (CodeException ce : exc) {
				if ((ce.getCatchType() == 0)
				&&  (ce.getStartPC() == ce.getHandlerPC())) {
                    fbInfo.add(new FinallyBlockInfo(ce.getStartPC()));
				}
			}
        }

		if (!fbInfo.isEmpty())
			super.visitCode(obj);
	}

	/**
	 * overrides the visitor to find return/exceptions from the finally block.
	 *
	 * @param seen the opcode that is being visited
	 */
	@Override
	public void sawOpcode(int seen) {
    	if (fbInfo.isEmpty())
    		return;

		FinallyBlockInfo fbi = fbInfo.get(0);

		if (getPC() < fbi.startPC)
			return;

        if (getPC() == fbi.startPC) {
            if (seen == ASTORE)
                fbi.exReg = getRegisterOperand();
            else if ((seen >= ASTORE_0) && (seen <= ASTORE_3))
                fbi.exReg = seen - ASTORE_0;
            else {
                fbInfo.remove(0);
                sawOpcode(seen);
                return;
            }
            return;
        }

		if (seen == MONITORENTER) {
			fbi.monitorCount++;
		} else if (seen == MONITOREXIT) {
			fbi.monitorCount--;
			if (fbi.monitorCount < 0) {
				fbInfo.remove(0);
				sawOpcode(seen);
				return;
			}
		}

        if ((seen == ATHROW) && (loadedReg == fbi.exReg)) {
            fbInfo.remove(0);
            sawOpcode(seen);
            return;
        }
        else if (seen == ALOAD)
            loadedReg = getRegisterOperand();
        else if ((seen >= ALOAD_0) && (seen <= ALOAD_3))
            loadedReg = seen - ALOAD_0;
        else
            loadedReg = -1;

		if (((seen >= IRETURN) && (seen <= RETURN)) || (seen == ATHROW)) {
			bugReporter.reportBug(new BugInstance( this, BugType.AFBR_ABNORMAL_FINALLY_BLOCK_RETURN.name(), NORMAL_PRIORITY)
					.addClass(this)
					.addMethod(this)
					.addSourceLine(this));
			fbInfo.remove(0);
		} else if ((seen == INVOKEVIRTUAL) || (seen == INVOKEINTERFACE) || (seen == INVOKESPECIAL) || (seen == INVOKESTATIC)) {
			try {
				JavaClass cls = Repository.lookupClass(getClassConstantOperand());
				Method m = findMethod(cls, getNameConstantOperand(), getSigConstantOperand());
				if (m != null) {
					ExceptionTable et = m.getExceptionTable();
					if ((et != null) && (et.getLength() > 0)) {
						if (!catchBlockInFinally(fbi)) {
							bugReporter.reportBug(new BugInstance( this, BugType.AFBR_ABNORMAL_FINALLY_BLOCK_RETURN.name(), LOW_PRIORITY)
									.addClass(this)
									.addMethod(this)
									.addSourceLine(this));
							fbInfo.remove(0);
						}
					}
				}
			} catch (ClassNotFoundException cnfe) {
				bugReporter.reportMissingClass(cnfe);
			}
		}
	}

	/**
	 * finds the method in specified class by name and signature
	 *
	 * @param cls the class to look the method in
	 * @param name the name of the method to look for
	 * @param sig the signature of the method to look for
	 *
	 * @return the Method object for the specified information
	 */
	private static Method findMethod(JavaClass cls, String name, String sig) {
		Method[] methods = cls.getMethods();
		for (Method m : methods) {
			if (m.getName().equals(name) && m.getSignature().equals(sig)) {
				return m;
			}
		}

		return null;
	}

	/**
	 * looks to see if any try/catch block exists inside this finally block, that
	 * wrap the current pc. This is a lax check as the try catch block may not
	 * catch exceptions that are thrown, but doing so would be prohibitively slow.
	 * But it should catch some problems.
	 *
	 * @param fBlockInfo the finally block the pc is currently in
	 *
	 * @return if all exceptions are caught inside this finally block
	 */
	private boolean catchBlockInFinally(FinallyBlockInfo fBlockInfo) {

		CodeException[] catchExceptions = getCode().getExceptionTable();
		if ((catchExceptions == null) || (catchExceptions.length == 0))
			return false;

		int pc = getPC();
		for (CodeException ex : catchExceptions) {
			if ((ex.getStartPC() <= pc) && (ex.getEndPC() >= pc)) {
				if (ex.getStartPC() >= fBlockInfo.startPC) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * holds the finally block information for a particular method.
	 */
	static class FinallyBlockInfo
	{
		public int startPC;
		public int monitorCount;
        public int exReg;

        /**
         * create a finally block info for a specific code range
         *
         * @param start the start of the try block
         */
		FinallyBlockInfo(int start) {
			startPC = start;
			monitorCount = 0;
            exReg = -1;
		}
		
		@Override
		public String toString() {
			return ToString.build(this);
		}
	}
}