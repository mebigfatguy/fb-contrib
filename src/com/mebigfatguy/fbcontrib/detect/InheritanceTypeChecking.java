/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2012 Dave Brosius
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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.bcel.classfile.Code;

import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for if/else blocks where a series of them use instanceof on the same 
 * variable to determine what do to. If these classes are related by inheritance,
 * this often is better handled through calling a single overridden method.
 */
public class InheritanceTypeChecking extends BytecodeScanningDetector 
{
	private BugReporter bugReporter;
	private Set<IfStatement> ifStatements;

	/**
     * constructs a ITC detector given the reporter to report bugs on
     * @param bugReporter the sync of bug reports
	 */
	public InheritanceTypeChecking(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}
	
	/**
	 * implements the visitor to allocate and clear the ifStatements set
	 * 
	 * @param classContext the context object of the currently parsed class
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
			ifStatements = new HashSet<IfStatement>();
			super.visitClassContext(classContext);
		} finally {
			ifStatements = null;
		}
	}
	
	/**
	 * implements the visitor to clear the ifStatements set
	 * 
	 * @param obj the context object of the currently parsed code block
	 */
	@Override
	public void visitCode(Code obj) {
		ifStatements.clear();
		super.visitCode(obj);
	}
	
	/**
	 * implements the visitor to find if/else code that checks types using
	 * instanceof, and these types are related by inheritance.
	 * 
	 * @param seen the opcode of the currently parsed instruction
	 */
	@Override
	public void sawOpcode(int seen) {
		boolean processed = false;
		Iterator<IfStatement> isi = ifStatements.iterator();
		while (isi.hasNext()) {
			IfStatement.Action action = isi.next().processOpcode(this, bugReporter, seen);
			if (action == IfStatement.Action.REMOVE_ACTION)
				isi.remove();
			else if (action == IfStatement.Action.PROCESSED_ACTION)
				processed = true;
		}
		
		if (!processed) {
			if ((seen == ALOAD)
			||  ((seen >= ALOAD_0) && (seen <= ALOAD_3))) {
				IfStatement is = new IfStatement(this, seen);
				ifStatements.add(is);
			}
		}
	}
	
	private static class IfStatement {
		enum Action {NO_ACTION, REMOVE_ACTION, PROCESSED_ACTION}
		enum State {SEEN_ALOAD, SEEN_INSTANCEOF, SEEN_IFEQ}

		private State state;
		private int reg;
		private int firstPC;
		private int branchTarget;
		private int matchCount;
		private Set<String> instanceOfTypes;
		
		public IfStatement(BytecodeScanningDetector bsd, int seen) {
			state = State.SEEN_ALOAD;
			reg = RegisterUtils.getALoadReg(bsd, seen);
			matchCount = 0;
			firstPC = bsd.getPC();
		}
		
		public IfStatement.Action processOpcode(BytecodeScanningDetector bsd, BugReporter bugReporter, int seen) {
			switch (state) {
					case SEEN_ALOAD:
					if (seen == INSTANCEOF) {
						if (instanceOfTypes == null)
							instanceOfTypes = new HashSet<String>();
						instanceOfTypes.add(bsd.getClassConstantOperand());
						state = State.SEEN_INSTANCEOF;
						return IfStatement.Action.PROCESSED_ACTION;						
					}
				break;

				case SEEN_INSTANCEOF:
					if (seen == IFEQ) {
						branchTarget = bsd.getBranchTarget();
						state = State.SEEN_IFEQ;
						matchCount++;
						return IfStatement.Action.PROCESSED_ACTION;
					}
				break;
				
				case SEEN_IFEQ:
					if (bsd.getPC() == branchTarget) {
						if ((seen == ALOAD)
						||  ((seen >= ALOAD_0) && (seen <= ALOAD_3))) {
							if (reg == RegisterUtils.getALoadReg(bsd, seen)) {
								state = State.SEEN_ALOAD;
								return IfStatement.Action.PROCESSED_ACTION;
							}
						}
						if (matchCount > 1) {
							String clsName = bsd.getClassName();
							int priority = NORMAL_PRIORITY;
							for (String type : instanceOfTypes) {
								if (!SignatureUtils.similarPackages(clsName, type, 2)) {
									priority = LOW_PRIORITY;
									break;
								}
							}
							
							bugReporter.reportBug(new BugInstance(bsd, "ITC_INHERITANCE_TYPE_CHECKING", priority)
									   .addClass(bsd)
									   .addMethod(bsd)
									   .addSourceLine(bsd, firstPC));
							return IfStatement.Action.REMOVE_ACTION;
						}
					}
					return IfStatement.Action.NO_ACTION;
			}
			
			return IfStatement.Action.REMOVE_ACTION;
		}
	}
}
