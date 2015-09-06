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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.ToString;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.FieldAnnotation;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.SourceLineAnnotation;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XField;

/**
 * looks for fields that are implementations of java.util.List, but that are
 * used in a set-like fashion. Since lookup type operations are performed using
 * a linear search for Lists, the performance for large Lists will be poor.
 * Consideration should be made as to whether these fields should be sets. In
 * the case that order is important, consider using LinkedHashSet.
 */
public class DubiousListCollection extends BytecodeScanningDetector {
    private static Set<String> setMethods = new HashSet<String>(6);
    private static Set<String> listMethods = new HashSet<String>(8);

    static {
        setMethods.add("contains(Ljava/lang/Object;)Z");
        setMethods.add("containsAll(Ljava/util/Collection;)Z");
        setMethods.add("remove(Ljava/lang/Object;)Ljava/lang/Object;");
        setMethods.add("removeAll(Ljava/util/Collection;)Z");
        setMethods.add("retainAll(Ljava/util/Collection;)Z");

        listMethods.add("add(ILjava/lang/Object;)V");
        listMethods.add("addAll(ILjava/util/Collection;)Z");
        listMethods.add("lastIndexOf(Ljava/lang/Object;)I");
        listMethods.add("remove(I)Ljava/lang/Object;");
        listMethods.add("set(ILjava/lang/Object;)Ljava/lang/Object;");
        listMethods.add("subList(II)Ljava/util/List;");
        listMethods.add("listIterator()Ljava/util/ListIterator;");
        listMethods.add("listIterator(I)Ljava/util/ListIterator;");

        // Theoretically get(i) and indexOf(Object) are list Methods but are so
        // abused, as to be meaningless
    }

    private final BugReporter bugReporter;
    private final OpcodeStack stack = new OpcodeStack();
    private final Map<String, FieldInfo> fieldsReported = new HashMap<String, FieldInfo>(10);

    /**
     * constructs a DLC detector given the reporter to report bugs on
     * 
     * @param bugReporter
     *            the sync of bug reports
     */
    public DubiousListCollection(final BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * overrides the visitor to accept classes that define List based fields
     * 
     * @param classContext
     *            the context object for the currently parsed class
     */
    @Override
    public void visitClassContext(final ClassContext classContext) {
        JavaClass cls = classContext.getJavaClass();
        Field[] flds = cls.getFields();
        for (Field f : flds) {
            String sig = f.getSignature();
            if (sig.charAt(0) == 'L') {
                sig = sig.substring(1, sig.length() - 1);
                if (sig.startsWith("java/util/") && sig.endsWith("List")) {
                    fieldsReported.put(f.getName(), new FieldInfo());
                }
            }
        }

        if (fieldsReported.size() > 0) {
            super.visitClassContext(classContext);
            reportBugs();
        }
    }

    /**
     * overrides the visitor to reset the opcode stack object
     * 
     * @param obj
     *            the code object for the currently parse method
     */
    @Override
    public void visitCode(final Code obj) {
        stack.resetForMethodEntry(this);
        super.visitCode(obj);
    }

    /**
     * overrides the visitor to record all method calls on List fields. If a
     * method is not a set based method, remove it from further consideration
     * 
     * @param seen
     *            the current opcode parsed.
     */
    @Override
    public void sawOpcode(final int seen) {
        try {
            stack.precomputation(this);

            if (seen == INVOKEINTERFACE) {
                String className = this.getClassConstantOperand();

                if (className.startsWith("java/util/") && className.endsWith("List")) {
                    String signature = getSigConstantOperand();
                    XField field = getFieldFromStack(stack, signature);
                    if (field != null) {
                        String fieldName = field.getName();
                        FieldInfo fi = fieldsReported.get(fieldName);
                        if (fi != null) {
                            String methodName = getNameConstantOperand();
                            String methodInfo = methodName + signature;
                            if (listMethods.contains(methodInfo))
                                fieldsReported.remove(fieldName);
                            else if (setMethods.contains(methodInfo))
                                fi.addUse(getPC());
                        }
                    }
                }
            } else if (seen == INVOKEVIRTUAL) {
                String className = getClassConstantOperand();
                if (className.startsWith("java/util/") && className.endsWith("List")) {
                    XField field = getFieldFromStack(stack, getSigConstantOperand());
                    if (field != null) {
                        String fieldName = field.getName();
                        fieldsReported.remove(fieldName);
                    }
                }
            } else if (seen == ARETURN) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    XField field = item.getXField();
                    if (field != null) {
                        String fieldName = field.getName();
                        fieldsReported.remove(fieldName);
                    }
                }
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    /**
     * return the field object that the current method was called on, by finding
     * the reference down in the stack based on the number of parameters
     * 
     * @param stk
     *            the opcode stack where fields are stored
     * @param signature
     *            the signature of the called method
     * 
     * @return the field annotation for the field whose method was executed
     */
    private static XField getFieldFromStack(final OpcodeStack stk, final String signature) {
        int parmCount = Type.getArgumentTypes(signature).length;
        if (stk.getStackDepth() > parmCount) {
            OpcodeStack.Item itm = stk.getStackItem(parmCount);
            return itm.getXField();
        }
        return null;
    }

    /**
     * implements the detector, by reporting all remaining fields that only have
     * set based access
     */
    private void reportBugs() {
        int major = getClassContext().getJavaClass().getMajor();
        for (Map.Entry<String, FieldInfo> entry : fieldsReported.entrySet()) {
            String field = entry.getKey();
            FieldInfo fi = entry.getValue();
            int cnt = fi.getSetCount();
            if (cnt > 0) {
                FieldAnnotation fa = getFieldAnnotation(field);
                if (fa != null) {
                    // can't use LinkedHashSet in 1.3 so report at LOW
                    bugReporter
                            .reportBug(new BugInstance(this, BugType.DLC_DUBIOUS_LIST_COLLECTION.name(), (major >= MAJOR_1_4) ? NORMAL_PRIORITY : LOW_PRIORITY)
                                    .addClass(this).addField(fa).addSourceLine(fi.getSourceLineAnnotation()));
                }
            }
        }
    }

    /**
     * builds a field annotation by finding the field in the classes' field list
     * 
     * @param fieldName
     *            the field for which to built the field annotation
     * 
     * @return the field annotation of the specified field
     */
    private FieldAnnotation getFieldAnnotation(final String fieldName) {
        JavaClass cls = getClassContext().getJavaClass();
        Field[] fields = cls.getFields();
        for (Field f : fields) {
            if (f.getName().equals(fieldName))
                return new FieldAnnotation(cls.getClassName(), fieldName, f.getSignature(), (f.getAccessFlags() & Constants.ACC_STATIC) != 0);
        }
        return null; // shouldn't happen
    }

    /**
     * holds information about fields and keeps counts of set methods called on them
     */
    class FieldInfo {
        private int setCnt = 0;
        private SourceLineAnnotation slAnnotation = null;

        /**
         * increments the number of times this field has a set method called on it
         *
         * @param pc the current instruction offset
         */
        public void addUse(final int pc) {
            setCnt++;
            if (slAnnotation == null)
                slAnnotation = SourceLineAnnotation.fromVisitedInstruction(DubiousListCollection.this.getClassContext(), DubiousListCollection.this, pc);
        }

        public SourceLineAnnotation getSourceLineAnnotation() {
            return slAnnotation;
        }

        public int getSetCount() {
            return setCnt;
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }
}
