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

import java.util.BitSet;
import java.util.Iterator;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.Detector;
import edu.umd.cs.findbugs.ba.BasicBlock;
import edu.umd.cs.findbugs.ba.CFG;
import edu.umd.cs.findbugs.ba.CFGBuilderException;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.ba.Edge;
import edu.umd.cs.findbugs.ba.EdgeTypes;
import edu.umd.cs.findbugs.visitclass.PreorderVisitor;

/**
 * Calculates the McCabe Cyclomatic Complexity measure and reports methods that
 * have an excessive value. This report value can be set with system property
 * 'fb-contrib.cc.limit'.
 */
public class CyclomaticComplexity extends PreorderVisitor implements Detector {
    public static final String LIMIT_PROPERTY = "fb-contrib.cc.limit";
    private BugReporter bugReporter;
    private ClassContext classContext;
    private int reportLimit = 50;

    /**
     * constructs a CC detector given the reporter to report bugs on
     * 
     * @param bugReporter
     *            the sync of bug reports
     */
    public CyclomaticComplexity(final BugReporter bugReporter) {
        this.bugReporter = bugReporter;
        Integer limit = Integer.getInteger(LIMIT_PROPERTY);
        if (limit != null)
            reportLimit = limit.intValue();
    }

    /**
     * overrides the visitor to store the class context
     * 
     * @param context
     *            the context object for the currently parsed class
     */
    @Override
    public void visitClassContext(final ClassContext context) {
        try {
            classContext = context;
            classContext.getJavaClass().accept(this);
        } finally {
            classContext = null;
        }
    }

    /**
     * implement the detector with null implementation
     */
    @Override
    public void report() {
        // not used, required by the Detector interface
    }

    /**
     * overrides the visitor to navigate the basic block list to count branches
     * 
     * @param obj
     *            the method of the currently parsed method
     */
    @Override
    public void visitMethod(final Method obj) {
        try {

            if ((obj.getAccessFlags() & Constants.ACC_SYNTHETIC) != 0)
                return;

            Code code = obj.getCode();
            if (code == null)
                return;

            // There really is no valid relationship between reportLimit and
            // code
            // length, but it is good enough. If the method is small, don't
            // bother
            if (code.getCode().length < (2 * reportLimit))
                return;

            BitSet exceptionNodeTargets = new BitSet();

            CFG cfg = classContext.getCFG(obj);
            int branches = 0;
            Iterator<BasicBlock> bbi = cfg.blockIterator();
            while (bbi.hasNext()) {
                BasicBlock bb = bbi.next();
                Iterator<Edge> iei = cfg.outgoingEdgeIterator(bb);
                while (iei.hasNext()) {
                    Edge e = iei.next();
                    int edgeType = e.getType();
                    if ((edgeType != EdgeTypes.FALL_THROUGH_EDGE) && (edgeType != EdgeTypes.RETURN_EDGE) && (edgeType != EdgeTypes.UNKNOWN_EDGE)) {
                        if ((edgeType == EdgeTypes.UNHANDLED_EXCEPTION_EDGE) || (edgeType == EdgeTypes.HANDLED_EXCEPTION_EDGE)) {
                            int nodeTarget = e.getTarget().getLabel();
                            if (!exceptionNodeTargets.get(nodeTarget)) {
                                exceptionNodeTargets.set(nodeTarget);
                                branches++;
                            }
                        } else {
                            branches++;
                        }
                    }
                }
            }

            if (branches > reportLimit) {

                int priority = (branches > (reportLimit * 2) ? HIGH_PRIORITY : NORMAL_PRIORITY);
                BugInstance bug = new BugInstance(this, BugType.CC_CYCLOMATIC_COMPLEXITY.name(), priority).addClass(this).addMethod(this)
                        .addSourceLine(classContext, this, 0).addInt(branches);

                bugReporter.reportBug(bug);
            }
        } catch (CFGBuilderException cbe) {
            bugReporter.logError("Failure examining basic blocks for method " + classContext.getJavaClass().getClassName() + "." + obj.getName()
                    + " in Cyclomatic Complexity detector", cbe);
        }
    }
}
