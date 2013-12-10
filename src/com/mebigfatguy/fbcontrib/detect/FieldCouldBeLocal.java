/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2013 Dave Brosius
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

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.ConstantUtf8;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.FieldInstruction;
import org.apache.bcel.generic.GETFIELD;
import org.apache.bcel.generic.INVOKESPECIAL;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionHandle;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.FieldAnnotation;
import edu.umd.cs.findbugs.SourceLineAnnotation;
import edu.umd.cs.findbugs.ba.BasicBlock;
import edu.umd.cs.findbugs.ba.BasicBlock.InstructionIterator;
import edu.umd.cs.findbugs.ba.CFG;
import edu.umd.cs.findbugs.ba.CFGBuilderException;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.Edge;

/**
 * finds fields that are used in a locals only fashion, specifically private fields
 * that are accessed first in each method with a store vs. a load.
 */
public class FieldCouldBeLocal extends BytecodeScanningDetector
{
	private final BugReporter bugReporter;
	private ClassContext clsContext;
	private Map<String, FieldInfo> localizableFields;
	private CFG cfg;
	private ConstantPoolGen cpg;
	private BitSet visitedBlocks;

    /**
     * constructs a FCBL detector given the reporter to report bugs on.

     * @param bugReporter the sync of bug reports
     */
	public FieldCouldBeLocal(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}

	/**
	 * overrides the visitor to collect localizable fields, and then report those that
	 * survive all method checks.
	 *
	 * @param classContext the context object that holds the JavaClass parsed
	 */
	@Override
	public void visitClassContext(ClassContext classContext) {
		try {
	        localizableFields = new HashMap<String, FieldInfo>();
	        visitedBlocks = new BitSet();
			clsContext = classContext;
			JavaClass cls = classContext.getJavaClass();
			Field[] fields = cls.getFields();
			ConstantPool cp = classContext.getConstantPoolGen().getConstantPool();
			
			for (Field f : fields) {
				if ((!f.isStatic() && f.getName().indexOf('$') < 0) && f.isPrivate()) {
					FieldAnnotation fa = new FieldAnnotation(cls.getClassName(), f.getName(), f.getSignature(), false);
					boolean hasExternalAnnotation = false;
					for (AnnotationEntry entry : f.getAnnotationEntries()) {
					    ConstantUtf8 cutf = (ConstantUtf8) cp.getConstant(entry.getTypeIndex());
					    if (!cutf.getBytes().startsWith("java")) {
					        hasExternalAnnotation = true;
					        break;
					    }
					}
					localizableFields.put(f.getName(), new FieldInfo(fa, hasExternalAnnotation));
				}
			}

			if (localizableFields.size() > 0) {
				super.visitClassContext(classContext);
				for (FieldInfo fi : localizableFields.values()) {
					FieldAnnotation fa = fi.getFieldAnnotation();
					SourceLineAnnotation sla = fi.getSrcLineAnnotation();
					BugInstance bug = new BugInstance(this, "FCBL_FIELD_COULD_BE_LOCAL", NORMAL_PRIORITY)
													.addClass(this)
													.addField(fa);
					if (sla != null)
						bug.addSourceLine(sla);
					bugReporter.reportBug(bug);
				}
			}
		} finally {
	        localizableFields = null;
	        visitedBlocks = null;
	        clsContext = null;
		}
	}

	/**
	 * overrides the visitor to navigate basic blocks looking for all first usages of fields, removing
	 * those that are read from first.
	 *
	 * @param obj the context object of the currently parsed method
	 */
	@Override
	public void visitMethod(Method obj) {
		if (localizableFields.isEmpty())
			return;

		try {

			cfg = clsContext.getCFG(obj);
			cpg = cfg.getMethodGen().getConstantPool();
			BasicBlock bb = cfg.getEntry();
			Set<String> uncheckedFields = new HashSet<String>(localizableFields.keySet());
			visitedBlocks.clear();
			checkBlock(bb, uncheckedFields);
		}
		catch (CFGBuilderException cbe) {
			localizableFields.clear();
		}
        finally {
            cfg = null;
            cpg = null;
        }
	}

	/**
	 * looks for methods that contain a GETFIELD or PUTFIELD opcodes
	 *
	 * @param method the context object of the current method
	 * @return if the class uses synchronization
	 */
	public boolean prescreen(Method method) {
		BitSet bytecodeSet = getClassContext().getBytecodeSet(method);
		return (bytecodeSet != null) && (bytecodeSet.get(Constants.PUTFIELD) || bytecodeSet.get(Constants.GETFIELD));
	}

	/**
	 * implements the visitor to pass through constructors and static initializers to the
	 * byte code scanning code. These methods are not reported, but are used to build
	 * SourceLineAnnotations for fields, if accessed.
	 *
	 * @param obj the context object of the currently parsed code attribute
	 */
	@Override
	public void visitCode(Code obj) {
		Method m = getMethod();
		if (prescreen(m)) {
			String methodName = m.getName();
			if ("<clinit".equals(methodName) || "<init>".equals(methodName))
				super.visitCode(obj);
		}
	}

	/**
	 * implements the visitor to add SourceLineAnnotations for fields in constructors and static
	 * initializers.
	 *
	 * @param seen the opcode of the currently visited instruction
	 */
	@Override
	public void sawOpcode(int seen) {
		if ((seen == GETFIELD) || (seen == PUTFIELD)) {
			String fieldName = getNameConstantOperand();
			FieldInfo fi = localizableFields.get(fieldName);
			if (fi != null) {
				SourceLineAnnotation sla = SourceLineAnnotation.fromVisitedInstruction(this);
				fi.setSrcLineAnnotation(sla);
			}
		}
	}

	/**
	 * looks in this basic block for the first access to the fields in uncheckedFields. Once found
	 * the item is removed from uncheckedFields, and removed from localizableFields if the access is
	 * a GETFIELD. If any unchecked fields remain, this method is recursively called on all outgoing edges
	 * of this basic block.
	 *
	 * @param bb this basic block
	 * @param uncheckedFields the list of fields to look for
	 */
	private void checkBlock(BasicBlock bb, Set<String> uncheckedFields) {
		LinkedList<BlockState> toBeProcessed = new LinkedList<BlockState>();
		toBeProcessed.add(new BlockState(bb, uncheckedFields));
		visitedBlocks.set(bb.getLabel());

		while (!toBeProcessed.isEmpty()) {
			if (localizableFields.isEmpty())
				return;
			BlockState bState = toBeProcessed.removeFirst();
			bb = bState.getBasicBlock();

			InstructionIterator ii = bb.instructionIterator();
			while ((bState.getUncheckedFieldSize() > 0) && ii.hasNext()) {
				InstructionHandle ih = ii.next();
				Instruction ins = ih.getInstruction();
				if (ins instanceof FieldInstruction) {
					FieldInstruction fi = (FieldInstruction) ins;
					String fieldName = fi.getFieldName(cpg);
					FieldInfo finfo = localizableFields.get(fieldName);
					
					if ((finfo != null) && localizableFields.get(fieldName).hasAnnotation()) {
					    localizableFields.remove(fieldName);
					} else {
    					boolean justRemoved = bState.removeUncheckedField(fieldName);
    
    					if (ins instanceof GETFIELD) {
    						if (justRemoved) {
    							localizableFields.remove(fieldName);
    							if (localizableFields.isEmpty())
    								return;
    						}
    					} else {
    						if (finfo != null)
    							finfo.setSrcLineAnnotation(SourceLineAnnotation.fromVisitedInstruction(clsContext, this, ih.getPosition()));
    					}
					}
				} else if (ins instanceof INVOKESPECIAL) {
				    INVOKESPECIAL is = (INVOKESPECIAL) ins;
				    
				    if ("<init>".equals(is.getMethodName(cpg)) && (is.getClassName(cpg).startsWith(clsContext.getJavaClass().getClassName() + "$"))) {  
				        localizableFields.clear();
				    }
				}
			}

			if (bState.getUncheckedFieldSize() > 0) {
				Iterator<Edge> oei = cfg.outgoingEdgeIterator(bb);
				while (oei.hasNext()) {
					Edge e = oei.next();
					BasicBlock cb = e.getTarget();
					int label = cb.getLabel();
					if (!visitedBlocks.get(label)) {
						toBeProcessed.addLast(new BlockState(cb, bState));
						visitedBlocks.set(label);
					}
				}
			}
		}
	}

	/**
	 * holds information about a field and it's first usage
	 */
	private static class FieldInfo {
		private final FieldAnnotation fieldAnnotation;
		private SourceLineAnnotation srcLineAnnotation;
		private boolean hasAnnotation;

		/**
		 * creates a FieldInfo from an annotation, and assumes no source line information
		 * @param fa the field annotation for this field
		 * @param hasExternalAnnotation the field has a non java based annotation
		 */
		public FieldInfo(final FieldAnnotation fa, boolean hasExternalAnnotation) {
			fieldAnnotation = fa;
			srcLineAnnotation = null;
			hasAnnotation = hasExternalAnnotation;
		}

		/**
		 * set the source line annotation of first use for this field
		 * @param sla the source line annotation
		 */
		public void setSrcLineAnnotation(final SourceLineAnnotation sla) {
			if (srcLineAnnotation == null)
				srcLineAnnotation = sla;
		}

		/**
		 * get the field annotation for this field
		 * @return the field annotation
		 */
		public FieldAnnotation getFieldAnnotation() {
			return fieldAnnotation;
		}

		/**
		 * get the source line annotation for the first use of this field
		 * @return the source line annotation
		 */
		public SourceLineAnnotation getSrcLineAnnotation() {
			return srcLineAnnotation;
		}
		
		/**
		 * gets whether the field has a non java annotation
		 * @return if the field has a non java annotation
		 */
		public boolean hasAnnotation() {
		    return hasAnnotation;
		}
	}

	/**
	 * holds the parse state of the current basic block, and what fields are left to be checked
	 * the fields that are left to be checked are a reference from the parent block
	 * and a new collection is created on first write to the set to reduce memory concerns.
	 */
	private static class BlockState {
		private final BasicBlock basicBlock;
		private Set<String> uncheckedFields;
		private boolean fieldsAreSharedWithParent;

		/**
		 * creates a BlockState consisting of the next basic block to parse,
		 * and what fields are to be checked
		 * @param bb the basic block to parse
		 * @param fields the fields to look for first use
		 */
		public BlockState(final BasicBlock bb, final Set<String> fields) {
			basicBlock = bb;
			uncheckedFields = fields;
			fieldsAreSharedWithParent = true;
		}

		/**
		 * creates a BlockState consisting of the next basic block to parse,
		 * and what fields are to be checked
		 * @param bb the basic block to parse
		 * @param the basic block to copy from
		 */
		public BlockState(final BasicBlock bb, BlockState parentBlockState) {
			basicBlock = bb;
			uncheckedFields = parentBlockState.uncheckedFields;
			fieldsAreSharedWithParent = true;
		}

		/**
		 * get the basic block to parse
		 * @return the basic block
		 */
		public BasicBlock getBasicBlock() {
			return basicBlock;
		}

		/**
		 * returns the number of unchecked fields
		 * @return the number of unchecked fields
		 */
		public int getUncheckedFieldSize() {
			return (uncheckedFields == null) ? 0 : uncheckedFields.size();
		}

		/**
		 * return the field from the set of unchecked fields
		 * if this occurs make a copy of the set on write to reduce memory usage
		 * @return whether the object was removed.
		 */
		public boolean removeUncheckedField(String field) {
			if ((uncheckedFields != null) && uncheckedFields.contains(field)) {
				if (uncheckedFields.size() == 1) {
					uncheckedFields = null;
					fieldsAreSharedWithParent = false;
					return true;
				}

				if (fieldsAreSharedWithParent) {
					uncheckedFields = new HashSet<String>(uncheckedFields);
					fieldsAreSharedWithParent = false;
					uncheckedFields.remove(field);
					return true;
				} else {
					uncheckedFields.remove(field);
					return true;
				}
			}
			return false;
		}

		@Override
		public String toString() {
			return basicBlock + "|" + uncheckedFields;
		}
	}
}