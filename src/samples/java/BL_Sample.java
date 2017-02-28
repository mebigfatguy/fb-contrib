import java.util.List;

public class BL_Sample {

    public String testSimpleBurying() {

        List<String> details = getDetails();
        if (details != null) {
            StringBuilder sb = new StringBuilder();

            for (String d : details) {
                sb.append(d);
            }

            return sb.toString();
        }

        return null;
    }

    public boolean fpNonAbsoluteReturn(Throwable t) {
        if ((t != null) && (t.getCause() != null)) {
            Throwable cause = t.getCause();
            if (cause instanceof java.lang.Error) {
                return t instanceof OutOfMemoryError;
            }
        }
        return false;
    }

    public String fpNonReturnedIfChain(String name) {
        if ("<clinit>".equals(name)) {
            System.out.println("static initializer declared");
        } else {
            System.out.println("not allowed method declared: " + name);
            return "wow: " + name;
        }
        return null;
    }

    public String fpBuryingSwitch(String data) {
        switch (data) {
            case "a":
                return fpBuryingSwitch(data + "fe") + data;
            case "b":
                return fpBuryingSwitch(data + "fi") + data;
            case "c":
                return fpBuryingSwitch(data + "fo") + data;
            case "d":
                return fpBuryingSwitch(data + "fum") + data;
        }
        return "";
    }

    public String fpIfElseReturnChain(Object c) {
        if (c instanceof String) {
            return "S" + c.getClass().getName();
        } else if (c instanceof Integer) {
            return "I" + c.getClass().getName();
        } else if (c instanceof Double) {
            return "D" + c.getClass().getName();
        }

        return null;
    }

    public String fpIfElseTailReturnChain(Object c) {
        String s;
        if (c instanceof String) {
            s = "S" + c.getClass().getName();
        } else if (c instanceof Integer) {
            s = "I" + c.getClass().getName();
        } else if (c instanceof Double) {
            return "D" + c.getClass().getName();
        }

        return null;
    }

    public String fpSwitchInReturningIfChain(String s, int t) {
        if (s.equals("foo")) {
            return s + "bar";
        } else if (s.equals("bar")) {
            return s + "baz";
        } else if (s.equals("baz")) {
            switch (t) {
                case 0:
                    for (int i = 0; i < t; i++) {
                        s += "0";
                    }
                    return s;

                case 1:
                    for (int i = 0; i < t; i++) {
                        s += "1";
                    }
                    return s;

                default:
                    for (int i = 0; i < t; i++) {
                        s += "1";
                    }
                    return s;
            }
        }

        return null;

    }

    private List<String> getDetails() {
        return null;
    }
}
