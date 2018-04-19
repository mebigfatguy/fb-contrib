/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2018 Bhaskar Maddala
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

import org.apache.bcel.Const;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.LocalVariableTable;

import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;

/**
 * Find usage of HashCodeBuilder from Apache commons, where the code invokes hashCode() on the constructed object rather than toHashCode()
 *
 * <pre>
 * new HashCodeBuilder().append(this.name).hashCode();
 * </pre>
 */
public class CommonsHashcodeBuilderToHashcode extends BytecodeScanningDetector {

    private static final String LANG_HASH_CODE_BUILDER = "Lorg/apache/commons/lang/builder/HashCodeBuilder;";
    private static final String LANG3_HASH_CODE_BUILDER = "Lorg/apache/commons/lang3/builder/HashCodeBuilder;";
    private final OpcodeStack stack;
    private final BugReporter bugReporter;

    /**
     * constructs a CHTH detector given the reporter to report bugs on.
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public CommonsHashcodeBuilderToHashcode(final BugReporter bugReporter) {
        stack = new OpcodeStack();
        this.bugReporter = bugReporter;
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
        stack.resetForMethodEntry(this);
        LocalVariableTable lvt = getMethod().getLocalVariableTable();
        if (lvt != null) {
            super.visitCode(obj);
        }
    }

    @Override
    public void sawOpcode(int seen) {
        if (seen == Const.INVOKEVIRTUAL) {
            String methodName = getNameConstantOperand();
            if (Values.HASHCODE.equals(methodName) && SignatureBuilder.SIG_VOID_TO_INT.equals(getSigConstantOperand()) && (stack.getStackDepth() > 0)) {
                String calledClass = stack.getStackItem(0).getSignature();
                if (LANG3_HASH_CODE_BUILDER.equals(calledClass) || LANG_HASH_CODE_BUILDER.equals(calledClass)) {
                    bugReporter.reportBug(new BugInstance(this, "CHTH_COMMONS_HASHCODE_BUILDER_TOHASHCODE", HIGH_PRIORITY).addClass(this).addMethod(this)
                            .addSourceLine(this));
                }
            }
        }
        super.sawOpcode(seen);
        stack.sawOpcode(this, seen);
    }
}
