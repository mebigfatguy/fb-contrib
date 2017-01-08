/*
 * fb-contrib - Auxiliary detectors for Java programs
 * Copyright (C) 2005-2017 ThrawnCA
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
package com.mebigfatguy.fbcontrib.utils;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class SignatureBuilderTest {

    @Test
    public void shouldDefaultToVoidReturnWithNoParameters() {
        assertEquals(new SignatureBuilder().build(), "()V");
    }

    @Test
    public void shouldConvertClassnamesToSignatures() {
        assertEquals(new SignatureBuilder().withMethodName("classNames").withParamTypes("java.lang.String").withReturnType("java/util/Collection").build(),
                "classNames(Ljava/lang/String;)Ljava/util/Collection;");
    }

    @Test
    public void shouldPreserveSignatures() {
        assertEquals(new SignatureBuilder().withMethodName("signatures").withParamTypes("Ljava/lang/Object;").withReturnType("Ljava/util/Date;").build(),
                "signatures(Ljava/lang/Object;)Ljava/util/Date;");
    }

    @Test
    public void shouldOverrideExistingParamTypes() {
        assertEquals(new SignatureBuilder().withMethodName("overrideParams").withParamTypes("java/lang/Object")
                .withParamTypes("java/lang/String", "java/lang/Boolean").build(), "overrideParams(Ljava/lang/String;Ljava/lang/Boolean;)V");
    }

    @Test
    public void shouldAcceptPrimitiveTypes() {
        assertEquals(new SignatureBuilder().withMethodName("primitives").withParamTypes("B", "S", "C", "I", "J", "java.lang.Object", "Z", "F")
                .withReturnType("D").build(), "primitives(BSCIJLjava/lang/Object;ZF)D");
    }

    @Test
    public void shouldAcceptClasses() {
        assertEquals(new SignatureBuilder().withMethodName("classes").withParamTypes(String.class, Object.class).withReturnType(Integer.class).build(),
                "classes(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Integer;");
    }

    @Test
    public void shouldBlankReturnTypeWhenNeeded() {
        assertEquals(new SignatureBuilder().withMethodName("noReturn").withParamTypes("java.lang.Object").withoutReturnType().build(),
                "noReturn(Ljava/lang/Object;)");
    }

}
