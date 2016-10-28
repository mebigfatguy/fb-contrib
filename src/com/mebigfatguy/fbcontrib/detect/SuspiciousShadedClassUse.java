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

import java.util.Set;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;

public class SuspiciousShadedClassUse extends BytecodeScanningDetector {

    private static final Set<String> SUSPICIOUS_ROOTS = UnmodifiableSet.create(
    // @formatter:off
        "/org/",
        "/com/",
        "/edu/"
    // @formatter:on
    );

    private BugReporter bugReporter;

    public SuspiciousShadedClassUse(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void sawOpcode(int seen) {

        if (OpcodeUtils.isInvoke(seen)) {
            String invokedCls = getClassConstantOperand();

            for (String suspiciousRoot : SUSPICIOUS_ROOTS) {
                int rootPos = invokedCls.indexOf(suspiciousRoot);
                if (rootPos >= 0) {
                    String invokedPrefix = invokedCls.substring(0, rootPos);
                    String[] parts = invokedPrefix.split("/");
                    if (!SignatureUtils.similarPackages(invokedCls, getClassName(), Math.min(2, parts.length))) {
                        bugReporter.reportBug(new BugInstance(this, BugType.SSCU_SUSPICIOUS_SHADED_CLASS_USE.name(), NORMAL_PRIORITY).addClass(this)
                                .addMethod(this).addSourceLine(this));
                    }
                }
            }
        }
    }
}
