package com.mebigfatguy.fbcontrib.utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.testng.annotations.*;

import static org.testng.Assert.*;

public class SignatureUtilsTest {

    @DataProvider(name = "namesToSignatures")
    public Object[][] namesToSignatures() {
        String sigString = "Ljava/lang/String;";
        return new Object[][] {
            {"java.lang.String", sigString},
            {"java/lang/String", sigString},
            {sigString, sigString},
            {"B", "B"},
            {"S", "S"},
            {"C", "C"},
            {"I", "I"},
            {"J", "J"},
            {"F", "F"},
            {"D", "D"},
            {"Z", "Z"},
            {"", ""},
            {null, null},
        };
    }

    @DataProvider(name = "signaturesToSlashedNames")
    public Object[][] signaturesToSlashedNames() {
        String slashedString = "java/lang/String";
        return new Object[][] {
            {"Ljava/lang/String;", slashedString},
            {slashedString, slashedString},
            {"B", "B"},
            {"S", "S"},
            {"C", "C"},
            {"I", "I"},
            {"J", "J"},
            {"F", "F"},
            {"D", "D"},
            {"Z", "Z"},
            {"", ""},
            {null, null},
        };
    }

    @DataProvider(name = "signaturesToDottedNames")
    public Object[][] signaturesToDottedNames() {
        String dottedString = "java.lang.String";
        return new Object[][] {
            {"Ljava/lang/String;", dottedString},
            {"java/lang/String", dottedString},
            {dottedString, dottedString},
            {"B", "B"},
            {"S", "S"},
            {"C", "C"},
            {"I", "I"},
            {"J", "J"},
            {"F", "F"},
            {"D", "D"},
            {"Z", "Z"},
            {"", ""},
        };
    }

    @DataProvider(name = "namesToArrays")
    public Object[][] namesToArrays() {
        String sigStringArray = "[Ljava/lang/String;";
        return new Object[][] {
            {"java.lang.String", sigStringArray},
            {"java/lang/String", sigStringArray},
            {sigStringArray, "[[Ljava/lang/String;"},
            {"B", "[B"},
            {"S", "[S"},
            {"C", "[C"},
            {"I", "[I"},
            {"J", "[J"},
            {"F", "[F"},
            {"D", "[D"},
            {"Z", "[Z"},
            {"", ""},
            {null, null},
        };
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
