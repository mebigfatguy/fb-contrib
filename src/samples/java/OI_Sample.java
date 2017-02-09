import java.util.Optional;

public class OI_Sample {

    public String useDelayedExecution(Optional<String> o, String a, String b) {

        return o.orElse(String.format("%s boo %s hiss", a, b));
    }

    public String checkingOptionalReference(Optional<String> o) {
        if (o == null) {
            return "";
        }

        return o.get();
    }

    public String useImmediateExecution(Optional<String> o, final String a) {
        return o.orElseGet(() -> a);
    }

    public String fpDelayedOK(Optional<String> o, String a, String b) {

        return o.orElseGet(() -> String.format("%s boo %s hiss", a, b));
    }

    public String fpImmediateOK(Optional<String> o, final String a) {

        return o.orElse(a);
    }

    public String fpStringAppendingIsTooCommon(Optional<String> o, final String a, final String b) {

        return o.orElse(a + b);
    }

}
