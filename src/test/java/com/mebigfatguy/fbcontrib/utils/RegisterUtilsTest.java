package com.mebigfatguy.fbcontrib.utils;

import static org.apache.bcel.Constants.ACC_PUBLIC;
import static org.apache.bcel.Constants.ACC_STATIC;
import static org.apache.bcel.Constants.ALOAD;
import static org.apache.bcel.Constants.ALOAD_0;
import static org.apache.bcel.Constants.ALOAD_1;
import static org.apache.bcel.Constants.ALOAD_2;
import static org.apache.bcel.Constants.ALOAD_3;
import static org.apache.bcel.Constants.ASTORE;
import static org.apache.bcel.Constants.ASTORE_0;
import static org.apache.bcel.Constants.ASTORE_1;
import static org.apache.bcel.Constants.ASTORE_2;
import static org.apache.bcel.Constants.ASTORE_3;
import static org.apache.bcel.Constants.DLOAD;
import static org.apache.bcel.Constants.DLOAD_0;
import static org.apache.bcel.Constants.DLOAD_1;
import static org.apache.bcel.Constants.DLOAD_2;
import static org.apache.bcel.Constants.DLOAD_3;
import static org.apache.bcel.Constants.DSTORE;
import static org.apache.bcel.Constants.DSTORE_0;
import static org.apache.bcel.Constants.DSTORE_1;
import static org.apache.bcel.Constants.DSTORE_2;
import static org.apache.bcel.Constants.DSTORE_3;
import static org.apache.bcel.Constants.FLOAD;
import static org.apache.bcel.Constants.FLOAD_0;
import static org.apache.bcel.Constants.FLOAD_1;
import static org.apache.bcel.Constants.FLOAD_2;
import static org.apache.bcel.Constants.FLOAD_3;
import static org.apache.bcel.Constants.FSTORE;
import static org.apache.bcel.Constants.FSTORE_0;
import static org.apache.bcel.Constants.FSTORE_1;
import static org.apache.bcel.Constants.FSTORE_2;
import static org.apache.bcel.Constants.FSTORE_3;
import static org.apache.bcel.Constants.ILOAD;
import static org.apache.bcel.Constants.ILOAD_0;
import static org.apache.bcel.Constants.ILOAD_1;
import static org.apache.bcel.Constants.ILOAD_2;
import static org.apache.bcel.Constants.ILOAD_3;
import static org.apache.bcel.Constants.ISTORE;
import static org.apache.bcel.Constants.ISTORE_0;
import static org.apache.bcel.Constants.ISTORE_1;
import static org.apache.bcel.Constants.ISTORE_2;
import static org.apache.bcel.Constants.ISTORE_3;
import static org.apache.bcel.Constants.LLOAD;
import static org.apache.bcel.Constants.LLOAD_0;
import static org.apache.bcel.Constants.LLOAD_1;
import static org.apache.bcel.Constants.LLOAD_2;
import static org.apache.bcel.Constants.LLOAD_3;
import static org.apache.bcel.Constants.LSTORE;
import static org.apache.bcel.Constants.LSTORE_0;
import static org.apache.bcel.Constants.LSTORE_1;
import static org.apache.bcel.Constants.LSTORE_2;
import static org.apache.bcel.Constants.LSTORE_3;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

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

import edu.umd.cs.findbugs.FindBugs;
import edu.umd.cs.findbugs.visitclass.DismantleBytecode;

public class RegisterUtilsTest {

    private static final int OPERAND = 12345;

    @Mock
    private DismantleBytecode dbc;

    @BeforeSuite
    public void setUpClass() {
        FindBugs.setHome("target/findbugs-3.0.1.jar");
    }

    @BeforeMethod
    public void setUp() {

        MockitoAnnotations.initMocks(this);

        when(dbc.getRegisterOperand()).thenReturn(OPERAND);
    }

    @DataProvider(name = "parameterRegisters")
    Object[][] parameterRegisters() {
        return new Object[][] {
                // access flags, signature, expected registers
                { ACC_PUBLIC | ACC_STATIC, "(JLjava/lang/String;[[I)V", new int[] { 0, 2, 3 } }, { ACC_STATIC, "(Ljava/lang/Object;)Z", new int[] { 0 } },
                { ACC_PUBLIC, "(DJ[D[J)V", new int[] { 1, 3, 5, 6 } }, { 0, "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", new int[] { 1, 2 } }, };
    }

    @DataProvider(name = "reg_ASTORE")
    Object[][] reg_ASTORE() {
        return new Object[][] { { ASTORE, OPERAND }, { ASTORE_0, 0 }, { ASTORE_1, 1 }, { ASTORE_2, 2 }, { ASTORE_3, 3 }, { ASTORE_0 - 1, -1 },
                { ASTORE_3 + 1, -1 }, };
    }

    @DataProvider(name = "reg_ALOAD")
    Object[][] reg_ALOAD() {
        return new Object[][] { { ALOAD_0, 0 }, { ALOAD_1, 1 }, { ALOAD_2, 2 }, { ALOAD_3, 3 }, { ALOAD_0 - 1, -1 }, { ALOAD_3 + 1, -1 }, };
    }

    @DataProvider(name = "regStore")
    Object[][] regStore() {
        return new Object[][] { { ASTORE, OPERAND }, { ISTORE, OPERAND }, { LSTORE, OPERAND }, { FSTORE, OPERAND }, { DSTORE, OPERAND }, { ASTORE_0, 0 },
                { ASTORE_1, 1 }, { ASTORE_2, 2 }, { ASTORE_3, 3 }, { ISTORE_0, 0 }, { ISTORE_1, 1 }, { ISTORE_2, 2 }, { ISTORE_3, 3 }, { LSTORE_0, 0 },
                { LSTORE_1, 1 }, { LSTORE_2, 2 }, { LSTORE_3, 3 }, { FSTORE_0, 0 }, { FSTORE_1, 1 }, { FSTORE_2, 2 }, { FSTORE_3, 3 }, { DSTORE_0, 0 },
                { DSTORE_1, 1 }, { DSTORE_2, 2 }, { DSTORE_3, 3 }, { Integer.MIN_VALUE, -1 }, { Integer.MAX_VALUE, -1 }, };
    }

    @DataProvider(name = "regLoad")
    Object[][] regLoad() {
        return new Object[][] { { ALOAD, OPERAND }, { ILOAD, OPERAND }, { LLOAD, OPERAND }, { FLOAD, OPERAND }, { DLOAD, OPERAND }, { ALOAD_0, 0 },
                { ALOAD_1, 1 }, { ALOAD_2, 2 }, { ALOAD_3, 3 }, { ILOAD_0, 0 }, { ILOAD_1, 1 }, { ILOAD_2, 2 }, { ILOAD_3, 3 }, { LLOAD_0, 0 }, { LLOAD_1, 1 },
                { LLOAD_2, 2 }, { LLOAD_3, 3 }, { FLOAD_0, 0 }, { FLOAD_1, 1 }, { FLOAD_2, 2 }, { FLOAD_3, 3 }, { DLOAD_0, 0 }, { DLOAD_1, 1 }, { DLOAD_2, 2 },
                { DLOAD_3, 3 }, { Integer.MIN_VALUE, -1 }, { Integer.MAX_VALUE, -1 }, };
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

    @Test(dataProvider = "reg_ASTORE")
    public void shouldReturnOffsetWhenSeenASTORE(int seen, int expected) {
        assertEquals(RegisterUtils.getAStoreReg(dbc, seen), expected);
    }

    @Test(dataProvider = "reg_ALOAD")
    public void shouldReturnOffsetWhenSeenALOAD(int seen, int expected) {
        assertEquals(RegisterUtils.getALoadReg(dbc, seen), expected);
    }

    @Test(dataProvider = "regStore")
    public void shouldReturnStoreReg(int seen, int expected) {
        assertEquals(RegisterUtils.getStoreReg(dbc, seen), expected);
    }

    @Test(dataProvider = "regLoad")
    public void shouldReturnLoadReg(int seen, int expected) {
        assertEquals(RegisterUtils.getLoadReg(dbc, seen), expected);
    }

}
