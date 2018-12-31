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
import java.util.List;
import java.util.Set;

import org.apache.bcel.classfile.Code;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for the execution of sql queries inside a loop. This pattern tends to
 * be inefficient, and often can be improved upon, by collecting all the keys
 * needed for the query and issuing just one query using an in clause with all
 * the keys for all the queries previously needed in the loop.
 */
public class SQLInLoop extends BytecodeScanningDetector {
    private static final Set<String> queryClasses = UnmodifiableSet.create(
        "java/sql/Statement",
        "java/sql/PreparedStatement",
        "java/sql/CallableStatement"
    );
    

    private static final Set<String> queryMethods = UnmodifiableSet.create("execute", "executeQuery");

    private final BugReporter bugReporter;
    List<Integer> queryLocations;
    List<LoopLocation> loops;

    /**
     * constructs a SIL detector given the reporter to report bugs on
     * 
     * @param bugReporter
     *            the sync of bug reports
     */
    public SQLInLoop(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    /**
     * implements the visitor to create and clear the query locations and loops
     * collections
     * 
     * @param classContext
     *            the context object for the currently parsed java class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            queryLocations = new ArrayList<Integer>();
            loops = new ArrayList<LoopLocation>();
            super.visitClassContext(classContext);
        } finally {
            queryLocations = null;
            loops = null;
        }
    }

    /**
     * implements the visitor to clear the collections, and report the query
     * locations that are in loops
     * 
     * @param obj
     *            the context object for the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        queryLocations.clear();
        loops.clear();
        super.visitCode(obj);
        for (Integer qLoc : queryLocations) {
            for (LoopLocation lLoc : loops) {
                if (lLoc.isInLoop(qLoc.intValue())) {
                    bugReporter.reportBug(new BugInstance(this, BugType.SIL_SQL_IN_LOOP.name(), NORMAL_PRIORITY).addClass(this).addMethod(this)
                            .addSourceLine(this, qLoc.intValue()));
                    break;
                }
            }
        }
    }

    /**
     * implements the visitor to collect positions of queries and loops
     * 
     * @param seen
     *            the opcode of the currently parsed instruction
     */
    @Override
    public void sawOpcode(int seen) {
        if (seen == INVOKEINTERFACE) {
            String clsName = getClassConstantOperand();
            String methodName = getNameConstantOperand();

            if (queryClasses.contains(clsName) && queryMethods.contains(methodName))
                queryLocations.add(Integer.valueOf(getPC()));
        } else if ((seen == GOTO) || (seen == GOTO_W)) {
            int branchTarget = getBranchTarget();
            int pc = getPC();
            if (branchTarget < pc) {
                loops.add(new LoopLocation(branchTarget, pc));
            }
        }
    }

    /**
     * holds the start and end position of a loop
     */
    private static class LoopLocation {
        private final int startPC;
        private final int endPC;

        LoopLocation(int start, int end) {
            startPC = start;
            endPC = end;
        }

        boolean isInLoop(int pc) {
            return (pc >= startPC) && (pc <= endPC);
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }
}
