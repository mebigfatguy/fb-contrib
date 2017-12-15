package ex;

import java.util.HashMap;
import java.util.Map;

public class MUP_Sample {

    public String testGetAfterContainsKey() {
        Map<String, String> m = new HashMap<>();

        if (m.containsKey("Foo")) {
            String v = m.get("Foo");
            return v + "Bar";
        } else {
            return "Bar";
        }
    }
}
