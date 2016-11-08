package com.mebigfatguy.fbcontrib.utils;

import static org.testng.Assert.assertEquals;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.ConstantUtf8;
import org.apache.bcel.classfile.Method;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class RegisterUtilsTest {

    @DataProvider(name = "parameterRegisters")
    Object[][] parameterRegisters() {
        return new Object[][] {
            // access flags, signature, expected registers
            {Constants.ACC_PUBLIC | Constants.ACC_STATIC, "(JLjava/lang/String;[[I)V", new int[] { 0, 2, 3 }},
            {Constants.ACC_STATIC, "(Ljava/lang/Object;)Z", new int[] { 0 }},
            {Constants.ACC_PUBLIC, "(DJ[D[J)V", new int[] { 1, 3, 5, 6 }},
            {0, "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", new int[] { 1, 2 }},
        };
    }

    @Test(dataProvider = "parameterRegisters")
    public void shouldGetParameterRegisters(int accessFlags, String signature, int[] expected) {
        Method method = new Method();
        method.setAccessFlags(accessFlags);

        Constant[] cnst = new Constant[] { new ConstantUtf8(signature) };
        ConstantPool cp = new ConstantPool(cnst) {

            @Override
            protected Object clone() throws CloneNotSupportedException {
                throw new CloneNotSupportedException();
            }

        };
        method.setConstantPool(cp);
        method.setSignatureIndex(0);

        int[] regs = RegisterUtils.getParameterRegisters(method);

        assertEquals(regs, expected);
    }

}
