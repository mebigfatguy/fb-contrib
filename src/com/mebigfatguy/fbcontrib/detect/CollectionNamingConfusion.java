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

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.LocalVariable;
import org.apache.bcel.classfile.LocalVariableTable;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.Detector;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.visitclass.PreorderVisitor;

/**
 * looks for fields and local variables that have Map, Set, List in their names
 * but the variable is a collection of a different basic type.
 */
public class CollectionNamingConfusion extends PreorderVisitor implements Detector {

    private static JavaClass MAP_CLASS;
    private static JavaClass SET_CLASS;
    private static JavaClass LIST_CLASS;
    private static JavaClass QUEUE_CLASS;

    static {
        try {
            MAP_CLASS = Repository.lookupClass("java/util/Map");
            SET_CLASS = Repository.lookupClass("java/util/Set");
            LIST_CLASS = Repository.lookupClass("java/util/List");
            QUEUE_CLASS = Repository.lookupClass("java/util/Queue");
        } catch (ClassNotFoundException cnfe) {
            MAP_CLASS = null;
            SET_CLASS = null;
            LIST_CLASS = null;
            QUEUE_CLASS = null;
        }
    }

    private BugReporter bugReporter;
    private ClassContext classContext;

    public CollectionNamingConfusion(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        if (MAP_CLASS != null) {
            this.classContext = classContext;
            classContext.getJavaClass().accept(this);
        }
    }

    @Override
    public void visitField(Field obj) {
        if (checkConfusedName(obj.getName(), obj.getSignature())) {
            bugReporter.reportBug(new BugInstance(this, BugType.CNC_COLLECTION_NAMING_CONFUSION.name(), NORMAL_PRIORITY).addClass(this).addField(this)
                    .addString(obj.getName()));
        }
    }

    @Override
    public void visitMethod(Method obj) {
        LocalVariableTable lvt = obj.getLocalVariableTable();
        if (lvt != null) {
            LocalVariable[] lvs = lvt.getLocalVariableTable();
            for (LocalVariable lv : lvs) {
                if (checkConfusedName(lv.getName(), lv.getSignature())) {
                    bugReporter.reportBug(new BugInstance(this, BugType.CNC_COLLECTION_NAMING_CONFUSION.name(), NORMAL_PRIORITY).addClass(this)
                            .addString(lv.getName()).addSourceLine(this.classContext, this, lv.getStartPC()));
                }
            }
        }
    }

    /**
     * looks for a name that mentions a collection type but the wrong type for
     * the variable
     * 
     * @param name
     *            the variable name
     * @param signature
     *            the variable signature
     * @return whether the name doesn't match the type
     */
    private boolean checkConfusedName(String name, String signature) {
        try {
            name = name.toLowerCase();
            if (name.endsWith("map") || (name.endsWith("set") && !name.endsWith("toset")) || name.endsWith("list") || name.endsWith("queue")) {
                if (signature.startsWith("Ljava/util/")) {
                    String clsName = SignatureUtils.stripSignature(signature);
                    JavaClass cls = Repository.lookupClass(clsName);
                    if (cls.implementationOf(MAP_CLASS) && !name.endsWith("map")) {
                        return true;
                    } else if (cls.implementationOf(SET_CLASS) && !name.endsWith("set")) {
                        return true;
                    } else if (cls.implementationOf(LIST_CLASS) && !name.endsWith("list")) {
                        return true;
                    } else if (cls.implementationOf(QUEUE_CLASS) && !name.endsWith("queue")) {
                        return true;
                    }
                }
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        }

        return false;
    }

    @Override
    public void report() {
        // not used, implements the interface
    }
}
