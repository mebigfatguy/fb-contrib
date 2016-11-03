/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2016 Dave Brosius
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
import java.util.Map;
import java.util.Set;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.StopOpcodeParsingException;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.FieldAnnotation;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XField;

/**
 * looks for fields that are implementations of java.util.Map, but that are only ever iterated over. This probably means that this data structure should be a
 * List of some class that holds two values, or at the least Pair. Map was probably choosen as it was the easiest thing to use, but belies the point of the data
 * structure.
 */
public class DubiousMapCollection extends BytecodeScanningDetector {

    private static final Set<String> SPECIAL_METHODS = UnmodifiableSet.create(Values.CONSTRUCTOR, Values.STATIC_INITIALIZER);
    private static final Set<String> MAP_METHODS = UnmodifiableSet.create(
    // @formatter:off
        "computeIfAbsent",
        "containsKey",
        "get",
        "getOrDefault",
        "remove",
        "removeEldestEntry",
        "values"
    // @formatter:on
    );

    private static final Set<String> MODIFYING_METHODS = UnmodifiableSet.create(
    // @formatter:off
        "clear",
        "put",
        "putAll",
        "remove"
    // @formatter:on
    );

    private BugReporter bugReporter;
    private JavaClass mapInterface;
    private OpcodeStack stack;
    private Map<String, FieldAnnotation> mapFields;
    boolean isInSpecial;

    public DubiousMapCollection(BugReporter bugReporter) {
        this.bugReporter = bugReporter;

        try {
            mapInterface = Repository.lookupClass("java.util.Map");
        } catch (ClassNotFoundException e) {
            bugReporter.reportMissingClass(e);
        }
    }

    @Override
    public void visitClassContext(ClassContext classContext) {

        if (mapInterface == null) {
            return;
        }

        try {
            stack = new OpcodeStack();
            mapFields = new HashMap<>();
            super.visitClassContext(classContext);

            for (FieldAnnotation mapField : mapFields.values()) {
                bugReporter.reportBug(new BugInstance(this, BugType.DMC_DUBIOUS_MAP_COLLECTION.toString(), NORMAL_PRIORITY).addClass(this).addField(mapField));
            }
        } finally {
            mapFields = null;
            stack = null;
        }
    }

    @Override
    public void visitField(Field obj) {
        if (obj.isPrivate() && isMap(obj)) {
            mapFields.put(obj.getName(), new FieldAnnotation(getDottedClassName(), obj.getName(), obj.getSignature(), obj.isStatic()));
        }
    }

    @Override
    public void visitCode(Code obj) {
        isInSpecial = SPECIAL_METHODS.contains(getMethod().getName());
        stack.resetForMethodEntry(this);
        try {
            super.visitCode(obj);
        } catch (StopOpcodeParsingException e) {
            // no more unaccounted for map fields
        }
    }

    @Override
    public void sawOpcode(int seen) {
        try {

            if ((seen == INVOKEINTERFACE) || (seen == INVOKEVIRTUAL)) {
                processNormalInvoke();
                processMethodCall();
            } else if ((seen == INVOKESPECIAL) || (seen == INVOKESTATIC) || (seen == INVOKEDYNAMIC)) {
                processMethodCall();
            } else if ((seen == ARETURN) || (OpcodeUtils.isAStore(seen))) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    XField xf = item.getXField();
                    if (xf != null) {
                        mapFields.remove(xf.getName());
                        if (mapFields.isEmpty()) {
                            throw new StopOpcodeParsingException();
                        }
                    }
                }
            } else if ((seen == PUTFIELD) || (seen == PUTSTATIC)) {
                XField xf = getXFieldOperand();
                if (xf != null) {
                    if (!isInSpecial) {
                        mapFields.remove(xf.getName());

                    } else {
                        if (stack.getStackDepth() > 0) {
                            OpcodeStack.Item item = stack.getStackItem(0);
                            if ((item.getRegisterNumber() >= 0) || (item.getXField() != null)) {
                                mapFields.remove(xf.getName());
                            }
                        }
                    }
                    if (mapFields.isEmpty()) {
                        throw new StopOpcodeParsingException();
                    }

                }
            }

        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    private void processNormalInvoke() {
        String signature = getSigConstantOperand();
        int numParms = SignatureUtils.getNumParameters(signature);
        if (stack.getStackDepth() <= numParms) {
            return;
        }

        OpcodeStack.Item item = stack.getStackItem(numParms);
        XField xf = item.getXField();
        if (xf == null) {
            return;
        }

        String fName = xf.getName();
        if (!mapFields.containsKey(fName)) {
            return;
        }

        String mName = getNameConstantOperand();
        if (MAP_METHODS.contains(mName)) {
            mapFields.remove(fName);
            if (mapFields.isEmpty()) {
                throw new StopOpcodeParsingException();
            }

            return;
        }

        if (isInSpecial) {
            // TODO: Really we have to make sure that items are added to the map as 'constants'
            return;
        }

        if (MODIFYING_METHODS.contains(mName)) {
            mapFields.remove(fName);
            if (mapFields.isEmpty()) {
                throw new StopOpcodeParsingException();
            }

            return;
        }
    }

    /**
     * parses all the parameters of a called method and removes any of the parameters that are maps currently being looked at for this detector
     */
    private void processMethodCall() {
        int numParams = SignatureUtils.getNumParameters(getSigConstantOperand());

        int depth = stack.getStackDepth();
        for (int i = 0; i < numParams; i++) {
            if (depth > i) {
                OpcodeStack.Item item = stack.getStackItem(i);
                XField xf = item.getXField();
                if (xf != null) {
                    mapFields.remove(xf.getName());
                    if (mapFields.isEmpty()) {
                        throw new StopOpcodeParsingException();
                    }

                }
            } else {
                return;
            }
        }
    }

    private boolean isMap(Field obj) {
        try {
            String sig = obj.getSignature();
            if (!sig.startsWith(Values.SIG_QUALIFIED_CLASS_PREFIX)) {
                return false;
            }

            sig = sig.substring(1, sig.length() - 1);
            JavaClass fieldClass = Repository.lookupClass(sig);
            return fieldClass.implementationOf(mapInterface);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
