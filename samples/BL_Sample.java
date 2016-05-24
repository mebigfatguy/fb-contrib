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

    private List<String> getDetails() {
        return null;
    }
}
