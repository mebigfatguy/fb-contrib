package ex;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class MUI_Sample {

    Map<String, String> fieldMap = new HashMap<>();

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

    public String getAValue() {
        return MUI_Sample.class.getName();
    }

}
