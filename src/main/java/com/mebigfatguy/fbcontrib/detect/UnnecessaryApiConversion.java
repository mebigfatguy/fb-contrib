/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2017 Dave Brosius
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

import org.apache.bcel.classfile.Code;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.FQMethod;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.XMethod;

/**
 * looks for code that appears to be using two forms of similar apis, an older one, and a new one. It finds code that creates newer api objects by first
 * instantiating older api objects, and converting them into the new form. It is simpler just to create the new object directly.
 */
public class UnnecessaryApiConversion extends BytecodeScanningDetector {

    private static Map<FQMethod, LegacyInfo> conversions = new HashMap<>();

    static {
        conversions.put(new FQMethod("java/util/Date", "toInstant", "()Ljava/time/Instant;"),
                new LegacyInfo("<init>", BugType.UAC_UNNECESSARY_API_CONVERSION_DATE_TO_INSTANT));
        conversions.put(new FQMethod("java/io/File", "toPath", "()Ljava/nio/file/Path;"),
                new LegacyInfo("<init>", BugType.UAC_UNNECESSARY_API_CONVERSION_FILE_TO_PATH));

    }

    private BugReporter bugReporter;
    private OpcodeStack stack;

    public UnnecessaryApiConversion(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
        }
    }

    @Override
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        super.visitCode(obj);
    }

    @Override
    public void sawOpcode(int seen) {

        try {
            switch (seen) {
                case INVOKEVIRTUAL:
                    FQMethod conversionMethod = new FQMethod(getClassConstantOperand(), getNameConstantOperand(), getSigConstantOperand());
                    LegacyInfo legacyInfo = conversions.get(conversionMethod);
                    if ((legacyInfo != null) && (stack.getStackDepth() > 0)) {
                        OpcodeStack.Item itm = stack.getStackItem(0);
                        XMethod xm = itm.getReturnValueOf();
                        if ((xm != null) && (xm.getName().equals(legacyInfo.methodName)
                                && (xm.getClassName().equals(conversionMethod.getClassName().replace('/', '.'))))) {
                            bugReporter.reportBug(
                                    new BugInstance(this, legacyInfo.bugType.name(), NORMAL_PRIORITY).addClass(this).addMethod(this).addSourceLine(this));
                        }
                    }
                break;

                default:
                break;
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    static class LegacyInfo {
        String methodName;
        BugType bugType;

        public LegacyInfo(String methodName, BugType bugType) {
            this.methodName = methodName;
            this.bugType = bugType;
        }
    }
}
