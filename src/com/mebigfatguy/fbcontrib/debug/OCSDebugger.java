package com.mebigfatguy.fbcontrib.debug;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import org.apache.bcel.classfile.Code;

import edu.umd.cs.findbugs.BugReporter;
import edu.umd.cs.findbugs.BytecodeScanningDetector;
import edu.umd.cs.findbugs.OpcodeStack;

public class OCSDebugger extends BytecodeScanningDetector {

    OpcodeStack stack = new OpcodeStack();
    PrintWriter pw = null;
    
    public OCSDebugger(BugReporter bugReporter) {
    }
    
    public void visitCode(Code obj) {
        stack.resetForMethodEntry(this);
        if (getClassContext().getJavaClass().getClassName().contains("LeveledManifest") && getMethod().getName().equals("getAllLevelSize")) {
            try {
                pw = new PrintWriter(new FileWriter("/home/dave/Desktop/OCS.txt"));
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
        pw.println("After Opcode: " + getPC() + " Stack Size is: " + stack.getStackDepth());
    }
}
