import org.junit.Assert;

import junit.framework.TestCase;

public class JAO_Sample extends TestCase {

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

    public void test3ArgNP(float foo, int boo) {
        Assert.assertEquals(1.0f, foo, 0.1);
        Assert.assertEquals(20, boo, 0);

    }
}
