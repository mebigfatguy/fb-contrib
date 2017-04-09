package ex;
import java.util.HashMap;
import java.util.Map;

public class DMC_Sample {

    private static Map<String, Boolean> STUFF = new HashMap<>();

    private static Map<String, String> flim = new HashMap<>();
    private static Map<String, String> flam = new HashMap<>();

    static {
        STUFF.put("this", Boolean.TRUE);
        STUFF.put("that", Boolean.FALSE);
        STUFF.put("the", Boolean.TRUE);
        STUFF.put("other", Boolean.FALSE);
        STUFF.put("thing", Boolean.TRUE);
    }

    public String getInfo(boolean v) {

        String data = "";

        for (Map.Entry<String, Boolean> e : STUFF.entrySet()) {
            if (e.getValue().booleanValue() == v) {
                data += e.getKey();
            }
        }

        return data;
    }

    public static Map<String, String> fpTernaryGetMapField(boolean b) {
        return b ? flim : flam;
    }
}
