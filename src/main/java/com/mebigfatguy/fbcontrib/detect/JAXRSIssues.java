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

import java.util.Set;

import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.ElementValuePair;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.classfile.ParameterAnnotationEntry;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;
import com.mebigfatguy.fbcontrib.utils.SignatureBuilder;
import com.mebigfatguy.fbcontrib.utils.UnmodifiableSet;
import com.mebigfatguy.fbcontrib.utils.Values;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.Detector;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.visitclass.PreorderVisitor;

/**
 * looks for various issues around use of the JAX_RS standard
 */
public class JAXRSIssues extends PreorderVisitor implements Detector {

    private static final Set<String> METHOD_ANNOTATIONS = UnmodifiableSet.create(
            //@formatter:off
            "Ljavax/ws/rs/HEAD;",
            "Ljavax/ws/rs/GET;",
            "Ljavax/ws/rs/PUT;",
            "Ljavax/ws/rs/POST;",
            "Ljavax/ws/rs/DELETE;",
            "Ljavax/ws/rs/POST;"
            //@formatter:on
    );

    private static final Set<String> PARAM_ANNOTATIONS = UnmodifiableSet.create(
            //@formatter:off
            "Ljavax/ws/rs/PathParam;",
            "Ljavax/ws/rs/CookieParam;",
            "Ljavax/ws/rs/FormParam;",
            "Ljavax/ws/rs/HeaderParam;",
            "Ljavax/ws/rs/MatrixParam;",
            "Ljavax/ws/rs/QueryParam;",
            "Ljavax/ws/rs/BeanParam;",
            "Ljavax/ws/rs/container/Suspended;",
            "Ljavax/ws/rs/core/Context;",
            "Lcom/wordnik/swagger/annotations/ApiParam;",
            "Lio/swagger/annotations/ApiParam;",
            "Lorg/glassfish/jersey/media/multipart/FormDataParam;"
            //@formatter:on
    );

    private static final Set<String> NATIVE_JAXRS_TYPES = UnmodifiableSet.create(
            //@formatter:off
            Values.SIG_JAVA_LANG_STRING,
            SignatureBuilder.SIG_BYTE_ARRAY,
            "Ljava/io/InputStream;",
            "Ljava/io/Reader;",
            "Ljava/io/File;",
            "Ljavax/activation/DataSource;",
            "Ljavax/xml/transform/Source;",
            "Ljavax/xml/bin/JAXBElement;",
            "Ljavax/ws/rc/core/MultivaluedMap;"
            //@formatter:on
    );

    private static final Set<String> VALID_CONTEXT_TYPES = UnmodifiableSet.create(
            //@formatter:off
            "Ljavax/ws/rs/core/Application;",
            "Ljavax/ws/rs/core/UriInfo;",
            "Ljavax/ws/rs/core/HttpHeaders;",
            "Ljavax/ws/rs/core/Request;",
            "Ljavax/ws/rs/core/SecurityContext;",
            "Ljavax/ws/rs/ext/Providers;",
            "Ljavax/servlet/ServletConfig;",
            "Ljavax/servlet/ServletContext;",
            "Ljavax/servlet/http/HttpServletRequest;",
            "Ljavax/servlet/http/HttpServletResponse;"
            //@formatter:on
    );

    private BugReporter bugReporter;
    private boolean hasClassConsumes;
    private String pathOnClass;

    public JAXRSIssues(BugReporter bugReporter) {
        this.bugReporter = bugReporter;
    }

    @Override
    public void visitClassContext(ClassContext classContext) {
        JavaClass cls = classContext.getJavaClass();
        pathOnClass = "";
        hasClassConsumes = false;
        for (AnnotationEntry entry : cls.getAnnotationEntries()) {
            if ("Ljavax/ws/rs/Consumes;".equals(entry.getAnnotationType())) {
                hasClassConsumes = true;
            } else if ("Ljavax/ws/rs/Path;".equals(entry.getAnnotationType())) {
                pathOnClass = getDefaultAnnotationValue(entry);
            }
        }

        cls.accept(this);
    }

    @Override
    public void visitMethod(Method obj) {

        if (obj.isSynthetic()) {
            return;
        }

        String path = null;
        boolean isJAXRS = false;
        boolean hasGet = false;
        boolean hasConsumes = false;

        for (AnnotationEntry entry : obj.getAnnotationEntries()) {
            String annotationType = entry.getAnnotationType();
            switch (annotationType) {
                case "Ljavax/ws/rs/GET;":
                    hasGet = true;
                    isJAXRS = true;
                break;

                case "Ljavax/ws/rs/Consumes;":
                    hasConsumes = true;
                break;

                case "Ljavax/ws/rs/Path;":
                    path = getDefaultAnnotationValue(entry);
                break;

                default:
                    // it is fine that GET is not captured here
                    if (METHOD_ANNOTATIONS.contains(annotationType)) {
                        isJAXRS = true;
                    }
                break;
            }

            if (hasGet && hasConsumes) {
                bugReporter.reportBug(new BugInstance(this, BugType.JXI_GET_ENDPOINT_CONSUMES_CONTENT.name(), NORMAL_PRIORITY).addClass(this).addMethod(this));
                break;
            }
        }

        if (isJAXRS) {
            processJAXRSMethod(obj, pathOnClass + path, hasConsumes || hasClassConsumes);
        }
    }

    private void processJAXRSMethod(Method m, String path, boolean hasConsumes) {
        Type[] parmTypes = m.getArgumentTypes();
        int numParms = parmTypes.length;
        if (numParms > 0) {
            boolean sawBareParm = false;

            ParameterAnnotationEntry[] pes = m.getParameterAnnotationEntries();
            int parmIndex = 0;
            for (ParameterAnnotationEntry pe : pes) {
                boolean foundParamAnnotation = false;
                for (AnnotationEntry a : pe.getAnnotationEntries()) {
                    String annotationType = a.getAnnotationType();
                    if (PARAM_ANNOTATIONS.contains(annotationType)) {
                        foundParamAnnotation = true;

                        if ((path != null) && "Ljavax/ws/rs/PathParam;".equals(annotationType)) {
                            String parmPath = getDefaultAnnotationValue(a);
                            if ((parmPath != null) && (!path.matches(".*\\{" + parmPath + "\\b.*"))) {
                                bugReporter.reportBug(new BugInstance(this, BugType.JXI_PARM_PARAM_NOT_FOUND_IN_PATH.name(), NORMAL_PRIORITY).addClass(this)
                                        .addMethod(this).addString("Path param: " + parmPath));
                            }
                        } else if ("Ljavax/ws/rs/core/Context;".equals(annotationType)) {
                            String parmSig = parmTypes[parmIndex].getSignature();
                            if (!VALID_CONTEXT_TYPES.contains(parmSig)) {
                                bugReporter.reportBug(new BugInstance(this, BugType.JXI_INVALID_CONTEXT_PARAMETER_TYPE.name(), NORMAL_PRIORITY).addClass(this)
                                        .addMethod(this).addString("Parameter signature: " + parmSig));
                            }
                        }
                    }
                }

                if (!foundParamAnnotation) {

                    if ((!sawBareParm) && (hasConsumes || NATIVE_JAXRS_TYPES.contains(parmTypes[parmIndex].getSignature()))) {
                        sawBareParm = true;
                    } else {
                        bugReporter.reportBug(new BugInstance(this, BugType.JXI_UNDEFINED_PARAMETER_SOURCE_IN_ENDPOINT.name(), NORMAL_PRIORITY).addClass(this)
                                .addMethod(this).addString("Parameter " + (parmIndex + 1)));
                        break;
                    }

                }

                parmIndex++;
            }

        }
    }

    private String getDefaultAnnotationValue(AnnotationEntry entry) {
        int numPairs = entry.getNumElementValuePairs();
        if (numPairs > 0) {
            ElementValuePair[] pairs = entry.getElementValuePairs();
            for (ElementValuePair pair : pairs) {
                if ("value".equals(pair.getNameString())) {
                    return pair.getValue().stringifyValue();
                }
            }
        }

        return null;
    }

    @Override
    public void report() {
        // required by the interface, but not used
    }
}
