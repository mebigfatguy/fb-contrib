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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.ElementValuePair;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.classfile.ParameterAnnotationEntry;
import org.apache.bcel.generic.Type;

import com.mebigfatguy.fbcontrib.utils.BugType;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.Detector;
import edu.umd.cs.findbugs.ba.ClassContext;
import edu.umd.cs.findbugs.visitclass.PreorderVisitor;

public class JAXRSIssues extends PreorderVisitor implements Detector {

    private static final Set<String> METHOD_ANNOTATIONS;
    static {
        Set<String> ma = new HashSet<String>();
        ma.add("Ljavax/ws/rs/HEAD;");
        ma.add("Ljavax/ws/rs/GET;");
        ma.add("Ljavax/ws/rs/PUT;");
        ma.add("Ljavax/ws/rs/POST;");
        ma.add("Ljavax/ws/rs/DELETE;");
        ma.add("Ljavax/ws/rs/POST;");
        METHOD_ANNOTATIONS = Collections.<String>unmodifiableSet(ma);
    }
    
    private static final Set<String> PARAM_ANNOTATIONS;
    static {
        Set<String> pa = new HashSet<String>();
        pa.add("Ljavax/ws/rs/PathParam;");
        pa.add("Ljavax/ws/rs/CookieParam;");
        pa.add("Ljavax/ws/rs/FormParam;");
        pa.add("Ljavax/ws/rs/HeaderParam;");
        pa.add("Ljavax/ws/rs/MatrixParam;");
        pa.add("Ljavax/ws/rs/QueryParam;");
        pa.add("Ljavax/ws/rs/core/Context;");
        
        pa.add("Lcom/wordnik/swagger/annotations/ApiParam");
        PARAM_ANNOTATIONS = Collections.<String>unmodifiableSet(pa);
    }
    
    private static final Set<String> NATIVE_JAXRS_TYPES;
    static {
        Set<String> njt = new HashSet<String>();
        njt.add("Ljava/lang/String;");
        njt.add("[B");
        njt.add("Ljava/io/InputStream;");
        njt.add("Ljava/io/Reader;");
        njt.add("Ljava/io/File;");
        njt.add("Ljavax/activation/DataSource;");
        njt.add("Ljavax/xml/transform/Source;");
        njt.add("Ljavax/xml/bin/JAXBElement;");
        njt.add("Ljavax/ws/rc/core/MultivaluedMap;");
        
        NATIVE_JAXRS_TYPES = Collections.<String>unmodifiableSet(njt);
    }
    
    private static final Set<String> VALID_CONTEXT_TYPES;
    static {
        Set<String> vct = new HashSet<String>();
        vct.add("Ljavax/ws/rs/core/Application;");
        vct.add("Ljavax/ws/rs/core/UriInfo;");
        vct.add("Ljavax/ws/rs/core/HttpHeaders;");
        vct.add("Ljavax/ws/rs/core/Request;");
        vct.add("Ljavax/ws/rs/core/SecurityContext;");
        vct.add("Ljavax/ws/rs/ext/Providers;");
        vct.add("Ljavax/servlet/ServletConfig;");
        vct.add("Ljavax/servlet/ServletContext;");
        vct.add("Ljavax/servlet/http/HttpServletRequest;");
        vct.add("Ljavax/servlet/http/HttpServletResponse;");
        
        VALID_CONTEXT_TYPES = Collections.<String>unmodifiableSet(vct);
    }
    
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
                bugReporter.reportBug(new BugInstance(this, BugType.JXI_GET_ENDPOINT_CONSUMES_CONTENT.name(), NORMAL_PRIORITY)
                                .addClass(this)
                                .addMethod(this));
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
                            if ((parmPath != null) && (!path.matches(".*\\{" + parmPath + "\\b.*" ))) {
                                bugReporter.reportBug(new BugInstance(this, BugType.JXI_PARM_PARAM_NOT_FOUND_IN_PATH.name(), NORMAL_PRIORITY)
                                        .addClass(this)
                                        .addMethod(this)
                                        .addString(parmPath));
                            }
                        } else if ("Ljavax/ws/rs/core/Context;".equals(annotationType)) {
                            String parmSig = parmTypes[parmIndex].getSignature();
                            if (!VALID_CONTEXT_TYPES.contains(parmSig)) {
                                bugReporter.reportBug(new BugInstance(this, BugType.JXI_INVALID_CONTEXT_PARAMETER_TYPE.name(), NORMAL_PRIORITY)
                                        .addClass(this)
                                        .addMethod(this)
                                        .addString(parmSig));
                            }
                        }
                    }
                }
                
                if (!foundParamAnnotation) {
                    
                    if ((!sawBareParm) && (hasConsumes || NATIVE_JAXRS_TYPES.contains(parmTypes[parmIndex].getSignature()))) {
                        sawBareParm = true;
                    } else {
                        bugReporter.reportBug(new BugInstance(this, BugType.JXI_UNDEFINED_PARAMETER_SOURCE_IN_ENDPOINT.name(), NORMAL_PRIORITY)
                                .addClass(this)
                                .addMethod(this)
                                .addString("Parameter " + parmIndex + 1));
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
    }
}
