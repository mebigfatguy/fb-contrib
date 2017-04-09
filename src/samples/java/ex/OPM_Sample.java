package ex;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class OPM_Sample extends OPMSuper implements Comparator<String> {

    public void testNormalMethodCouldBePrivate() {
        someNormalMethod();
    }

    @Override
    public int compare(String s1, String s2) {
        return s2.compareTo(s1);
    }

    public int testFPGenericDerivation() {
        return compare("Hello", "World");
    }

    public void someNormalMethod() {
        List<String> l = getFoo(new ArrayList<String>());
    }

    @Override
    public List<String> getFoo(List<String> l) {
        return l;
    }

    public void fpUncalledDontReport() {
        fpHasRTAnnotation();
    }

    @RT
    public void fpHasRTAnnotation() {
    }

    public void setFPFoo(int x) {
    }

    public int getFPFoo() {
        return 0;
    }

    public void doIt() {
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(Writer::fpFlush, 1L, 1L, TimeUnit.SECONDS);
    }
}

abstract class OPMSuper {
    public abstract List<String> getFoo(List<String> l);
}

@Retention(RetentionPolicy.RUNTIME)
@interface RT {

}

enum FPEnumValueOf {

    What, Where;

    public static void fpWithValueOf() {
        FPEnumValueOf f = FPEnumValueOf.valueOf(String.valueOf("What"));
    }
}

class Writer {
    public static void fpFlush() {
    }

    public void close() {
        fpFlush();
    }
}
