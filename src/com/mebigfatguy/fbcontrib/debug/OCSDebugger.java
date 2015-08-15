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
package com.mebigfatguy.fbcontrib.debug;

import java.io.IOException;
import java.io.PrintWriter;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Method;

import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

public class OCSDebugger extends BytecodeScanningDetector {

    private static final String OCS_OUTPUT_FILE = "fb-contrib.ocs.output";
    private static final String OCS_METHOD_DESC = "fb-contrib.ocs.method";

    private static final String OUTPUT_FILE_NAME = System.getProperty(OCS_OUTPUT_FILE);
    private static final String METHOD_DESC = System.getProperty(OCS_METHOD_DESC);

    private OpcodeStack stack = new OpcodeStack();
    private PrintWriter pw = null;

    public OCSDebugger(@SuppressWarnings("unused") BugReporter bugReporter) {
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        if ((OUTPUT_FILE_NAME != null) && (METHOD_DESC != null))
            super.visitClassContext(classContext);
    }

    @Override
    public void visitCode(Code obj) {
        Method m = getMethod();

        String curMethodDesc = getClassContext().getJavaClass().getClassName() + "." + m.getName() + m.getSignature();
        if (curMethodDesc.equals(METHOD_DESC)) {
            try {
                pw = new PrintWriter(OUTPUT_FILE_NAME, "UTF-8");
                stack.resetForMethodEntry(this);

                super.visitCode(obj);
            } catch (IOException e) {
            } finally {
                pw.close();
                pw = null;
            }
        }
    }

    @Override
    public void sawOpcode(int seen) {
        stack.precomputation(this);
        stack.sawOpcode(this, seen);
        pw.println(String.format("After executing: %-16s at PC: %-5d Stack Size: %-3d", Constants.OPCODE_NAMES[getOpcode()], getPC(), stack.getStackDepth()));
    }
}
