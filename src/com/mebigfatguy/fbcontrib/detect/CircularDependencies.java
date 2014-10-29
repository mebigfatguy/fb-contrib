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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.bcel.classfile.JavaClass;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.ToString;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;

/**
 * looks for classes that have dependencies on each other in a circular way.
 * Class initialization can be compromised in this scenario, and usually points to
 * a bad data model. Consider using interfaces to break this hard circular dependency.
 */
public class CircularDependencies extends BytecodeScanningDetector {
    private Map<String, Set<String>> dependencyGraph = null;

    private BugReporter bugReporter;

    private String clsName;

    /**
     * constructs a CD detector given the reporter to report bugs on
     * 
     * @param bugReporter
     *            the sync of bug reports
     */
    public CircularDependencies(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
        this.dependencyGraph = new HashMap<String, Set<String>>();
    }

    @Override
    public void visit(JavaClass obj) {
        clsName = obj.getClassName();
    }

    @Override
    public void sawOpcode(int seen) {
        if ((seen == INVOKESPECIAL) || (seen == INVOKESTATIC) || (seen == INVOKEVIRTUAL)) {
            String refClsName = getClassConstantOperand();
            refClsName = refClsName.replace('/', '.');
            if (refClsName.startsWith("java"))
                return;

            if (clsName.equals(refClsName))
                return;

            if (clsName.startsWith(refClsName) && (refClsName.indexOf('$') >= 0))
                return;

            if (refClsName.startsWith(clsName) && (clsName.indexOf('$') >= 0))
                return;

            Set<String> dependencies = dependencyGraph.get(clsName);
            if (dependencies == null) {
                dependencies = new HashSet<String>();
                dependencyGraph.put(clsName, dependencies);
            }

            dependencies.add(refClsName);
        }
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
                BugInstance bug = new BugInstance(this, BugType.CD_CIRCULAR_DEPENDENCY.name(), NORMAL_PRIORITY);
                for (String loopCls : loop) {
                    bug.addClass(loopCls);
                }
                bugReporter.reportBug(bug);
                pruneLeaves = removeLoopLinks(loop);
            } else {
                dependencyGraph.remove(className);
                pruneLeaves = true;
            }
            if (pruneLeaves)
                removeDependencyLeaves();
        }

        dependencyGraph.clear();
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
            if (dependencies != null)
                dependencies.remove(className);
            dependencies = dependencyGraph.get(className);
        }
        if (dependencies != null)
            dependencies.remove(loop.iterator().next());

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

    static class LoopFinder {
    	
        private Map<String, Set<String>> dGraph = null;
        private String startClass = null;
        private Set<String> visited = null;
        private Set<String> loop = null;

        public Set<String> findLoop(Map<String, Set<String>> dependencyGraph, String startCls) {
            dGraph = dependencyGraph;
            startClass = startCls;
            visited = new HashSet<String>();
            loop = new LinkedHashSet<String>();
            if (findLoop(startClass))
                return loop;
            return null;
        }

        private boolean findLoop(String curClass) {
            if (curClass.contains("$"))
                return false;
            
            Set<String> dependencies = dGraph.get(curClass);
            if (dependencies == null)
                return false;

            visited.add(curClass);
            loop.add(curClass);
            for (String depClass : dependencies) {
                if (depClass.equals(startClass))
                    return true;

                if (visited.contains(depClass))
                    continue;

                if (findLoop(depClass))
                    return true;
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
