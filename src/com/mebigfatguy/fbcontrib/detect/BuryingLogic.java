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

import java.util.ArrayDeque;
import java.util.Deque;

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

public class BuryingLogic extends BytecodeScanningDetector {

    private BugReporter bugReporter;
    private OpcodeStack stack;
    private Deque<Integer> ifLocations;
    private Deque<Integer> elseLocations;

    public BuryingLogic(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            stack = new OpcodeStack();
            ifLocations = new ArrayDeque<>();
            elseLocations = new ArrayDeque<>();
            super.visitClassContext(classContext);
        } finally {
            stack = null;
            ifLocations = null;
            elseLocations = null;
        }
    }

    @Override
    public void visitCode(Code obj) {
        Method m = getMethod();
        if (m.getReturnType() == Type.VOID) {
            return;
        }

        stack.resetForMethodEntry(this);
        ifLocations.clear();
        elseLocations.clear();
        super.visitCode(obj);
    }

    @Override
    public void sawOpcode(int seen) {

        if (!elseLocations.isEmpty() && (getPC() >= elseLocations.getFirst())) {
            ifLocations.removeFirst();
            elseLocations.removeFirst();
        }

        if (isBranch(seen)) {
            if (getBranchOffset() > 0) {
                ifLocations.addLast(getNextPC());
                elseLocations.addLast(getBranchTarget());
            }
        }
    }
}
