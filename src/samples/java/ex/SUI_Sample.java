package ex;

import java.util.Set;

public class SUI_Sample {

    public void testAddAfterContains(Set<String> ss) {
        if (!ss.contains("foo")) {
            ss.add("foo");
        }
    }

}
