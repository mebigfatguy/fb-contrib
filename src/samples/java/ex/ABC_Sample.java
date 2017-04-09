package ex;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class ABC_Sample {
    public Map<String[], String> testMaps(String[] key, String value) {
        Map<String[], String> m = new HashMap<String[], String>();
        m.put(key, value);
        return m;
    }

    public Set<String[]> testSets(String[] values) {
        Set<String[]> s = new HashSet<String[]>();
        s.add(values);
        return s;
    }

    public boolean testLists(List<String[]> l, String[] value) {
        return l.contains(value);
    }

    public static class UseComparator {
        private Map<byte[], byte[]> testComp;

        public UseComparator() {
            testComp = new TreeMap<byte[], byte[]>(new Comparator<byte[]>() {
                @Override
                public int compare(byte[] b1, byte[] b2) {
                    return b1.length - b2.length;
                }
            });
        }

        public void testc() {
            testComp.put(new byte[5], new byte[3]);
        }
    }
}
