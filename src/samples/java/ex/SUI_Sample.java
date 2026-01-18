package ex;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SUI_Sample {

    private static final Set<Integer> fib = new HashSet<>();
    private static final Set<Integer> fpSet;
    private static final Set<String> nuttin = Collections.emptySet();

    static {
        fib.add(1);
        fib.add(2);
        fib.add(3);
        fib.add(5);
        fib.add(8);
        fib.add(13);
        fib.add(21);
        fib.add(34);

        Set<Integer> fp = new HashSet<>();
        fp.add(1);
        fpSet = Collections.unmodifiableSet(fp);
    }

    Map<String, String> fieldMap = new HashMap<>();

    public Set<Integer> getFib() {
        return fib;
    }

    public Set<Integer> fpSet() {
        return fpSet;
    }

    public Set<String> fpGetNuttin() {
        return nuttin;
    }

    public void testAddAfterContains(Set<String> ss) {
        if (!ss.contains("foo")) {
            ss.add("foo");
        }
    }

    public void testFpAddAfterContains(Set<String> ss) {
        if (ss.contains("foo")) {
            ss.remove("foo");
        }
        ss.add("foo");
    }

    public void testremoveAfterContains(Set<String> ss) {
        if (ss.contains("foo")) {
            ss.remove("foo");
        }
    }

}
