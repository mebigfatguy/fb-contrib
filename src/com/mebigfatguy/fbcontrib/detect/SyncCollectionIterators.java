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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.ConstantFieldref;
import org.apache.bcel.classfile.ConstantNameAndType;

import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XField;

/**
 * Looks for use of iterators on synchronized collections built from the
 * Collections class. As the collection in question was built thru
 * Collections.synchronizedXXX, an assumption is made that this collection must
 * be multithreaded safe. However, iterator access is used, which is explicitly
 * unsafe. When iterators are to be used, synchronization should be done
 * manually.
 */
public class SyncCollectionIterators extends BytecodeScanningDetector {
    private final BugReporter bugReporter;
    private static final Set<String> synchCollectionNames = UnmodifiableSet.create(
            "synchronizedSet",
            "synchronizedMap",
            "synchronizedList",
            "synchronizedSortedSet",
            "synchronizedSortedMap"
    );
    
    private static final Set<String> mapToSetMethods = UnmodifiableSet.create(
            "keySet",
            "entrySet",
            "values"
    );
    
    enum State {
        SEEN_NOTHING, SEEN_SYNC, SEEN_LOAD
    }

    private State state;
    private Set<String> memberCollections;
    private Set<Integer> localCollections;
    private List<Object> monitorObjects;
    private OpcodeStack stack;
    private Object collectionInfo = null;

    /**
     * constructs a SCI detector given the reporter to report bugs on
     * 
     * @param bugReporter
     *            the sync of bug reports
     */
    public SyncCollectionIterators(final BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(final ClassContext classContext) {
        try {
            memberCollections = new HashSet<String>();
            localCollections = new HashSet<Integer>();
            monitorObjects = new ArrayList<Object>();
            stack = new OpcodeStack();
            super.visitClassContext(classContext);
        } finally {
            memberCollections = null;
            localCollections = null;
            monitorObjects = null;
            stack = null;
        }
    }

    @Override
    public void visitCode(final Code obj) {
        if (obj.getCode() != null) {
            state = State.SEEN_NOTHING;
            localCollections.clear();
            monitorObjects.clear();
            stack.resetForMethodEntry(this);
            super.visitCode(obj);
        }
    }

    @Override
    public void sawOpcode(final int seen) {
        try {
            stack.precomputation(this);

            switch (state) {
            case SEEN_NOTHING:
                if ((seen == INVOKESTATIC) && "java/util/Collections".equals(getClassConstantOperand())) {
                    if (synchCollectionNames.contains(getNameConstantOperand())) {
                        state = State.SEEN_SYNC;
                    }
                } else if (seen == ALOAD) {
                    int reg = getRegisterOperand();
                    if (localCollections.contains(Integer.valueOf(reg))) {
                        collectionInfo = Integer.valueOf(reg);
                        state = State.SEEN_LOAD;
                    }
                } else if ((seen >= ALOAD_0) && (seen <= ALOAD_3)) {
                    int reg = seen - ALOAD_0;
                    if (localCollections.contains(Integer.valueOf(reg))) {
                        collectionInfo = Integer.valueOf(reg);
                        state = State.SEEN_LOAD;
                    }
                } else if (seen == GETFIELD) {
                    ConstantFieldref ref = (ConstantFieldref) getConstantRefOperand();
                    ConstantNameAndType nandt = (ConstantNameAndType) getConstantPool().getConstant(ref.getNameAndTypeIndex());

                    String fieldName = nandt.getName(getConstantPool());
                    if (memberCollections.contains(fieldName)) {
                        collectionInfo = fieldName;
                        state = State.SEEN_LOAD;
                    }
                }
                break;

            case SEEN_SYNC:
                if (seen == ASTORE) {
                    int reg = getRegisterOperand();
                    localCollections.add(Integer.valueOf(reg));
                } else if ((seen >= ASTORE_0) && (seen <= ASTORE_3)) {
                    int reg = seen - ASTORE_0;
                    localCollections.add(Integer.valueOf(reg));
                } else if (seen == PUTFIELD) {
                    ConstantFieldref ref = (ConstantFieldref) getConstantRefOperand();
                    ConstantNameAndType nandt = (ConstantNameAndType) getConstantPool().getConstant(ref.getNameAndTypeIndex());
                    memberCollections.add(nandt.getName(getConstantPool()));
                }
                state = State.SEEN_NOTHING;
                break;

            case SEEN_LOAD:
                if (seen == INVOKEINTERFACE) {
                    String calledClass = getClassConstantOperand();
                    if ("java/lang/Map".equals(calledClass)) {
                        if (mapToSetMethods.contains(getNameConstantOperand())) {
                            state = State.SEEN_LOAD;
                        } else {
                            state = State.SEEN_NOTHING;
                        }
                    } else if (calledClass.startsWith("java/util/")) {
                        if ("iterator".equals(getNameConstantOperand())) {
                            if (monitorObjects.isEmpty()) {
                                bugReporter.reportBug(new BugInstance(this, "SCI_SYNCHRONIZED_COLLECTION_ITERATORS", NORMAL_PRIORITY).addClass(this)
                                        .addMethod(this).addSourceLine(this));
                                state = State.SEEN_NOTHING;
                            } else {
                                Object syncObj = monitorObjects.get(monitorObjects.size() - 1);

                                if (!syncIsMap(syncObj, collectionInfo)) {
                                    bugReporter.reportBug(new BugInstance(this, "SCI_SYNCHRONIZED_COLLECTION_ITERATORS", NORMAL_PRIORITY).addClass(this)
                                            .addMethod(this).addSourceLine(this));
                                }
                                state = State.SEEN_NOTHING;
                            }
                        }
                    } else {
                        state = State.SEEN_NOTHING;
                    }
                } else {
                    state = State.SEEN_NOTHING;
                }
                break;
            }

            if (seen == MONITORENTER) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    int reg = item.getRegisterNumber();
                    if (reg >= 0)
                        monitorObjects.add(Integer.valueOf(reg));
                    else {
                        XField field = item.getXField();
                        if (field != null)
                            monitorObjects.add(field.getName());
                    }
                }
            } else if (seen == MONITOREXIT) {
                if (monitorObjects.size() > 0)
                    monitorObjects.remove(monitorObjects.size() - 1);
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    private static boolean syncIsMap(Object syncObject, Object colInfo) {
        if ((syncObject != null) && (colInfo != null) && syncObject.getClass().equals(colInfo.getClass()))
            return syncObject.equals(colInfo);

        // Something went wrong... don't report
        return true;
    }
}
