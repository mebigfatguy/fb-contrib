package ex;
import java.util.ArrayList;
import java.util.List;

public class UNNC_Sample {

    public void testPosNEW() {
        UNNC_Sample sample = new UNNC_Sample();

        if (sample != null) {
            System.out.println("OK");
        }
    }

    public void testNegNEW() {
        UNNC_Sample sample = new UNNC_Sample();

        if (sample == null) {
            return;
        }

        System.out.println("OK");
    }

    public void testPosANEWARAY() {
        String[] s = new String[10];
        if (s != null) {
            System.out.println("OK");
        }
    }

    public void testNegANEWARRAY() {
        String[] s = new String[10];
        if (s == null) {
            return;
        }

        System.out.println("OK");
    }

    public void testPosMULTIANEWARRAY() {
        String[][] s = new String[10][5];
        if (s != null) {
            System.out.println("OK");
        }
    }

    public void testNegMULTIANEWARRAY() {
        String[][] s = new String[10][5];
        if (s == null) {
            return;
        }

        System.out.println("OK");
    }

    public void testFPFinally() throws Exception {
        StringBuilder sb = null;
        try {
            sb = new StringBuilder();
            sb.append("False Positive");
        } finally {
            if (sb != null) {
                sb.setLength(0);
            }
        }
    }

    public void testTrinary(Boolean b) {
        List<String> l = (b == null) ? null : new ArrayList<String>();

        if (l != null) {
            l.add("foo");
        }
    }
}
