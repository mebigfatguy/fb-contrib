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

import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.Detector;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.visitclass.PreorderVisitor;

public class JAXRSIssues extends PreorderVisitor implements Detector {

    private BugReporter bugReporter;
    
    public JAXRSIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }
    
    @Override
    public void visitClassContext(ClassContext classContext) {
        JavaClass cls = classContext.getJavaClass();
        cls.accept(this);
    }
    
    @Override
    public void visitMethod(Method obj) {
        boolean hasGet = false;
        boolean hasConsumes = false;
        
        for (AnnotationEntry entry : obj.getAnnotationEntries()) {
            switch (entry.getAnnotationType()) {
            case "Ljavax/ws/rs/GET;":
                hasGet = true;
                break;
                
            case "Ljavax/ws/rs/Consumes;":
                hasConsumes = true;
                break;
            }
            
            if (hasGet && hasConsumes) {
                bugReporter.reportBug(new BugInstance(this, BugType.JXI_GET_ENDPOINT_CONSUMES_CONTENT.name(), NORMAL_PRIORITY)
                                .addClass(this)
                                .addMethod(this));
                break;
            }
        }
    }

    @Override
    public void report() {
    }
}
