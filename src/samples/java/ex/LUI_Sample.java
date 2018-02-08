package ex;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LUI_Sample {

    public List<String> testUseSingleton() {
        return Arrays.asList("foo");
    }

    public Set<String> testAddVsAddAll(String one) {
        Set<String> s = new HashSet<>();
        if ("foo".equals(one)) {
            s.addAll(Collections.singletonList(one));
        } else {
            s.addAll(Arrays.asList(one));
        }

        return s;
    }

    public List<String> fpWithArray() {
        return Arrays.asList("foo", "bar");
    }
}
