
public class CBC_Sample {

    private static final Integer ONE = Integer.valueOf(1);
    private static final Integer TWO = Integer.valueOf(2);
    private static final Integer THREE = Integer.valueOf(3);

    enum Giant {
        Fee, Fi, Fo, Fum
    };

    public void testCBCStrings(String x) {
        if (x.equals("foo") || x.equals("bar") || x.equals("baz")) {
            System.out.println("yup");
        }
    }

    public void testCBCClass(Class<?> x) {
        if (x.equals(String.class) || x.equals(Integer.class) || x.equals(Long.class)) {
            System.out.println("yup");
        }
    }

    public void testCBCEnum(Giant x) {
        if (x.equals(Giant.Fee) || x.equals(Giant.Fi) || x.equals(Giant.Fo) || x.equals(Giant.Fum)) {
            System.out.println("yup");
        }
    }

    public void testCBCEnumDoubleEquals(Giant x) {
        if ((x == Giant.Fee) || (x == Giant.Fi) || (x == Giant.Fo) || (x == Giant.Fum)) {
            System.out.println("yup");
        }
    }

    public void testCBCInteger(Integer x) {
        if (x.equals(ONE) || x.equals(TWO) || x.equals(THREE)) {
            System.out.println("yup");
        }
    }

    public void testCBCPrimitiveInt(int x) {
        if ((x == 1) || (x == 2) || (x == 3)) {
            System.out.println("yup");
        }
    }
}
