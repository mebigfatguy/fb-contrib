/*
 * fb-contrib - Auxiliary detectors for Java programs
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

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.LocalVariable;
import org.apache.bcel.classfile.LocalVariableTable;
import org.apache.bcel.classfile.Method;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.Detector;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.visitclass.PreorderVisitor;

/** looks for fields and local variables that have Map, Set, List in their names
 * but the variable is a collection of a different basic type.
 */
public class CollectionNamingConfusion extends PreorderVisitor implements Detector {

    private static JavaClass MAP_CLASS;
    private static JavaClass SET_CLASS;
    private static JavaClass LIST_CLASS;
    
    static {
        try {
            MAP_CLASS = Repository.lookupClass("java/util/Map");
            SET_CLASS = Repository.lookupClass("java/util/Set");
            LIST_CLASS = Repository.lookupClass("java/util/List");
        } catch (ClassNotFoundException cnfe) {
            MAP_CLASS = null;
            SET_CLASS = null;
            LIST_CLASS = null;
        }
    }
    private BugReporter bugReporter;

    public CollectionNamingConfusion(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }
    
    public void visitClassContext(ClassContext classContext) {
        if (MAP_CLASS != null) {
            classContext.getJavaClass().accept(this);
        }
    }
    
    @Override
    public void visitField(Field obj) {
        if (checkConfusedName(obj.getName(), obj.getSignature())) {
            bugReporter.reportBug(new BugInstance(this, "CNC_COLLECTION_NAMING_CONFUSION", NORMAL_PRIORITY)
                        .addClass(this)
                        .addField(this)
                        .addString(obj.getName()));
        }
    }
    
    @Override
    public void visitMethod(Method obj) {
        LocalVariableTable lvt = obj.getLocalVariableTable();
        if (lvt != null ) {
            LocalVariable[] lvs = lvt.getLocalVariableTable();
            for (LocalVariable lv : lvs) {
                if (checkConfusedName(lv.getName(), lv.getSignature())) {
                    bugReporter.reportBug(new BugInstance(this, "CNC_COLLECTION_NAMING_CONFUSION", NORMAL_PRIORITY)
                    .addClass(this)
                    .addMethod(this)
                    .addString(lv.getName()));
                }
            }
        }
    }
    
    private boolean checkConfusedName(String name, String signature) {
        try {
            name = name.toLowerCase();
            if (name.endsWith("map") || name.endsWith("set") || name.endsWith("list")) {
                if (signature.startsWith("Ljava/util/")) {
                    String clsName = signature.substring(1, signature.length() - 1);
                    JavaClass cls = Repository.lookupClass(clsName);
                    if (cls.implementationOf(MAP_CLASS) && !name.endsWith("map")) {
                        return true;
                    } else if (cls.implementationOf(SET_CLASS) && !name.endsWith("set")) {
                        return true;
                    } else if (cls.implementationOf(LIST_CLASS) && !name.endsWith("list")) {
                        return true;                        
                    }
                }
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        }
        
        return false;
    }

    public void report() {
    }
}
