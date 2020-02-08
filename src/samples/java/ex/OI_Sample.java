package ex;

import java.util.Date;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

public class OI_Sample {

    public String useDelayedExecution(Optional<String> o, String a, String b) {

        return o.orElse(String.format("%s boo %s hiss", a, b));
    }

    public double useDelayedExecutionWithDouble(OptionalDouble o) {

        return o.orElse(Math.pow(Math.PI, Math.exp(Math.E)));
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

    public int useImmediateExecutionWithInt(OptionalInt o) {
        return o.orElseGet(() -> 0);
    }

    public Optional<Double> useWrongOptional(Optional<Double> d) {

        return Optional.of(3.14);
    }

    public Date orElseGetNull(Optional<Date> o) {
        return o.orElseGet(null);
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

    public Long fpBoxingIsTooCommon(Optional<Long> o) {
        return o.orElse(0L);
    }

    private Optional<String> get(String name) {
        return Optional.of(null);
    }

    public String get(String parameterName, Supplier<String> defaultValueSupplier) {
        return this.<String>get(parameterName).orElseGet(defaultValueSupplier);
    }
}
