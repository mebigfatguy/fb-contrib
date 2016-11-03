
public abstract class FP_Sample {
    public void test1(String a) {
        System.out.println(a);
    }

    public void test2(String a) {
        a = "hello";
        System.out.println(a);
    }

    public abstract void test3(String c);

    public String test4(String a, String b, String c) {
        a = c;
        if (a.equals(b))
            b = a;
        return a + b + c;
    }

    public void testLongDouble(int a, long b, double d, char c) {
    }

}