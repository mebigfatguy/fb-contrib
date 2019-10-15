package ex;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CCI_Sample {

    private Map<String, Set<String>> map = new ConcurrentHashMap<>();

    public void update(String key, String value) {

        Set<String> values = map.get(key);

        if (values == null) {
            values = Collections.<String>newSetFromMap(new ConcurrentHashMap<String, Boolean>());
            map.put(key, values);
        }

        values.add(value);
    }
}
