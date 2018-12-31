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
package com.mebigfatguy.fbcontrib.detect;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.ConstantFieldref;
import org.apache.bcel.classfile.ConstantNameAndType;

import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XField;

/**
 * Looks for use of iterators on synchronized collections built from the Collections class. As the collection in question was built thru
 * Collections.synchronizedXXX, an assumption is made that this collection must be multithreaded safe. However, iterator access is used, which is explicitly
 * unsafe. When iterators are to be used, synchronization should be done manually.
 */
public class SyncCollectionIterators extends BytecodeScanningDetector {
    private static final Set<String> synchCollectionNames = UnmodifiableSet.create("synchronizedSet", "synchronizedMap", "synchronizedList",
            "synchronizedSortedSet", "synchronizedSortedMap");

    private static final Set<String> mapToSetMethods = UnmodifiableSet.create("keySet", "entrySet", "values");

    enum State {
        SEEN_NOTHING, SEEN_SYNC, SEEN_LOAD
    }

    private final BugReporter bugReporter;
    private State state;
    private Set<String> memberCollections;
    private BitSet localCollections;
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
            memberCollections = new HashSet<>();
            localCollections = new BitSet();
            monitorObjects = new ArrayList<>();
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
                    sawOpcodeAfterNothing(seen);
                break;

                case SEEN_SYNC:
                    sawOpcodeAfterSync(seen);
                break;

                case SEEN_LOAD:
                    sawOpcodeAfterLoad(seen);
                break;
            }

            if (seen == Const.MONITORENTER) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    int reg = item.getRegisterNumber();
                    if (reg >= 0) {
                        monitorObjects.add(Integer.valueOf(reg));
                    } else {
                        XField field = item.getXField();
                        if (field != null) {
                            monitorObjects.add(field.getName());
                        }
                    }
                }
            } else if ((seen == Const.MONITOREXIT) && !monitorObjects.isEmpty()) {
                monitorObjects.remove(monitorObjects.size() - 1);
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    private void sawOpcodeAfterNothing(int seen) {
        if ((seen == Const.INVOKESTATIC) && "java/util/Collections".equals(getClassConstantOperand())) {
            if (synchCollectionNames.contains(getNameConstantOperand())) {
                state = State.SEEN_SYNC;
            }
        } else if (OpcodeUtils.isALoad(seen)) {
            int reg = RegisterUtils.getALoadReg(this, seen);
            if (localCollections.get(reg)) {
                collectionInfo = Integer.valueOf(reg);
                state = State.SEEN_LOAD;
            }
        } else if (seen == Const.GETFIELD) {
            ConstantFieldref ref = (ConstantFieldref) getConstantRefOperand();
            ConstantNameAndType nandt = (ConstantNameAndType) getConstantPool().getConstant(ref.getNameAndTypeIndex());

            String fieldName = nandt.getName(getConstantPool());
            if (memberCollections.contains(fieldName)) {
                collectionInfo = fieldName;
                state = State.SEEN_LOAD;
            }
        }
    }

    private void sawOpcodeAfterSync(int seen) {
        if (OpcodeUtils.isAStore(seen)) {
            localCollections.set(RegisterUtils.getAStoreReg(this, seen));
        } else if (seen == Const.PUTFIELD) {
            ConstantFieldref ref = (ConstantFieldref) getConstantRefOperand();
            ConstantNameAndType nandt = (ConstantNameAndType) getConstantPool().getConstant(ref.getNameAndTypeIndex());
            memberCollections.add(nandt.getName(getConstantPool()));
        }
        state = State.SEEN_NOTHING;
    }

    private void sawOpcodeAfterLoad(int seen) {
        if (seen != Const.INVOKEINTERFACE) {
            state = State.SEEN_NOTHING;
            return;
        }
        String calledClass = getClassConstantOperand();
        if ((Values.SLASHED_JAVA_UTIL_MAP.equals(calledClass) || ("java/util/SortedMap".equals(calledClass)))) {
            if (mapToSetMethods.contains(getNameConstantOperand())) {
                state = State.SEEN_LOAD;
            } else {
                state = State.SEEN_NOTHING;
            }
        } else if (calledClass.startsWith("java/util/")) {
            if ("iterator".equals(getNameConstantOperand())) {
                state = State.SEEN_NOTHING;
                if (monitorObjects.isEmpty() || !syncIsMap(monitorObjects.get(monitorObjects.size() - 1), collectionInfo)) {
                    bugReporter.reportBug(
                            new BugInstance(this, "SCI_SYNCHRONIZED_COLLECTION_ITERATORS", NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
                }
            }
            /* don't change state at this point */
        } else {
            state = State.SEEN_NOTHING;
        }
    }

    private static boolean syncIsMap(Object syncObject, Object colInfo) {
        if ((syncObject != null) && (colInfo != null) && syncObject.getClass().equals(colInfo.getClass())) {
            return syncObject.equals(colInfo);
        }

        // Something went wrong... don't report
        return true;
    }
}
