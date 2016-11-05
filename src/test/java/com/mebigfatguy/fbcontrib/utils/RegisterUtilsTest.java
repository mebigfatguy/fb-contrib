package com.mebigfatguy.fbcontrib.utils;

import static org.testng.Assert.assertEquals;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.ConstantUtf8;
import org.apache.bcel.classfile.Method;
import org.testng.annotations.Test;

public class RegisterUtilsTest {

    @Test
    public void shouldGetStaticParameterRegisters() {
        int[] expected = { 0, 2, 3 };
        Method method = new Method();
        method.setAccessFlags(Constants.ACC_PUBLIC | Constants.ACC_STATIC);

        Constant[] cnst = new Constant[] { new ConstantUtf8("(JLjava/lang/String;[[I)V") };
        ConstantPool cp = new ConstantPool(cnst) {

            @Override
            protected Object clone() throws CloneNotSupportedException {
                throw new CloneNotSupportedException();
            }

        };
        method.setConstantPool(cp);
        method.setSignatureIndex(0);

        int[] regs = RegisterUtils.getParameterRegisters(method);

        assertEquals(expected, regs);
    }

    @Test
    public void shouldGetInstanceParameterRegisters() {
        int[] expected = { 1, 3, 5, 6 };
        Method method = new Method();
        method.setAccessFlags(Constants.ACC_PUBLIC);

        Constant[] cnst = new Constant[] { new ConstantUtf8("(DJ[D[J)V") };
        ConstantPool cp = new ConstantPool(cnst) {

            @Override
            protected Object clone() throws CloneNotSupportedException {
                throw new CloneNotSupportedException();
            }

        };
        method.setConstantPool(cp);
        method.setSignatureIndex(0);

        int[] regs = RegisterUtils.getParameterRegisters(method);

        assertEquals(expected, regs);

    }
}
