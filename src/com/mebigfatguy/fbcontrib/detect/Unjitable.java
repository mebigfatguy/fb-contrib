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

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.Detector;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.visitclass.PreorderVisitor;

/**
 * looks for methods that are bigger than 8000 bytes, as these methods are ignored by
 * the jit for compilation, causing them to always be interpreted.
 */
public class Unjitable extends PreorderVisitor implements Detector {

	private static final int UNJITABLE_CODE_LENGTH = 8000;
	
	private BugReporter bugReporter;
	
	public Unjitable(BugReporter bugReporter) {
		this.bugReporter = bugReporter;
	}

	
    /**
     * implements the visitor to accept the class for visiting
     *
     * @param classContext the context object of the currently parsed class
     */
	@Override
	public void visitClassContext(ClassContext classContext) {
		JavaClass cls = classContext.getJavaClass();
        cls.accept(this);
	}


    /**
     * implements the visitor to look at the size of the method. static initializer are
     * ignored as these will only be executed once anyway.
     *
     * @param obj the context object of the currently parsed method
     */
	@Override
	public void visitCode(Code obj) {
		
		Method m = getMethod();
		if ((((m.getAccessFlags() & Constants.ACC_STATIC) == 0) || !Values.STATIC_INITIALIZER.equals(m.getName()))
		&&  (!m.getName().contains("enum constant"))) { //a findbugs thing!!
			byte[] code = obj.getCode();
			if (code.length >= UNJITABLE_CODE_LENGTH) {
				bugReporter.reportBug(new BugInstance(this, BugType.UJM_UNJITABLE_METHOD.name(), NORMAL_PRIORITY)
								.addClass(this)
								.addMethod(this)
								.addString("Code Bytes: " + code.length));
			}
		}
	}
	
	@Override
	public void report() {
	}
}
