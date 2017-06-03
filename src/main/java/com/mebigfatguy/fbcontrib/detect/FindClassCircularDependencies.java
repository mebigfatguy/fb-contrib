/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2017 Dave Brosius
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantClass;
import org.apache.bcel.classfile.ElementValue;
import org.apache.bcel.classfile.JavaClass;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.ToString;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.internalAnnotations.DottedClassName;

/**
 * looks for classes that have dependencies on each other in a circular way. Class initialization can be compromised in this scenario, and usually points to a
 * bad data model. Consider using interfaces to break this hard circular dependency.
 */
public class FindClassCircularDependencies extends BytecodeScanningDetector {

    private static final Pattern ARRAY_PATTERN = Pattern.compile("\\[+(L.*)");
    private Map<String, Set<String>> dependencyGraph = null;
    private BugReporter bugReporter;
    private String clsName;

    /**
     * constructs a FCCD detector given the reporter to report bugs on
     *
     * @param bugReporter
     *            the sync of bug reports
     */
    public FindClassCircularDependencies(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
        this.dependencyGraph = new HashMap<>();
    }

    @Override
    public void visit(JavaClass obj) {
        clsName = obj.getClassName();
    }

    @Override
    public void visitAnnotation(@DottedClassName String annotationClass, Map<String, ElementValue> map, boolean runtimeVisible) {
        if (!runtimeVisible) {
            return;
        }

        for (ElementValue v : map.values()) {
            if (v.getElementValueType() == ElementValue.CLASS) {
                String annotationClsAttr = SignatureUtils.stripSignature(v.stringifyValue());

                Set<String> dependencies = getDependenciesForClass(clsName);
                dependencies.add(annotationClsAttr);
            }
        }
    }

    @Override
    public void sawOpcode(int seen) {
        if ((seen == INVOKESPECIAL) || (seen == INVOKESTATIC) || (seen == INVOKEVIRTUAL)) {
            processInvoke();

        } else if (seen == LDC) {
            processLoadConstant();
        }
    }

    private void processInvoke() {
        String refClsName = getClassConstantOperand();
        refClsName = normalizeArrayClass(refClsName.replace('/', '.'));

        if (refClsName.startsWith("java")) {
            return;
        }

        if (clsName.equals(refClsName)) {
            return;
        }

        if (isEnclosingClassName(clsName, refClsName) || isEnclosingClassName(refClsName, clsName)) {
            return;
        }

        if (isStaticChild(clsName, refClsName) || isStaticChild(refClsName, clsName)) {
            return;
        }

        Set<String> dependencies = getDependenciesForClass(clsName);
        dependencies.add(refClsName);
    }

    private void processLoadConstant() {
        Constant c = getConstantRefOperand();
        if (c instanceof ConstantClass) {
            String refClsName = normalizeArrayClass(getClassConstantOperand().replace('/', '.'));
            if (!refClsName.equals(clsName)) {
                Set<String> dependencies = getDependenciesForClass(clsName);
                dependencies.add(refClsName);
            }
        }
    }

    private String normalizeArrayClass(String clsName) {
        if (!clsName.startsWith(Values.SIG_ARRAY_PREFIX)) {
            return clsName;
        }

        Matcher m = ARRAY_PATTERN.matcher(clsName);
        if (!m.matches()) {
            return clsName;
        }

        return m.group(1);
    }

    /**
     * returns a set of dependent class names for a class, and if it doesn't exist create the set install it, and then return;
     *
     * @param clsName
     *            the class for which you are trying to get dependencies
     * @return the active set of classes that are dependent on the specified class
     */
    private Set<String> getDependenciesForClass(String clsName) {
        Set<String> dependencies = dependencyGraph.get(clsName);
        if (dependencies == null) {
            dependencies = new HashSet<>();
            dependencyGraph.put(clsName, dependencies);
        }

        return dependencies;
    }

    private boolean isEnclosingClassName(String outerClass, String innerClass) {
        return innerClass.startsWith(outerClass) && (innerClass.indexOf('$') >= 0);
    }

    @Override
    public void report() {
        removeDependencyLeaves();

        LoopFinder lf = new LoopFinder();

        while (!dependencyGraph.isEmpty()) {
            String className = dependencyGraph.keySet().iterator().next();
            Set<String> loop = lf.findLoop(dependencyGraph, className);
            boolean pruneLeaves;
            if (loop != null) {
                BugInstance bug = new BugInstance(this, BugType.FCCD_FIND_CLASS_CIRCULAR_DEPENDENCY.name(), NORMAL_PRIORITY);
                for (String loopCls : loop) {
                    bug.addClass(loopCls);
                }
                bugReporter.reportBug(bug);
                pruneLeaves = removeLoopLinks(loop);
            } else {
                dependencyGraph.remove(className);
                pruneLeaves = true;
            }
            if (pruneLeaves) {
                removeDependencyLeaves();
            }
        }

        dependencyGraph.clear();
    }

    private boolean isStaticChild(String child, String parent) {
        if (!child.startsWith(parent)) {
            return false;
        }

        int parentLength = parent.length();
        return ((child.charAt(parentLength) == '.') && (child.indexOf('.', parentLength + 1) < 0));
    }

    private void removeDependencyLeaves() {
        boolean changed = true;
        while (changed) {
            changed = false;
            Iterator<Set<String>> it = dependencyGraph.values().iterator();
            while (it.hasNext()) {
                Set<String> dependencies = it.next();

                boolean foundClass = false;
                Iterator<String> dit = dependencies.iterator();
                while (dit.hasNext()) {
                    foundClass = dependencyGraph.containsKey(dit.next());
                    if (!foundClass) {
                        dit.remove();
                        changed = true;
                    }
                }
                if (dependencies.isEmpty()) {
                    it.remove();
                    changed = true;
                }
            }
        }
    }

    private boolean removeLoopLinks(Set<String> loop) {
        Set<String> dependencies = null;
        for (String className : loop) {
            if (dependencies != null) {
                dependencies.remove(className);
            }
            dependencies = dependencyGraph.get(className);
        }
        if (dependencies != null) {
            dependencies.remove(loop.iterator().next());
        }

        boolean removedClass = false;
        Iterator<String> cIt = loop.iterator();
        while (cIt.hasNext()) {
            String className = cIt.next();
            dependencies = dependencyGraph.get(className);
            if (dependencies.isEmpty()) {
                cIt.remove();
                removedClass = true;
            }
        }
        return removedClass;
    }

    /**
     * finds class dependency loops in a directed graph
     */
    static class LoopFinder {

        private Map<String, Set<String>> dGraph = null;
        private String startClass = null;
        private Set<String> visited = null;
        private Set<String> loop = null;

        public Set<String> findLoop(Map<String, Set<String>> dependencyGraph, String startCls) {
            dGraph = dependencyGraph;
            startClass = startCls;
            visited = new HashSet<>();
            loop = new LinkedHashSet<>();
            if (findLoop(startClass)) {
                return loop;
            }
            return null;
        }

        private boolean findLoop(String curClass) {
            if (curClass.contains("$")) {
                return false;
            }

            Set<String> dependencies = dGraph.get(curClass);
            if (dependencies == null) {
                return false;
            }

            visited.add(curClass);
            loop.add(curClass);
            for (String depClass : dependencies) {
                if (depClass.equals(startClass)) {
                    return true;
                }

                if (visited.contains(depClass)) {
                    continue;
                }

                if (findLoop(depClass)) {
                    return true;
                }
            }
            loop.remove(curClass);
            return false;
        }

        @Override
        public String toString() {
            return ToString.build(this);
        }
    }
}
