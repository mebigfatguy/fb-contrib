import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("all")
public class BSB_Sample {
    private Object lock = new Object();
    private Set<String> info = new HashSet<String>();
    private Map<String, Object> synchMap = Collections.synchronizedMap(new HashMap<String, Object>());

    public void testFieldBeginBloated(int i, int j) {
        synchronized (lock) {
            StringBuffer sb = new StringBuffer();
            sb.append("Test");
            sb.append(i);
            info.add(sb.toString());
        }
    }

    public void testLocalBeginBloated(int j) {
        Set<String> i = getInfo();
        synchronized (i) {
            StringBuffer sb = new StringBuffer();
            sb.append("Test");
            sb.append(j);
            i.add(sb.toString());
        }
    }

    public void testAliasedLocalBeginBloated(int j) {
        Set<String> i = getInfo();
        synchronized (info) {
            StringBuffer sb = new StringBuffer();
            sb.append("Test");
            sb.append(j);
            i.add(sb.toString());
        }
    }

    public void testBranchCutDown(int j) {
        Set<String> i = getInfo();
        synchronized (i) {
            StringBuffer sb = new StringBuffer();
            if (sb.length() > 0) {
                sb.append("Test");
                sb.append(j);
                i.add(sb.toString());
            }
        }
    }

    public Set<String> getInfo() {
        return info;
    }

    public void accessSyncMap() {
        Set keySet = synchMap.keySet();
        synchronized (synchMap) {
            for (Iterator it = keySet.iterator(); it.hasNext();) {
                String key = (String) it.next();
                Object obj = synchMap.get(key);
            }
        }
    }

}
