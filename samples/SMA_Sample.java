import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;

public class SMA_Sample implements ActionListener {
    SMA_Sample proto;

    public void testSMA(SMA_Sample s1) {
        doubleSMAs(s1, s1);
    }

    public void testNonReport(int a, int b) {
        doubleInts(4, 4);
    }

    public void testSMAFPMaps(Integer i) {
        Map<Integer, Integer> m = new HashMap<Integer, Integer>();

        m.put(i, i);
    }

    public void testFPTwoInstances(SMA_Sample other) {
        doubleSMAs(proto, proto.proto);
    }

    public void testFPPrimitives(int i) {
        doubleInts(i, i);
    }

    public void testMultiTypes(SMA_Sample a) {
        twoInfs(a, a);
    }

    public void doubleInts(int i, int j) {
    }

    public void doubleSMAs(SMA_Sample s) {
        doubleSMAs(s, s);
    }

    public void doubleSMAs(SMA_Sample s1, SMA_Sample s2) {

    }

    public void twoInfs(SMA_Sample a, ActionListener al) {

    }

    public void actionPerformed(ActionEvent ae) {
    }
}
