package ex;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class STT_Sample {

    enum Sample {
        Hi, Lo
    };

    Map<String, Object> map = new HashMap<>();

    String val;

    public void testToStringToField() {
        val = Sample.Hi.toString();
    }

    public void testSBWithToStringToField(Date d, Integer i) {
        StringBuilder s = new StringBuilder();
        s.append(d);
        s.append(i);
        val = s.toString();
    }

    public Object testSBToMapField(Date d1, Date d2) {
        map.put("a-v", d1 + ":" + d2);
        return map.get(d1 + "-" + d2);
    }

    public String testParseOfMapResult() {
        String s = (String) map.get("foo");
        int colonPos = s.indexOf(":");
        return s.substring(0, colonPos);
    }

    public void fpTestToStringToFieldSB(String s) {
        val = s + "wow";
    }

    public void fpAllowUUID() {
        val = UUID.randomUUID().toString();
    }
}
