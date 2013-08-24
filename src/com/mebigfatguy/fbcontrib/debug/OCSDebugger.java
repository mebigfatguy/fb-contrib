package com.mebigfatguy.fbcontrib.debug;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Method;

import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;
import edu.umd.cs.findbugs.ba.ClassContext;

public class OCSDebugger extends BytecodeScanningDetector {

    private static final String OCS_OUTPUT_FILE = "fb-contrib.ocs.output";
    private static final String OCS_METHOD_DESC = "fb-contrib.ocs.method";
    
    private static final String OUTPUT_FILE_NAME = System.getProperty(OCS_OUTPUT_FILE);
    private static final String METHOD_DESC = System.getProperty(OCS_METHOD_DESC);
    
    private OpcodeStack stack = new OpcodeStack();
    private PrintWriter pw = null;
    
    public OCSDebugger(BugReporter bugReporter) {
    }
    
    public void visitClassContext(ClassContext classContext) {
        if ((OUTPUT_FILE_NAME != null) && (METHOD_DESC != null))
            super.visitClassContext(classContext);
    }
    
    public void visitCode(Code obj) {
        Method m = getMethod();
        
        String curMethodDesc = getClassContext().getJavaClass().getClassName() + "." + m.getName() + m.getSignature();
        if (curMethodDesc.equals(METHOD_DESC)) {
            try {
                pw = new PrintWriter(new FileWriter(OUTPUT_FILE_NAME));
                stack.resetForMethodEntry(this);

                super.visitCode(obj);
            } catch (IOException e) {
            } finally {
                pw.close();
                pw = null;
            }              
        }
    }
    
    public void sawOpcode(int seen) {
        stack.sawOpcode(this, seen);
        pw.println(String.format("After executing: %-16s at PC: %-5d Stack Size: %-3d", Constants.OPCODE_NAMES[getOpcode()], getPC(), stack.getStackDepth()));
    }
}
