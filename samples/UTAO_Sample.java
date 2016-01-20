import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import junit.framework.TestCase;

@SuppressWarnings("deprecation")
public class UTAO_Sample extends TestCase {

    public void testExactDoubles(double d1, double d2) {
        Assert.assertEquals(d1, d2);
        Assert.assertEquals("Still bad", d1, d2);
        Assert.assertEquals(0.1, d1, d2); // Actually good
        Assert.assertEquals("This one is ok", 0.1, d1, d2); // Still good
    }

    public void testTrue(boolean b) {
        Assert.assertEquals(true, b);
    }

    public void testFalse(boolean b) {
        Assert.assertEquals("Wow this is bad", false, b);
    }

    public void testNull(String s) {
        Assert.assertEquals(null, s);
    }

    public void testNotNull(String s) {
        Assert.assertNotEquals(null, s);
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

    public void testUseAssertNotEqualsCrossed(String s, String s2) {
        Assert.assertTrue(!s.equals(s2));
        Assert.assertTrue(s.length() != s.length());
        Assert.assertTrue("this is bad", !s.equals(s2));
        Assert.assertTrue("this is reallly bad", s.length() != s.length());
    }

    public void testUseAssertEqualsCrossed(String s, String s2) {
        Assert.assertFalse(!s.equals(s2));
        Assert.assertFalse(s.length() != s.length());
        Assert.assertFalse("this is bad", !s.equals(s2));
        Assert.assertFalse("this is reallly bad", s.length() != s.length());
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

    @Test(expected = RuntimeException.class)
    public void fpNoAssertsWithJUnitExpects() {
        throw new RuntimeException();
    }

    @Test
    @SuppressWarnings("deprecation")
    public void usingOldClasses(int x) {
        junit.framework.Assert.assertEquals(0, x);
    }
}

class TestNG {
    @org.testng.annotations.Test(enabled = false)
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
    public void testExactDoubles(double d1, double d2) {
        org.testng.Assert.assertEquals(d1, d2, "Don't ever do this!");
    }

    @org.testng.annotations.Test
    public void testNull(String s) {
        org.testng.Assert.assertEquals(s, null);
    }

    @org.testng.annotations.Test
    public void testNotNull(String s) {
        org.testng.Assert.assertNotEquals(s, null);
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
    public void testUseAssertNotEqualsCrossed(String s, String s2) {
        org.testng.Assert.assertTrue(!s.equals(s2));
        org.testng.Assert.assertTrue(s.length() != s.length());
    }

    @org.testng.annotations.Test
    public void testUseAssertEqualsCrossed(String s, String s2) {
        org.testng.Assert.assertFalse(!s.equals(s2));
        org.testng.Assert.assertFalse(s.length() != s.length());
    }

    @org.testng.annotations.Test
    public void test3ArgNP(float foo, int boo) {
        org.testng.Assert.assertEquals(foo, 1.0f, 0.1);
        org.testng.Assert.assertEquals(boo, 20, 0);
    }

    @org.testng.annotations.Test(expectedExceptions = RuntimeException.class, enabled = false)
    public void fpNoAssertsWithNGExpects() {
        throw new RuntimeException();
    }
}

class GitHubIssue94 {
    private Object realObject;

    @Mock
    private Object mockObject;

    @org.testng.annotations.BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        realObject = new Object();
    }

    @org.testng.annotations.Test(enabled = false)
    public void fpShouldNotEqualMockObject() {
        org.testng.Assert.assertNotEquals(realObject, mockObject);
    }

}
