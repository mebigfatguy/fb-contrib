package com.mebigfatguy.fbcontrib.utils;

import edu.umd.cs.findbugs.FindBugs;
import edu.umd.cs.findbugs.visitclass.DismantleBytecode;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import org.apache.bcel.Constants;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.ConstantUtf8;
import org.apache.bcel.classfile.Method;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class RegisterUtilsTest {

    @Mock private DismantleBytecode dbc;

    @BeforeSuite
    public void setUpClass() {
        FindBugs.setHome("target/findbugs-3.0.1.jar");
    }

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

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

    @Test
    public void shouldReturnRegisterOperandWhenSeen_ASTORE() {
        int operand = 12345;
        when(dbc.getRegisterOperand()).thenReturn(operand);

        int result = RegisterUtils.getAStoreReg(dbc, Constants.ASTORE);

        assertEquals(result, operand);
    }

    @Test
    public void shouldReturnOffsetWhenSeen_ASTORE_0To3() {
        assertEquals(RegisterUtils.getAStoreReg(dbc, Constants.ASTORE_0), 0);
        assertEquals(RegisterUtils.getAStoreReg(dbc, Constants.ASTORE_1), 1);
        assertEquals(RegisterUtils.getAStoreReg(dbc, Constants.ASTORE_2), 2);
        assertEquals(RegisterUtils.getAStoreReg(dbc, Constants.ASTORE_3), 3);
    }

    @Test
    public void shouldReturnNegativeWhenNotSeen_ASTORE() {
        assertEquals(RegisterUtils.getAStoreReg(dbc, Constants.ASTORE_0 - 1), -1);
        assertEquals(RegisterUtils.getAStoreReg(dbc, Constants.ASTORE_3 + 1), -1);
    }

}
