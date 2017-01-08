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

import java.util.HashSet;
import java.util.Set;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.ExceptionTable;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.OpcodeUtils;
import com.mebigfatguy.fbcontrib.utils.SignatureUtils;
import com.mebigfatguy.fbcontrib.utils.StopOpcodeParsingException;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

/**
 * looks for constructors, private methods or static methods that declare that they throw specific checked exceptions, but that do not. This just causes callers
 * of these methods to do extra work to handle an exception that will never be thrown. also looks for throws clauses where two exceptions declared to be thrown
 * are related through inheritance.
 */
public class BogusExceptionDeclaration extends BytecodeScanningDetector {

    private static final Set<String> safeClasses = UnmodifiableSet.create(
            //@formatter:off
            Values.SLASHED_JAVA_LANG_OBJECT,
            Values.SLASHED_JAVA_LANG_STRING,
            Values.SLASHED_JAVA_LANG_INTEGER,
            Values.SLASHED_JAVA_LANG_LONG,
            Values.SLASHED_JAVA_LANG_FLOAT,
            Values.SLASHED_JAVA_LANG_DOUBLE,
            Values.SLASHED_JAVA_LANG_SHORT,
            Values.SLASHED_JAVA_LANG_BYTE,
            Values.SLASHED_JAVA_LANG_BOOLEAN
            //@formatter:on
    );

    private final BugReporter bugReporter;
    private JavaClass runtimeExceptionClass;
    private JavaClass exceptionClass;

    private OpcodeStack stack;
    private Set<String> declaredCheckedExceptions;
    private boolean classIsFinal;
    private boolean classIsAnonymous;

    public BogusExceptionDeclaration(BugReporter bugReporter) {
        this.bugReporter = bugReporter;

        try {
            runtimeExceptionClass = Repository.lookupClass("java/lang/RuntimeException");
            exceptionClass = Repository.lookupClass(Values.SLASHED_JAVA_LANG_EXCEPTION);

        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
            runtimeExceptionClass = null;
            exceptionClass = null;
        }
    }

    /**
     * overrides the visitor to create the opcode stack
     *
     * @param classContext
     *            the context object of the currently parsed class
     *
     */
    @Override
    public void visitClassContext(ClassContext classContext) {
        try {
            if ((runtimeExceptionClass != null) && (exceptionClass != null)) {
                stack = new OpcodeStack();
                declaredCheckedExceptions = new HashSet<>(6);
                JavaClass cls = classContext.getJavaClass();
                classIsFinal = cls.isFinal();
                classIsAnonymous = cls.isAnonymous();
                super.visitClassContext(classContext);
            }
        } finally {
            declaredCheckedExceptions = null;
            stack = null;
        }
    }

    /**
     * implements the visitor to see if the method declares that it throws any checked exceptions.
     *
     * @param obj
     *            the context object of the currently parsed code block
     */
    @Override
    public void visitCode(Code obj) {
        Method method = getMethod();

        if (method.isSynthetic()) {
            return;
        }

        declaredCheckedExceptions.clear();
        stack.resetForMethodEntry(this);

        ExceptionTable et = method.getExceptionTable();
        if (et != null) {
            if (classIsFinal || classIsAnonymous || method.isStatic() || method.isPrivate() || method.isFinal()
                    || ((Values.CONSTRUCTOR.equals(method.getName()) && !isAnonymousInnerCtor(method, getThisClass())))) {
                String[] exNames = et.getExceptionNames();
                for (String exName : exNames) {
                    try {
                        JavaClass exCls = Repository.lookupClass(exName);
                        if (!exCls.instanceOf(runtimeExceptionClass)) {
                            declaredCheckedExceptions.add(exName);
                        }
                    } catch (ClassNotFoundException cnfe) {
                        bugReporter.reportMissingClass(cnfe);
                    }
                }
                if (!declaredCheckedExceptions.isEmpty()) {
                    try {
                        super.visitCode(obj);
                        if (!declaredCheckedExceptions.isEmpty()) {
                            BugInstance bi = new BugInstance(this, BugType.BED_BOGUS_EXCEPTION_DECLARATION.name(), NORMAL_PRIORITY).addClass(this)
                                    .addMethod(this).addSourceLine(this, 0);
                            for (String ex : declaredCheckedExceptions) {
                                bi.addString(ex.replaceAll("/", "."));
                            }
                            bugReporter.reportBug(bi);
                        }
                    } catch (StopOpcodeParsingException e) {
                        // no exceptions left
                    }
                }
            }

            String[] exNames = et.getExceptionNames();
            for (int i = 0; i < (exNames.length - 1); i++) {
                try {
                    JavaClass exCls1 = Repository.lookupClass(exNames[i]);
                    for (int j = i + 1; j < exNames.length; j++) {
                        JavaClass exCls2 = Repository.lookupClass(exNames[j]);
                        JavaClass childEx;
                        JavaClass parentEx;
                        if (exCls1.instanceOf(exCls2)) {
                            childEx = exCls1;
                            parentEx = exCls2;
                        } else if (exCls2.instanceOf(exCls1)) {
                            childEx = exCls2;
                            parentEx = exCls1;
                        } else {
                            continue;
                        }

                        if (!parentEx.equals(exceptionClass)) {
                            bugReporter.reportBug(new BugInstance(this, BugType.BED_HIERARCHICAL_EXCEPTION_DECLARATION.name(), NORMAL_PRIORITY).addClass(this)
                                    .addMethod(this).addString(childEx.getClassName() + " derives from " + parentEx.getClassName()));
                            return;
                        }

                    }
                } catch (ClassNotFoundException cnfe) {
                    bugReporter.reportMissingClass(cnfe);
                }
            }
        }
    }

    /**
     * checks to see if this method is a constructor of an instance based inner class, the handling of the Exception table for this method is odd, -- doesn't
     * seem correct, in some cases. So just ignore these cases
     *
     * @param m
     *            the method to check
     * @param cls
     *            the cls that owns the method
     * @return whether this method is a ctor of an instance based anonymous inner class
     */
    private static boolean isAnonymousInnerCtor(Method m, JavaClass cls) {
        return Values.CONSTRUCTOR.equals(m.getName())
            && cls.getClassName().lastIndexOf('$') >= 0;
    }

    /**
     * implements the visitor to look for method calls that could throw the exceptions that are listed in the declaration.
     */
    @Override
    public void sawOpcode(int seen) {
        try {

            stack.precomputation(this);

            if (OpcodeUtils.isStandardInvoke(seen)) {
                String clsName = getClassConstantOperand();
                if (!safeClasses.contains(clsName)) {
                    try {
                        JavaClass cls = Repository.lookupClass(clsName);
                        Method[] methods = cls.getMethods();
                        String methodName = getNameConstantOperand();
                        String signature = getSigConstantOperand();
                        boolean found = false;
                        for (Method m : methods) {
                            if (m.getName().equals(methodName) && m.getSignature().equals(signature)) {

                                if (isAnonymousInnerCtor(m, cls)) {
                                    // The java compiler doesn't properly attached an Exception Table to anonymous constructors, so just clear if so
                                    break;
                                }

                                ExceptionTable et = m.getExceptionTable();
                                if (et != null) {
                                    String[] thrownExceptions = et.getExceptionNames();
                                    for (String thrownException : thrownExceptions) {
                                        removeThrownExceptionHierarchy(thrownException);
                                    }
                                }
                                found = true;
                                break;
                            }
                        }

                        if (!found) {
                            clearExceptions();
                        }
                    } catch (ClassNotFoundException cnfe) {
                        bugReporter.reportMissingClass(cnfe);
                        clearExceptions();
                    }
                } else if ("wait".equals(getNameConstantOperand())) {
                    removeException("java.lang.InterruptedException");
                }
            } else if (seen == ATHROW) {
                if (stack.getStackDepth() > 0) {
                    OpcodeStack.Item item = stack.getStackItem(0);
                    String exSig = item.getSignature();
                    String thrownException = SignatureUtils.stripSignature(exSig);
                    removeThrownExceptionHierarchy(thrownException);
                } else {
                    clearExceptions();
                }
            }
        } finally {
            stack.sawOpcode(this, seen);
        }
    }

    /**
     * removes this thrown exception the list of declared thrown exceptions, including all exceptions in this exception's hierarchy. If an exception class is
     * found that can't be loaded, then just clear the list of declared checked exceptions and get out.
     *
     * @param thrownException
     *            the exception and it's hierarchy to remove
     */
    private void removeThrownExceptionHierarchy(String thrownException) {
        try {
            if (Values.DOTTED_JAVA_LANG_EXCEPTION.equals(thrownException)) {
                // Exception can be thrown even tho the method isn't declared to throw Exception in the case of templated Exceptions
                clearExceptions();
            } else {
                removeException(thrownException);
                JavaClass exCls = Repository.lookupClass(thrownException);
                String clsName;

                do {
                    exCls = exCls.getSuperClass();
                    if (exCls == null) {
                        break;
                    }
                    clsName = exCls.getClassName();
                    removeException(clsName);
                } while (!declaredCheckedExceptions.isEmpty() && !Values.DOTTED_JAVA_LANG_EXCEPTION.equals(clsName)
                        && !Values.DOTTED_JAVA_LANG_ERROR.equals(clsName));
            }

        } catch (ClassNotFoundException cnfe) {
            bugReporter.reportMissingClass(cnfe);
            clearExceptions();
        }
    }

    /**
     * removes the declared checked exception, and if that was the last declared exception, stops opcode parsing by throwing exception
     *
     * @param clsName
     *            the name of the exception to remove
     */
    private void removeException(String clsName) {
        declaredCheckedExceptions.remove(clsName);
        if (declaredCheckedExceptions.isEmpty()) {
            throw new StopOpcodeParsingException();
        }
    }

    /**
     * clears all declared checked exceptions and throws an exception to stop opcode parsing
     */
    private void clearExceptions() {
        declaredCheckedExceptions.clear();
        throw new StopOpcodeParsingException();
    }
}
