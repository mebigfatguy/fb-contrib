import java.util.HashMap;
import java.util.Map;


public class UMTP_Sample {

    public <T, C> C getT(String s, int i, Class<C> cls) {
        T t = (T) getFoo();
        System.out.println(t);

        return (C) new Object();
    }

    public <T> String fpUseClass(T t) {
        return t.toString();
    }

    public <T> String fpUseClass(Class<T> c) {
        return c.getName().toString();
    }

    public <T> String fpUseArray(T[] t) {
        return t[0].toString();
    }

    private <K, V> Map<V, K> fpKVReverseMap(Map<K, V> map) {
        Map<V, K> m = new HashMap<V, K>();
        for (Map.Entry<K, V> entry : map.entrySet()) {
            m.put(entry.getValue(), entry.getKey());
        }

        return m;
    }

    public Object getFoo() {
        return null;
    }
}
