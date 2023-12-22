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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.LocalVariable;
import org.apache.bcel.classfile.LocalVariableTable;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.RegisterUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.SourceLineAnnotation;
import edu.umd.cs.findbugs.ba.AnalysisContext;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.SourceFile;
import edu.umd.cs.findbugs.ba.SourceFinder;

/**
 * looks for methods that correctly do not write to a parameter. To help
 * document this, and to perhaps help the jvm optimize the invocation of this
 * method, you should consider defining these parameters as final.
 */
public class FinalParameters extends BytecodeScanningDetector {
    private final BugReporter bugReporter;
    private BitSet changedParms;
    private String methodName;
    private int firstLocalReg;
    private boolean isAbstract;
    private boolean srcInited;
    private SourceLineAnnotation srcLineAnnotation;
    private String[] sourceLines;

    /**
     * constructs a FP detector given the reporter to report bugs on
     *
     * @param bugReporter the sync of bug reports
     */
    public FinalParameters(final BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * overrides the visitor to initialize the 'has source' flag
     *
     * @param classContext the context object for the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        srcInited = false;
        super.visitClassContext(classContext);
    }

    /**
     * overrides the visitor capture source lines for the method
     *
     * @param obj the method object for the currently parsed method
     */
    @Override
    public void visitMethod(final Method obj) {
        methodName = obj.getName();
        if (Values.STATIC_INITIALIZER.equals(methodName) || Values.CONSTRUCTOR.equals(methodName)) {
            return;
        }

        List<String> parms = SignatureUtils.getParameterSignatures(obj.getSignature());

        if (!parms.isEmpty()) {
            boolean isStatic = obj.isStatic();
            isAbstract = obj.isAbstract();

            firstLocalReg = isStatic ? 0 : 1;
            for (String parmSig : parms) {
                firstLocalReg += SignatureUtils.getSignatureSize(parmSig);
            }

            sourceLines = getSourceLines(obj);
        }
    }

    /**
     * reads the sourcefile based on the source line annotation for the method
     *
     * @param obj the method object for the currently parsed method
     *
     * @return an array of source lines for the method
     */
    private String[] getSourceLines(Method obj) {

        if (srcInited) {
            return sourceLines;
        }

        try {
            srcLineAnnotation = SourceLineAnnotation.forEntireMethod(getClassContext().getJavaClass(), obj);
            if (srcLineAnnotation != null) {
                try (SourceFinder sourceFinder = AnalysisContext.currentAnalysisContext().getSourceFinder()) {
                    SourceFile sourceFile = sourceFinder.findSourceFile(srcLineAnnotation.getPackageName(),
                            srcLineAnnotation.getSourceFile());
                    try (BufferedReader sourceReader = new BufferedReader(
                            new InputStreamReader(sourceFile.getInputStream(), StandardCharsets.UTF_8))) {

                        List<String> lines = new ArrayList<>(100);
                        String line;
                        while ((line = sourceReader.readLine()) != null) {
                            lines.add(line);
                        }
                        sourceLines = lines.toArray(new String[0]);
                    }
                }
            }
        } catch (IOException ioe) {
            // noop
        }

        srcInited = true;
        return sourceLines;
    }

    /**
     * overrides the visitor to find the source lines for the method header, to find
     * non final parameters
     *
     * @param obj the code object for the currently parsed method
     */
    @Override
    public void visitCode(final Code obj) {
        if (sourceLines == null) {
            return;
        }

        if (isAbstract) {
            return;
        }

        if (Values.STATIC_INITIALIZER.equals(methodName) || Values.CONSTRUCTOR.equals(methodName)) {
            return;
        }

        int methodStart = srcLineAnnotation.getStartLine() - 2;
        int methodLine = methodStart;
        String line;
        while ((methodLine >= 0) && (methodLine < sourceLines.length)) {
            line = sourceLines[methodLine];
            if (line.indexOf(methodName) >= 0) {
                break;
            }
            methodLine--;
        }

        if (methodLine < 0) {
            return;
        }

        for (int i = methodLine; i <= methodStart; i++) {
            if ((i < 0) || (i >= sourceLines.length)) {
                return;
            }
            line = sourceLines[i];
            if (line.indexOf("final") >= 0) {
                return;
            }
        }

        changedParms = new BitSet();
        super.visitCode(obj);

        BugInstance bi = null;
        for (int i = 0; i < firstLocalReg; i++) {
            if (changedParms.get(i)) {
                changedParms.clear(i);
                continue;
            }

            String parmName = getRegisterName(obj, i);
            if (bi == null) {
                bi = new BugInstance(this, BugType.FP_FINAL_PARAMETERS.name(), LOW_PRIORITY).addClass(this)
                        .addMethod(this).addSourceLine(this, 0);
                bugReporter.reportBug(bi);
            }
            bi.addString(parmName);
        }
        changedParms = null;
    }

    /**
     * overrides the visitor to find local variable reference stores to store them
     * as changed
     *
     * @param seen the currently parsed opcode
     */
    @Override
    public void sawOpcode(final int seen) {
        if (OpcodeUtils.isAStore(seen)) {
            changedParms.set(RegisterUtils.getAStoreReg(this, seen));
        }
    }

    /**
     * returns the variable name of the specified register slot
     *
     * @param obj the currently parsed code object
     * @param reg the variable register of interest
     *
     * @return the variable name of the specified register
     */
    private static String getRegisterName(final Code obj, final int reg) {
        LocalVariableTable lvt = obj.getLocalVariableTable();
        if (lvt != null) {
            LocalVariable lv = lvt.getLocalVariable(reg, 0);
            if (lv != null) {
                return lv.getName();
            }
        }
        return String.valueOf(reg);
    }
}
