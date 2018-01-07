/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2018 Dave Brosius
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

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.bcel.Const;
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
import org.apache.bcel.generic.INVOKEVIRTUAL;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.ReferenceType;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.Values;

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
 * finds fields that are used in a locals only fashion, specifically private fields that are accessed first in each method with a store vs. a load.
 */
public class FieldCouldBeLocal extends BytecodeScanningDetector {
    private final BugReporter bugReporter;
    private ClassContext clsContext;
    private Map<String, FieldInfo> localizableFields;
    private CFG cfg;
    private ConstantPoolGen cpg;
    private BitSet visitedBlocks;
    private Map<String, Set<String>> methodFieldModifiers;
    private String clsName;
    private String clsSig;

    /**
     * constructs a FCBL detector given the reporter to report bugs on.
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public FieldCouldBeLocal(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * overrides the visitor to collect localizable fields, and then report those that survive all method checks.
     *
     * @param classContext
     *            the context object that holds the JavaClass parsed
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            localizableFields = new HashMap<>();
            visitedBlocks = new BitSet();
            clsContext = classContext;
            clsName = clsContext.getJavaClass().getClassName();
            clsSig = SignatureUtils.classToSignature(clsName);
            JavaClass cls = classContext.getJavaClass();
            Field[] fields = cls.getFields();
            ConstantPool cp = classContext.getConstantPoolGen().getConstantPool();

            for (Field f : fields) {
                if (!f.isStatic() && !f.isVolatile() && (f.getName().indexOf(Values.SYNTHETIC_MEMBER_CHAR) < 0) && f.isPrivate()) {
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

            if (!localizableFields.isEmpty()) {
                buildMethodFieldModifiers(classContext);
                super.visitClassContext(classContext);
                for (FieldInfo fi : localizableFields.values()) {
                    FieldAnnotation fa = fi.getFieldAnnotation();
                    SourceLineAnnotation sla = fi.getSrcLineAnnotation();
                    BugInstance bug = new BugInstance(this, BugType.FCBL_FIELD_COULD_BE_LOCAL.name(), NORMAL_PRIORITY).addClass(this).addField(fa);
                    if (sla != null) {
                        bug.addSourceLine(sla);
                    }
                    bugReporter.reportBug(bug);
                }
            }
        } finally {
            localizableFields = null;
            visitedBlocks = null;
            clsContext = null;
            methodFieldModifiers = null;
        }
    }

    /**
     * overrides the visitor to navigate basic blocks looking for all first usages of fields, removing those that are read from first.
     *
     * @param obj
     *            the context object of the currently parsed method
     */
    @Override
    public void visitMethod(Method obj) {
        if (localizableFields.isEmpty()) {
            return;
        }

        try {

            cfg = clsContext.getCFG(obj);
            cpg = cfg.getMethodGen().getConstantPool();
            BasicBlock bb = cfg.getEntry();
            Set<String> uncheckedFields = new HashSet<>(localizableFields.keySet());
            visitedBlocks.clear();
            checkBlock(bb, uncheckedFields);
        } catch (CFGBuilderException cbe) {
            localizableFields.clear();
        } finally {
            cfg = null;
            cpg = null;
        }
    }

    /**
     * looks for methods that contain a GETFIELD or PUTFIELD opcodes
     *
     * @param method
     *            the context object of the current method
     * @return if the class uses GETFIELD or PUTFIELD
     */
    private boolean prescreen(Method method) {
        BitSet bytecodeSet = getClassContext().getBytecodeSet(method);
        return (bytecodeSet != null) && (bytecodeSet.get(Const.PUTFIELD) || bytecodeSet.get(Const.GETFIELD));
    }

    /**
     * implements the visitor to pass through constructors and static initializers to the byte code scanning code. These methods are not reported, but are used
     * to build SourceLineAnnotations for fields, if accessed.
     *
     * @param obj
     *            the context object of the currently parsed code attribute
     */
    @Override
    public void visitCode(Code obj) {
        Method m = getMethod();
        if (prescreen(m)) {
            String methodName = m.getName();
            if (Values.STATIC_INITIALIZER.equals(methodName) || Values.CONSTRUCTOR.equals(methodName)) {
                super.visitCode(obj);
            }
        }
    }

    /**
     * implements the visitor to add SourceLineAnnotations for fields in constructors and static initializers.
     *
     * @param seen
     *            the opcode of the currently visited instruction
     */
    @Override
    public void sawOpcode(int seen) {
        if ((seen == Const.GETFIELD) || (seen == Const.PUTFIELD)) {
            String fieldName = getNameConstantOperand();
            FieldInfo fi = localizableFields.get(fieldName);
            if (fi != null) {
                SourceLineAnnotation sla = SourceLineAnnotation.fromVisitedInstruction(this);
                fi.setSrcLineAnnotation(sla);
            }
        }
    }

    /**
     * looks in this basic block for the first access to the fields in uncheckedFields. Once found the item is removed from uncheckedFields, and removed from
     * localizableFields if the access is a GETFIELD. If any unchecked fields remain, this method is recursively called on all outgoing edges of this basic
     * block.
     *
     * @param startBB
     *            this basic block
     * @param uncheckedFields
     *            the list of fields to look for
     */
    private void checkBlock(BasicBlock startBB, Set<String> uncheckedFields) {
        Deque<BlockState> toBeProcessed = new ArrayDeque<>();
        toBeProcessed.addLast(new BlockState(startBB, uncheckedFields));
        visitedBlocks.set(startBB.getLabel());

        while (!toBeProcessed.isEmpty()) {
            if (localizableFields.isEmpty()) {
                return;
            }
            BlockState bState = toBeProcessed.removeFirst();
            BasicBlock bb = bState.getBasicBlock();

            InstructionIterator ii = bb.instructionIterator();
            while ((bState.getUncheckedFieldSize() > 0) && ii.hasNext()) {
                InstructionHandle ih = ii.next();
                Instruction ins = ih.getInstruction();
                if (ins instanceof FieldInstruction) {
                    FieldInstruction fi = (FieldInstruction) ins;
                    if (fi.getReferenceType(cpg).getSignature().equals(clsSig)) {
                        String fieldName = fi.getFieldName(cpg);
                        FieldInfo finfo = localizableFields.get(fieldName);

                        if ((finfo != null) && localizableFields.get(fieldName).hasAnnotation()) {
                            localizableFields.remove(fieldName);
                        } else {
                            boolean justRemoved = bState.removeUncheckedField(fieldName);

                            if (ins instanceof GETFIELD) {
                                if (justRemoved) {
                                    localizableFields.remove(fieldName);
                                    if (localizableFields.isEmpty()) {
                                        return;
                                    }
                                }
                            } else if (finfo != null) {
                                finfo.setSrcLineAnnotation(SourceLineAnnotation.fromVisitedInstruction(clsContext, this, ih.getPosition()));
                            }
                        }
                    }
                } else if (ins instanceof INVOKESPECIAL) {
                    INVOKESPECIAL is = (INVOKESPECIAL) ins;

                    ReferenceType rt = is.getReferenceType(cpg);
                    if (Values.CONSTRUCTOR.equals(is.getMethodName(cpg))) {
                        if ((rt instanceof ObjectType)
                                && ((ObjectType) rt).getClassName().startsWith(clsContext.getJavaClass().getClassName() + Values.INNER_CLASS_SEPARATOR)) {
                            localizableFields.clear();
                        }
                    } else {
                        localizableFields.clear();
                    }
                } else if (ins instanceof INVOKEVIRTUAL) {
                    INVOKEVIRTUAL is = (INVOKEVIRTUAL) ins;

                    ReferenceType rt = is.getReferenceType(cpg);
                    if ((rt instanceof ObjectType) && ((ObjectType) rt).getClassName().equals(clsName)) {
                        String methodDesc = is.getName(cpg) + is.getSignature(cpg);
                        Set<String> fields = methodFieldModifiers.get(methodDesc);
                        if (fields != null) {
                            localizableFields.keySet().removeAll(fields);
                        }
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
     * builds up the method to field map of what method write to which fields this is one recursively so that if method A calls method B, and method B writes to
     * field C, then A modifies F.
     *
     * @param classContext
     *            the context object of the currently parsed class
     */
    private void buildMethodFieldModifiers(ClassContext classContext) {
        FieldModifier fm = new FieldModifier();
        fm.visitClassContext(classContext);
        methodFieldModifiers = fm.getMethodFieldModifiers();
    }

    /**
     * holds information about a field and it's first usage
     */
    private static class FieldInfo {
        private final FieldAnnotation fieldAnnotation;
        private SourceLineAnnotation srcLineAnnotation;
        private final boolean hasAnnotation;

        /**
         * creates a FieldInfo from an annotation, and assumes no source line information
         *
         * @param fa
         *            the field annotation for this field
         * @param hasExternalAnnotation
         *            the field has a non java based annotation
         */
        FieldInfo(final FieldAnnotation fa, boolean hasExternalAnnotation) {
            fieldAnnotation = fa;
            srcLineAnnotation = null;
            hasAnnotation = hasExternalAnnotation;
        }

        /**
         * set the source line annotation of first use for this field
         *
         * @param sla
         *            the source line annotation
         */
        void setSrcLineAnnotation(final SourceLineAnnotation sla) {
            if (srcLineAnnotation == null) {
                srcLineAnnotation = sla;
            }
        }

        /**
         * get the field annotation for this field
         *
         * @return the field annotation
         */
        FieldAnnotation getFieldAnnotation() {
            return fieldAnnotation;
        }

        /**
         * get the source line annotation for the first use of this field
         *
         * @return the source line annotation
         */
        SourceLineAnnotation getSrcLineAnnotation() {
            return srcLineAnnotation;
        }

        /**
         * gets whether the field has a non java annotation
         *
         * @return if the field has a non java annotation
         */
        boolean hasAnnotation() {
            return hasAnnotation;
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }

    /**
     * holds the parse state of the current basic block, and what fields are left to be checked the fields that are left to be checked are a reference from the
     * parent block and a new collection is created on first write to the set to reduce memory concerns.
     */
    private static class BlockState {
        private final BasicBlock basicBlock;
        private Set<String> uncheckedFields;
        private boolean fieldsAreSharedWithParent;

        /**
         * creates a BlockState consisting of the next basic block to parse, and what fields are to be checked
         *
         * @param bb
         *            the basic block to parse
         * @param fields
         *            the fields to look for first use
         */
        public BlockState(final BasicBlock bb, final Set<String> fields) {
            basicBlock = bb;
            uncheckedFields = fields;
            fieldsAreSharedWithParent = true;
        }

        /**
         * creates a BlockState consisting of the next basic block to parse, and what fields are to be checked
         *
         * @param bb
         *            the basic block to parse
         * @param parentBlockState
         *            the basic block to copy from
         */
        public BlockState(final BasicBlock bb, BlockState parentBlockState) {
            basicBlock = bb;
            uncheckedFields = parentBlockState.uncheckedFields;
            fieldsAreSharedWithParent = true;
        }

        /**
         * get the basic block to parse
         *
         * @return the basic block
         */
        public BasicBlock getBasicBlock() {
            return basicBlock;
        }

        /**
         * returns the number of unchecked fields
         *
         * @return the number of unchecked fields
         */
        public int getUncheckedFieldSize() {
            return (uncheckedFields == null) ? 0 : uncheckedFields.size();
        }

        /**
         * return the field from the set of unchecked fields if this occurs make a copy of the set on write to reduce memory usage
         *
         * @param field
         *            the field to be removed
         *
         * @return whether the object was removed.
         */
        public boolean removeUncheckedField(String field) {
            if ((uncheckedFields == null) || !uncheckedFields.contains(field)) {
                return false;
            }

            if (uncheckedFields.size() == 1) {
                uncheckedFields = null;
                fieldsAreSharedWithParent = false;
                return true;
            }

            if (fieldsAreSharedWithParent) {
                uncheckedFields = new HashSet<>(uncheckedFields);
                fieldsAreSharedWithParent = false;
                uncheckedFields.remove(field);
            } else {
                uncheckedFields.remove(field);
            }

            return true;
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }

    /**
     * a visitor that determines what methods write to what fields
     */
    private static class FieldModifier extends BytecodeScanningDetector {

        private final Map<String, Set<String>> methodCallChain = new HashMap<>();
        private final Map<String, Set<String>> mfModifiers = new HashMap<>();
        private String clsName;

        public Map<String, Set<String>> getMethodFieldModifiers() {
            Map<String, Set<String>> modifiers = new HashMap<>(mfModifiers.size());
            modifiers.putAll(mfModifiers);
            for (Entry<String, Set<String>> method : modifiers.entrySet()) {
                modifiers.put(method.getKey(), new HashSet<>(method.getValue()));
            }

            boolean modified = true;
            while (modified) {
                modified = false;
                for (Map.Entry<String, Set<String>> entry : methodCallChain.entrySet()) {
                    String methodDesc = entry.getKey();
                    Set<String> calledMethods = entry.getValue();

                    for (String calledMethodDesc : calledMethods) {
                        Set<String> fields = mfModifiers.get(calledMethodDesc);
                        if (fields != null) {
                            Set<String> flds = modifiers.get(methodDesc);
                            if (flds == null) {
                                flds = new HashSet<>();
                                modifiers.put(methodDesc, flds);
                            }
                            if (flds.addAll(fields)) {
                                modified = true;
                            }
                        }
                    }
                }
            }

            return modifiers;
        }

        @Override
        public void visitClassContext(ClassContext context) {
            clsName = context.getJavaClass().getClassName();
            super.visitClassContext(context);
        }

        @Override
        public void sawOpcode(int seen) {
            if (seen == Const.PUTFIELD) {
                if (clsName.equals(getClassConstantOperand())) {
                    String methodDesc = getMethodName() + getMethodSig();
                    Set<String> fields = mfModifiers.get(methodDesc);
                    if (fields == null) {
                        fields = new HashSet<>();
                        mfModifiers.put(methodDesc, fields);
                    }
                    fields.add(getNameConstantOperand());
                }
            } else if ((seen == Const.INVOKEVIRTUAL) && clsName.equals(getClassConstantOperand())) {
                String methodDesc = getMethodName() + getMethodSig();
                Set<String> methods = methodCallChain.get(methodDesc);
                if (methods == null) {
                    methods = new HashSet<>();
                    methodCallChain.put(methodDesc, methods);
                }
                methods.add(getNameConstantOperand() + getSigConstantOperand());
            }
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }
}
