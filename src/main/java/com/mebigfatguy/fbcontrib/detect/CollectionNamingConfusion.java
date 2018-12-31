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

import java.util.Locale;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.LocalVariable;
import org.apache.bcel.classfile.LocalVariableTable;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.Detector;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.visitclass.PreorderVisitor;

/**
 * looks for fields and local variables that have Map, Set, List in their names but the variable is a collection of a different basic type.
 */
public class CollectionNamingConfusion extends PreorderVisitor implements Detector {

    private JavaClass mapInterface;
    private JavaClass setInterface;
    private JavaClass listInterface;
    private JavaClass queueInterface;

    private BugReporter bugReporter;
    private ClassContext clsContext;

    /**
     * constructs a CNC detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public CollectionNamingConfusion(BugReporter bugReporter) {
        this.bugReporter = bugReporter;

        try {
            mapInterface = Repository.lookupClass(Values.SLASHED_JAVA_UTIL_MAP);
            setInterface = Repository.lookupClass(Values.SLASHED_JAVA_UTIL_SET);
            listInterface = Repository.lookupClass(Values.SLASHED_JAVA_UTIL_LIST);
            queueInterface = Repository.lookupClass(Values.SLASHED_JAVA_UTIL_QUEUE);
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
            mapInterface = null;
            setInterface = null;
            listInterface = null;
            queueInterface = null;

        }
    }

    /**
     * overrides the visitor to make sure that the static initializer was able to load the map class
     *
     * @param classContext
     *            the currently parsed class
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        if (mapInterface != null) {
            this.clsContext = classContext;
            classContext.getJavaClass().accept(this);
        }
    }

    /**
     * overrides the visitor to look for fields where the name has 'Map', 'Set', 'List' in it but the type of that field isn't that.
     *
     * @param obj
     *            the currently parsed field
     */
    @Override
    public void visitField(Field obj) {
        if (checkConfusedName(obj.getName(), obj.getSignature())) {
            bugReporter.reportBug(new BugInstance(this, BugType.CNC_COLLECTION_NAMING_CONFUSION.name(), NORMAL_PRIORITY).addClass(this).addField(this)
                    .addString(obj.getName()));
        }
    }

    /**
     * overrides the visitor to look for local variables where the name has 'Map', 'Set', 'List' in it but the type of that field isn't that. note that this
     * only is useful if compiled with debug labels.
     *
     * @param obj
     *            the currently parsed method
     */
    @Override
    public void visitMethod(Method obj) {
        LocalVariableTable lvt = obj.getLocalVariableTable();
        if (lvt != null) {
            LocalVariable[] lvs = lvt.getLocalVariableTable();
            for (LocalVariable lv : lvs) {
                if (checkConfusedName(lv.getName(), lv.getSignature())) {
                    bugReporter.reportBug(new BugInstance(this, BugType.CNC_COLLECTION_NAMING_CONFUSION.name(), NORMAL_PRIORITY).addClass(this)
                            .addString(lv.getName()).addSourceLine(this.clsContext, this, lv.getStartPC()));
                }
            }
        }
    }

    /**
     * looks for a name that mentions a collection type but the wrong type for the variable
     *
     * @param methodOrVariableName
     *            the method or variable name
     * @param signature
     *            the variable signature
     * @return whether the name doesn't match the type
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "EXS_EXCEPTION_SOFTENING_RETURN_FALSE", justification = "No other simple way to determine whether class exists")
    private boolean checkConfusedName(String methodOrVariableName, String signature) {
        try {
            String name = methodOrVariableName.toLowerCase(Locale.ENGLISH);
            if ((name.endsWith("map") || (name.endsWith("set") && !name.endsWith("toset")) || name.endsWith("list") || name.endsWith("queue"))
                    && signature.startsWith("Ljava/util/")) {
                String clsName = SignatureUtils.stripSignature(signature);
                JavaClass cls = Repository.lookupClass(clsName);
                if ((cls.implementationOf(mapInterface) && !name.endsWith("map")) || (cls.implementationOf(setInterface) && !name.endsWith("set"))
                        || ((cls.implementationOf(listInterface) || cls.implementationOf(queueInterface)) && !name.endsWith("list")
                                && !name.endsWith("queue"))) {
                    return true;
                }
            }
        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
        }

        return false;
    }

    /**
     * implements the visitor by does nothing
     */
    @Override
    public void report() {
        // not used, implements the interface
    }
}
