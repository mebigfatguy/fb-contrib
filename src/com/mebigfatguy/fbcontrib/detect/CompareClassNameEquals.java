/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2014 Bhaskar Maddala
 * Copyright (C) 2005-2014 Dave Brosius
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

import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.LocalVariableTable;

import com.mebigfatguy.fbcontrib.utils.BugType;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.OpcodeStack.CustomUserValue;
import edu.umd.cs.findbugs.OpcodeStack.Item;
import edu.umd.cs.findbugs.bcel.OpcodeStackDetector;

/**
 * In a JVM, Two classes are the same class (and consequently the same type) if
 * they are loaded by the same class loader, and they have the same fully
 * qualified name [JVMSpec 1999].
 *
 * Two classes with the same name but different package names are distinct, as
 * are two classes with the same fully qualified name loaded by different class
 * loaders.
 *
 * Find usage involving comparison of class names, rather than the class itself.
 *
 */
@CustomUserValue
public class CompareClassNameEquals extends OpcodeStackDetector {
    private boolean flag = false;
    private final BugReporter bugReporter;

    public CompareClassNameEquals(final BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public boolean shouldVisitCode(Code obj) {
        flag = false;
        LocalVariableTable lvt = getMethod().getLocalVariableTable();
        return lvt != null;
    }

    @Override
    public void afterOpcode(int seen) {
        super.afterOpcode(seen);
        if (flag == true) {
            stack.getStackItem(0).setUserValue(Boolean.TRUE);
            flag = false;
        }
    }

    @Override
    public void sawOpcode(int seen) {
    	if (seen == INVOKEVIRTUAL) {
            if ("getName".equals(getNameConstantOperand())
                    && "()Ljava/lang/String;".equals(getSigConstantOperand())
                    && "java/lang/Class".equals(getClassConstantOperand())) {
                flag = true;
            } else if ("equals".equals(getNameConstantOperand())
                    && "(Ljava/lang/Object;)Z".equals(getSigConstantOperand())
                    && "java/lang/String".equals(getClassConstantOperand())) {
                Item item = stack.getItemMethodInvokedOn(this);
                Object srcValue = item.getUserValue();
                item = stack.getStackItem(0);
                Object dstValue = item.getUserValue();
                if (Boolean.TRUE.equals(srcValue) && Boolean.TRUE.equals(dstValue)) {
                    bugReporter.reportBug(new BugInstance(this, BugType.CCNE_COMPARE_CLASS_EQUALS_NAME.name(),NORMAL_PRIORITY)
                    .addClass(this)
                    .addMethod(this)
                    .addSourceLine(this));
                }
            }
        }
//		stack.sawOpcode(this, seen);
    }
}
