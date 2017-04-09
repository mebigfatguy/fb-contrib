package ex;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

public class AWCBR_Sample {
    public void m(int[] v) {
        v[0]++;
    }

    public int testuninitedalloc(int i) {
        int[] data = new int[1];
        data[0] = i;
        m(data);
        i = data[0];
        return i;
    }

    public int testinitedalloc(int i) {
        int[] data = new int[] { i };
        m(data);
        i = data[0];
        return i;
    }

    public int testNoCall(int i) {
        // while silly don't report this, as there is no arg usage
        int[] data = new int[] { i };
        i = data[0];
        return i;
    }
    
    public boolean testFPInvoke(Method m) throws Exception {
        
        Set<String> s = new HashSet<String>();
        Object[] args = new Object[] { s };
        m.invoke(this, args);
        
        s = (Set<String>) args[0];
        
        return s.isEmpty();
    }
}
