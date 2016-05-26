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

    private List<String> getDetails() {
        return null;
    }
}
