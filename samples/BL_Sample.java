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

    private List<String> getDetails() {
        return null;
    }
}
