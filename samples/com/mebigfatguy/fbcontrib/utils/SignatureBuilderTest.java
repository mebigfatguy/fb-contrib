package com.mebigfatguy.fbcontrib.utils;

import org.testng.annotations.*;

import static org.testng.Assert.*;

public class SignatureBuilderTest {

    @Test
    public void shouldDefaultToVoidReturnWithNoParameters() {
        assertEquals(new SignatureBuilder().build(), "()V");
    }

    @Test
    public void shouldConvertClassnamesToSignatures() {
        assertEquals(new SignatureBuilder().withMethodName("classNames").withParamTypes("java.lang.String").withReturnType("java/util/Collection").build(), "classNames(Ljava/lang/String;)Ljava/util/Collection;");
    }

    @Test
    public void shouldPreserveSignatures() {
        assertEquals(new SignatureBuilder().withMethodName("signatures").withParamTypes("Ljava/lang/Object;").withReturnType("Ljava/util/Date;").build(), "signatures(Ljava/lang/Object;)Ljava/util/Date;");
    }

    @Test
    public void shouldOverrideExistingParamTypes() {
        assertEquals(new SignatureBuilder().withMethodName("overrideParams").withParamTypes("java/lang/Object").withParamTypes("java/lang/String", "java/lang/Boolean").build(), "overrideParams(Ljava/lang/String;Ljava/lang/Boolean;)V");
    }

    @Test
    public void shouldAcceptPrimitiveTypes() {
        assertEquals(new SignatureBuilder().withMethodName("primitives").withParamTypes("B", "S", "C", "I", "J", "java.lang.Object", "Z", "F").withReturnType("D").build(), "primitives(BSCIJLjava/lang/Object;ZF)D");
    }

    @Test
    public void shouldBlankReturnTypeWhenNeeded() {
        assertEquals(new SignatureBuilder().withMethodName("noReturn").withParamTypes("java.lang.Object").withoutReturnType().build(), "noReturn(Ljava/lang/Object;)");
    }

}
