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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class SignatureUtilsTest {

    @DataProvider(name = "namesToSignatures")
    public Object[][] namesToSignatures() {
        String sigString = "Ljava/lang/String;";
        return new Object[][] { { "java.lang.String", sigString }, { "java/lang/String", sigString }, { sigString, sigString }, { "B", "B" }, { "S", "S" },
                { "C", "C" }, { "I", "I" }, { "J", "J" }, { "F", "F" }, { "D", "D" }, { "Z", "Z" }, { "", "" }, { null, null }, };
    }

    @DataProvider(name = "signaturesToSlashedNames")
    public Object[][] signaturesToSlashedNames() {
        String slashedString = "java/lang/String";
        return new Object[][] { { "Ljava/lang/String;", slashedString }, { slashedString, slashedString }, { "B", "B" }, { "S", "S" }, { "C", "C" },
                { "I", "I" }, { "J", "J" }, { "F", "F" }, { "D", "D" }, { "Z", "Z" }, { "", "" }, { null, null }, };
    }

    @DataProvider(name = "signaturesToDottedNames")
    public Object[][] signaturesToDottedNames() {
        String dottedString = "java.lang.String";
        return new Object[][] { { "Ljava/lang/String;", dottedString }, { "java/lang/String", dottedString }, { dottedString, dottedString }, { "B", "B" },
                { "S", "S" }, { "C", "C" }, { "I", "I" }, { "J", "J" }, { "F", "F" }, { "D", "D" }, { "Z", "Z" }, { "", "" }, };
    }

    @DataProvider(name = "namesToArrays")
    public Object[][] namesToArrays() {
        String sigStringArray = "[Ljava/lang/String;";
        return new Object[][] { { "java.lang.String", sigStringArray }, { "java/lang/String", sigStringArray }, { sigStringArray, "[[Ljava/lang/String;" },
                { "B", "[B" }, { "S", "[S" }, { "C", "[C" }, { "I", "[I" }, { "J", "[J" }, { "F", "[F" }, { "D", "[D" }, { "Z", "[Z" }, { "", "" },
                { null, null }, };
    }

    @Test
    public void shouldGetParameterSlotsAndSignaturesForInstanceMethod() {
        Map<Integer, String> expected = new HashMap<>(2);
        expected.put(1, "I");
        expected.put(2, "Ljava/lang/Object;");
        assertEquals(SignatureUtils.getParameterSlotAndSignatures(false, "add(ILjava/lang/Object;)Ljava/lang/Object;"), expected);
    }

    @Test
    public void shouldGetParameterSlotsAndSignaturesForStaticMethod() {
        Map<Integer, String> expected = new HashMap<>(2);
        expected.put(0, "I");
        expected.put(1, "Ljava/lang/Object;");
        assertEquals(SignatureUtils.getParameterSlotAndSignatures(true, "add(ILjava/lang/Object;)Ljava/lang/Object;"), expected);
    }

    @Test
    public void shouldGetParameterSignatures() {
        assertEquals(SignatureUtils.getParameterSignatures("add(ILjava/lang/Object;)Ljava/lang/Object;"), Arrays.asList("I", "Ljava/lang/Object;"));
    }

    @Test
    public void shouldCountParameterSignatures() {
        assertEquals(SignatureUtils.getNumParameters("add(ILjava/lang/Object;)Ljava/lang/Object;"), 2);
    }

    @Test
    public void shouldIgnoreEclipseParameterSignatures() {
        assertEquals(SignatureUtils.getParameterSignatures("wonky(!Ljava/lang/Object;++)Ljava/lang/Object;"), Arrays.asList("Ljava/lang/Object;"));
    }

    @Test(dataProvider = "namesToSignatures")
    public void shouldConvertClassnamesToSignatures(String input, String expected) {
        assertEquals(SignatureUtils.classToSignature(input), expected);
    }

    @Test(dataProvider = "signaturesToSlashedNames")
    public void shouldConvertSignaturesToSlashedClassNames(String input, String expected) {
        assertEquals(SignatureUtils.trimSignature(input), expected);
    }

    @Test(dataProvider = "signaturesToDottedNames")
    public void shouldConvertSignaturesToDottedClassNames(String input, String expected) {
        assertEquals(SignatureUtils.stripSignature(input), expected);
    }

    @Test(dataProvider = "namesToArrays")
    public void shouldConvertClassnamesToArrays(String input, String expected) {
        assertEquals(SignatureUtils.toArraySignature(input), expected);
    }

}
