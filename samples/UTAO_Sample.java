import org.junit.Assert;
import org.junit.Test;

import junit.framework.TestCase;

public class UTAO_Sample extends TestCase {

    public void testExactDoubles(double d1, double d2) {
        Assert.assertEquals(d1, d2);
    }

    public void testTrue(boolean b) {
        Assert.assertEquals(true, b);
    }

    public void testFalse(boolean b) {
        Assert.assertEquals("Wow this is bad", false, b);
    }

    public void testWrongOrder(int i) {
        Assert.assertEquals(i, 10);
    }

    public void testAutoBoxNotNull(int i) {
        Assert.assertNotNull(i);
        Assert.assertNotNull(i == 3);
    }

    public void testAssertUsed(String s) {
        assert s != null;
    }

    public void testUseAssertEquals(String s, String s2) {
        Assert.assertTrue(s.equals(s2));
        Assert.assertTrue(s.length() == s.length());
    }
    
    public void testUseAssertNotEquals(String s, String s2) {
    	Assert.assertFalse(s.equals(s2));
        Assert.assertFalse(s.length() == s.length());
        Assert.assertFalse("this is bad", s.equals(s2));
        Assert.assertFalse("this is reallly bad", s.length() == s.length());
    }

    public void test3ArgNP(float foo, int boo) {
        Assert.assertEquals(1.0f, foo, 0.1);
        Assert.assertEquals(20, boo, 0);

    }
}

class New {
    @Test
    public void nada() {
    }

    @Test
    public void aha() {
        check("hello", "world");
    }

    private void check(String a, String b) {
        Assert.assertEquals(a, b);
    }
    
    @Test(expected=RuntimeException.class)
    public void fpNoAssertsWithJUnitExpects() {
        throw new RuntimeException();
    }
    
    @Test
    public void usingOldClasses(int x) {
        junit.framework.Assert.assertEquals(0,  x);
    }
}

class TestNG {
    @org.testng.annotations.Test
    public void nada() {
    }

    @org.testng.annotations.Test
    public void testTrue(boolean b) {
        org.testng.Assert.assertEquals(b, true);
    }

    @org.testng.annotations.Test
    public void testFalse(boolean b) {
        org.testng.Assert.assertEquals(b, false, "Wow this is bad");
    }

    @org.testng.annotations.Test
    public void testWrongOrder(int i) {
        org.testng.Assert.assertEquals(10, i);
    }

    @org.testng.annotations.Test
    public void testAutoBoxNotNull(int i) {
        org.testng.Assert.assertNotNull(i);
        org.testng.Assert.assertNotNull(i == 3);
    }

    @org.testng.annotations.Test
    public void testAssertUsed(String s) {
        assert s != null;
    }

    @org.testng.annotations.Test
    public void testUseAssertEquals(String s, String s2) {
        org.testng.Assert.assertTrue(s.equals(s2));
        org.testng.Assert.assertTrue(s.length() == s.length());
    }
    
    @org.testng.annotations.Test
    public void testUseAssertNotEquals(String s, String s2) {
    	org.testng.Assert.assertFalse(s.equals(s2));
    	org.testng.Assert.assertFalse(s.length() == s.length());
    }

    @org.testng.annotations.Test
    public void test3ArgNP(float foo, int boo) {
        Assert.assertEquals(foo, 1.0f, 0.1);
        Assert.assertEquals(boo, 20, 0);
    }
    
    @org.testng.annotations.Test(expectedExceptions=RuntimeException.class)
    public void fpNoAssertsWithNGExpects() {
        throw new RuntimeException();
    }
}
