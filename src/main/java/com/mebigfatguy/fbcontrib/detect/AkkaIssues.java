/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2020 Dave Brosius
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

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;

import com.mebigfatguy.fbcontrib.utils.BugType;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for common problems using akka.
 */
@CustomUserValue
public class AkkaIssues extends BytecodeScanningDetector {

    private BugReporter bugReporter;
    private OpcodeStack stack;
    private JavaClass akkaRouteDirectivesClass;
    private JavaClass pekkoRouteDirectivesClass;
    private boolean hasAkka;
    private boolean hasPekko;

    /**
     * constructs a AKI detector given the reporter to report bugs on
     *
     * @param bugReporter the sync of bug reports
     */
    public AkkaIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;

        try {
            akkaRouteDirectivesClass = Repository.lookupClass("akka.http.javadsl.server.directives.RouteDirectives");
            hasAkka = true;
        } catch (ClassNotFoundException e) {
            hasAkka = false;
        }
        try {
            pekkoRouteDirectivesClass = Repository
                    .lookupClass("org.apache.pekko.http.javadsl.server.directives.RouteDirectives");
            hasPekko = true;
        } catch (ClassNotFoundException e) {
            hasPekko = false;
        }
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        if (!hasAkka && !hasPekko) {
            return;
        }

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
        Integer userValue = null;
        try {
            switch (seen) {
            case org.apache.bcel.Const.INVOKEVIRTUAL:
                String methodName = getNameConstantOperand();
                if ("route".equals(methodName) || "concat".equals(methodName)) {
                    String clsName = getClassConstantOperand();
                    JavaClass cls = Repository.lookupClass(clsName);
                    if ((hasAkka && cls.instanceOf(akkaRouteDirectivesClass))
                            || (hasPekko && cls.instanceOf(pekkoRouteDirectivesClass))) {
                        OpcodeStack.Item itm = null;
                        int bogusSize = -1;
                        if ("route".equals(methodName)) {
                            if (stack.getStackDepth() > 0) {
                                itm = stack.getStackItem(0);
                                bogusSize = 1;
                            }
                        } else if ("concat".equals(methodName)) {
                            if (stack.getStackDepth() > 1) {
                                itm = stack.getStackItem(0);
                                bogusSize = 0;
                            }
                        }

                        if (itm != null) {
                            Integer size = (Integer) itm.getUserValue();
                            if (size != null && size.intValue() == bogusSize) {
                                if (hasAkka) {
                                    bugReporter.reportBug(new BugInstance(this,
                                            BugType.AKI_SUPERFLUOUS_ROUTE_SPECIFICATION.name(), NORMAL_PRIORITY)
                                            .addClass(this).addMethod(this).addSourceLine(this));
                                } else if (hasPekko) {
                                    bugReporter.reportBug(new BugInstance(this,
                                            BugType.PKI_SUPERFLUOUS_ROUTE_SPECIFICATION.name(), NORMAL_PRIORITY)
                                            .addClass(this).addMethod(this).addSourceLine(this));
                                }
                            }
                        }
                    }
                }
                break;

            case org.apache.bcel.Const.ANEWARRAY:
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item itm = stack.getStackItem(0);
                    userValue = (Integer) itm.getConstant();
                }
                break;
            }
        } catch (ClassNotFoundException e) {
            bugReporter.reportMissingClass(e);
        } finally {
            stack.sawOpcode(this, seen);
            if (userValue != null && stack.getStackDepth() > 0) {
                OpcodeStack.Item itm = stack.getStackItem(0);
                itm.setUserValue(userValue);
            }
        }
    }
}
