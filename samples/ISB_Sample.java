import java.util.Map;
import java.util.Set;

@SuppressWarnings("all")
public class ISB_Sample {
    public String testISB1(final String a, String b) {
        StringBuffer sb = new StringBuffer();
        sb.append("{" + a + ",");
        sb.append(b + "}");
        return sb.toString();
    }

    public String testISB2(Map<Integer, String> l) {
        StringBuffer sb = new StringBuffer();

        for (Map.Entry<Integer, String> e : l.entrySet()) {
            sb.append(e.getKey() + e.getValue());
        }

        return sb.toString();
    }

    public String testISB3(String s) {
        StringBuffer sb = new StringBuffer(s);
        return sb.toString();
    }

    public String testISB4(int len) {
        StringBuffer sb = new StringBuffer(len);
        return sb.toString();
    }

    public String testISB5(String a, String b, String c, String d) {
        return a + "false positive" + b + ((c == null) ? "" : (a + d));
    }

    public String testISB6(int a) {
        return "" + a;
    }

    public String testISB7(String s) {
        StringBuffer sb = new StringBuffer();
        sb.append("foo");
        sb.append(sb.toString());
        return sb.toString();
    }

    public String testISB8() {
        String foo = "foo";
        foo += " bar";

        return foo;
    }

    public String testFPISB9(int x) {
        StringBuffer sb = new StringBuffer();
        sb.append(x);
        sb.append("hello");
        return sb.toString();
    }

    public String testTOStringAppending(Map<String,Set<Integer>> map) {
        StringBuilder sb = new StringBuilder();
        //no tag ISB_TOSTRING_APPENDING, because this can never be null.  Some styles like this.toString()
        sb.append(this.toString());
        //tag ISB_TOSTRING_APPENDING
        sb.append(map.toString());
        return sb.toString();
    }
    
    public static final String WORLD = "CONSTWorld"; 
    

    @Override
    public String toString() {
        String a = System.getProperty("foo");
        String b = System.getProperty("boo");
        StringBuffer sb = new StringBuffer();
        sb.append("info: ");
        sb.append(a + ":" + b);
        return sb.toString();
    }

    public void testCtorIssue(String a) {
        StringBuffer sb1 = new StringBuffer(a + "1");
        StringBuilder sb2 = new StringBuilder("2" + a);
    }

    public String testFPISB9(String a, String b, String c) {
        String d = a + c;

        StringBuilder sb = new StringBuilder();

        sb.append(ISB_Sample.getBigger(a + b, d));

        return sb.toString();
    }

    private static String getBigger(String a, String b) {
        if (a.length() > b.length())
            return a;
        return b;
    }

    public String testFPISB10() {
        int i = 1;
        int j = 2;
        throw new RuntimeException("i=" + i + ", j=" + j);
    }

    public void testFPISB11(StringBuilder sb, String a, String b) {
        ISB_Sample isb = new ISB_Sample();
        sb.append(isb.xform(a + b));
    }

    public String xform(String a) {
        return a;
    }

}
