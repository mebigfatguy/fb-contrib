package ex;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("all")
public class PDP_Sample {
    ArrayList<String> al;

    public PDP_Sample(List<String> l) {
        al = (ArrayList<String>) l;
    }

    private void testFPCodeChecksType(List<String> l) {
        if (l instanceof ArrayList)
            al = (ArrayList<String>) l;
    }

    private void testFPNonParm(String s) {
        List<String> l = new ArrayList<String>();
        al = (ArrayList<String>) l;
    }

    private void testDoubleInSig(double d, List<String> l) {
        al = (ArrayList<String>) l;
    }

    public static void testStatic(long lng, List<String> l) {
        ArrayList<String> aal = (ArrayList<String>) l;
    }

    protected void testFPDerivableMethod(List<String> l) {
        al = (ArrayList<String>) l;
    }

    public static void testMultiCasts(String key, Object o) {
        if (key.equals("Foo")) {
            double d = ((Double) o).doubleValue();
        } else if (key.equals("Boo")) {
            float f = ((Float) o).floatValue();
        }
    }

    public static String testFPFlimsyIfGuard(Comparable<?> c, boolean isNumber) {
        String s = c.toString();
        if (isNumber) {
            Number n = (Number) c;
            s += n.intValue();
        }

        return s;
    }

    private String testFPTableSwitchGuard(Comparable<?> c, int type) {
        String s = "";

        switch (type) {
        case 0:
            Number n = (Number) c;
            s += n.intValue();
            break;

        case 1:
            s += s;
            break;

        case 2:
            s += "0";
            break;

        case 3:
            s += '3';
            break;

        case 4:
            s = null;
            break;

        case 5:
            s = s.substring(0, 1);
            break;
        }

        return s;
    }

    private String testFPLookupSwitch(Comparable<?> c, int type) {
        String s = "";

        switch (type) {
        case 0:
            Number n = (Number) c;
            s += n.intValue();
            break;

        case 1000:
            s += s;
            break;

        case 10000:
            s += "0";
            break;
        }

        return s;
    }
}