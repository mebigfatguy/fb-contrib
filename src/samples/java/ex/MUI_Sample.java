package ex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MUI_Sample {

    static Map<String, Set<String>> concMap = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> firingOrder = new HashMap<>();
    private static final Map<Integer, Integer> fpMap;

    static {
        firingOrder.put(1, 8);
        firingOrder.put(8, 4);
        firingOrder.put(4, 3);
        firingOrder.put(3, 6);
        firingOrder.put(6, 5);
        firingOrder.put(5, 7);
        firingOrder.put(7, 2);
        firingOrder.put(2, 1);

        Map<Integer, Integer> fp = new HashMap<>();
        fp.put(1, 1);
        fpMap = Collections.unmodifiableMap(fp);
    }

    Map<String, String> fieldMap = new HashMap<>();

    public Map<Integer, Integer> getFiringOrder() {
        return firingOrder;
    }

    public Map<Integer, Integer> fpMap() {
        return fpMap;
    }

    public String testGetAfterContainsKeyLocal() {
        Map<String, String> localMap = new HashMap<>();

        if (localMap.containsKey("Foo")) {
            String v = localMap.get("Foo");
            return v + "Bar";
        } else {
            return "Bar";
        }
    }

    public String testGetAfterContainsKeyWithReg() {
        Map<Date, String> localMap = new HashMap<>();
        Date d = new Date();

        if (localMap.containsKey(d)) {
            String v = localMap.get(d);
            return v + "Bar";
        } else {
            return "Bar";
        }
    }

    public String testGetAfterContainsKeyField() {

        if (fieldMap.containsKey("Foo")) {
            String v = fieldMap.get("Foo");
            return v + "Bar";
        } else {
            return "Bar";
        }
    }

    public String testGetAfterContainsKeyFromMethodCall() {

        if (fieldMap.containsKey(getAValue())) {
            String v = fieldMap.get(getAValue());
            return v + "Bar";
        } else {
            return "Bar";
        }
    }

    public String testRemoveAfterGetLocal(Map<String, String> ss) {
        String s = ss.get("foo");
        ss.remove("foo");
        return s;
    }

    public String testRemoveAfterGetField() {
        String s = fieldMap.get("foo");
        fieldMap.remove("foo");
        return s;
    }

    public boolean testKeySetForNull(Map<String, String> s) {
        return s.keySet() != null;
    }

    public void testUseContainsKey(Map m) {
        if (m.keySet().contains("Foo")) {
            System.out.println("Yup");
        }
    }

    public List<String> testKeySetSize(Map<String, String> m) {
        return new ArrayList<>(m.keySet().size());
    }

    public List<String> testEntrySetSize(Map<String, String> m) {
        return new ArrayList<>(m.entrySet().size());
    }

    public List<String> testValuesSize(Map<String, String> m) {
        return new ArrayList<>(m.values().size());
    }

    public void testConcurrentAccess(String k, String v) {

        Set<String> s = concMap.get(k);
        if (s == null) {
            s = new HashSet<>();
            concMap.put(k, s);
        }
        s.add(v);
    }

    public String getAValue() {
        return MUI_Sample.class.getName();
    }

}
