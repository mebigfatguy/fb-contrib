
public class LSC_Sample {
	public static final String CONSTANT_VAL_STRING = "GoodBye";
	private static final String CONSTANT_VAL_STRING2 = "GoodBye2";
	
    public boolean test1(String s) {
        return s.equals("Hello");
    }

    public boolean test2(String s) {
        return "Hello".equals(s);
    }

    public boolean test3(String s1, String s2) {
        return s1.equals(s2);
    }

    public int test4(String s) {
        return s.compareTo("Hello");
    }

    public int test5(String s) {
        return "Hello".compareTo(s);
    }
    
    public int test6(String s) {
        return s.compareTo(CONSTANT_VAL_STRING);
    }

    public int test7(String s) {
        return CONSTANT_VAL_STRING.compareTo(s);
    }
    
    public int test8(String s) {
        return s.compareTo(CONSTANT_VAL_STRING2);
    }

    public int test9(String s) {
        return CONSTANT_VAL_STRING2.compareTo(s);
    }
}