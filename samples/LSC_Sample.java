
public class LSC_Sample {
    public static final String CONSTANT_VAL_STRING = "GoodBye";
    private static final String CONSTANT_VAL_STRING2 = "GoodBye2";

    enum Planets {
        EARTH, MARS, VENUS, JUPITER;
    }

    public boolean test1(String s) {
        // tag
        return s.equals("Hello");
    }

    public boolean test2(String s) {
        // no tag
        return "Hello".equals(s);
    }

    public boolean test3(String s1, String s2) {
        // no tag
        return s1.equals(s2);
    }

    public int test4(String s) {
        // tag
        return s.compareTo("Hello");
    }

    public int test5(String s) {
        // no tag
        return "Hello".compareTo(s);
    }

    public int test6(String s) {
        // tag
        return s.compareTo(CONSTANT_VAL_STRING);
    }

    public int test7(String s) {
        // no tag
        return CONSTANT_VAL_STRING.compareTo(s);
    }

    public int test8(String s) {
        // tag
        return s.compareTo(CONSTANT_VAL_STRING2);
    }

    public int test9(String s) {
        // no tag
        return CONSTANT_VAL_STRING2.compareTo(s);
    }

    public static int test10(String s) {
        switch (s) {
        case "Hello":
            return 1;
        case CONSTANT_VAL_STRING:
            return 2;

        }

        switch (s) { // two in a row to check the correct switch offsets
        case "Hello2":
            return 1;
        case CONSTANT_VAL_STRING + "2":
            return 2;
        default:
            return 3;
        }
    }

    public static int test11(String s, String s2) {
        // no tag
        switch (s) {
        case "Switch1":
            return 1;
        case "switch2":
            return 2;
        case "switch3":
            // tag
            if (s2.equalsIgnoreCase("Foo6")) {
                return 5;
            }
        }

        // tag
        if (s.equals("Foo7")) {
            return 3;
        }
        System.out.println(s);
        return 4;

    }

    public static int test12(int n, String s2) {
        switch (n) { // this is probably a table lookup
        case 1:
            return 1;
        case 2:
            return 2;
        case 3:
        case 4:
            // tag
            if (s2.equalsIgnoreCase("Foo6")) {
                return 5;
            }
        }

        // tag
        if (s2.equals("Foo7")) {
            return 3;
        }
        System.out.println(s2);
        return 4;

    }

    public static int test13(Planets p, String s2) {
        switch (p) {
        case EARTH:
            return 1;
        case MARS:
            return 2;
        case JUPITER:
            // tag
            if (s2.equalsIgnoreCase("Foo6")) {
                return 5;
            }
        default:
            break;
        }

        // tag
        if (s2.equals("Foo7")) {
            return 3;
        }
        System.out.println(s2);
        return 4;

    }

    /*
     * Tried really hard to get a false negative, by manipulating this switch to
     * look like a string switch. Couldn't make it happen.
     */
    public static int test14(String s2) {
        switch (s2.hashCode()) {
        case 1:
            return 1;
        case 2:
            return 2;
        case 3:
        case 99: // forces this to also be a lookup table, like the strings
            // tag
            if (s2.equalsIgnoreCase("Foo6")) {
                return 5;
            }
        }

        // tag
        if (s2.equals("Foo7")) {
            return 3;
        }
        System.out.println(s2);
        return 4;

    }

}