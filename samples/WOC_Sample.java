import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

public class WOC_Sample {
    private final Set<String> memberSet = new HashSet<String>();
    private Set<String> fpSet;
    private final List<String> fpList = new ArrayList<String>();

    public void testWOCSimple() {
        Set<String> s = new HashSet<String>();
        s.add("Foo");
        memberSet.add("fee");
        if (fpSet.retainAll(new HashSet<String>())) {
            System.out.println("woops");
        }
    }

    public Map<String, String> testFPWOCReturn() {
        Map<String, String> m = new HashMap<String, String>();
        m.put("Foo", "Bar");
        memberSet.add("fi");
        fpSet = new HashSet<String>();
        return m;
    }

    public void testFPWOCAsParm() {
        Map<String, String> m = new HashMap<String, String>();
        m.put("Foo", "Bar");
        memberSet.add("fo");
        fpSet.add("boo");
        helper(0, m);
    }

    public void testFPWOCCopy() {
        Set<String> s = new LinkedHashSet<String>();
        s.add("foo");
        Set<String> c = s;
        memberSet.add("fum");
    }

    public void testFPWOCInArray() {
        Vector<Integer> v = new Vector<Integer>();
        v.addElement(Integer.valueOf(0));
        Object[] o = new Object[] { v };
    }

    public void testFPWOCUseReturnVal() {
        LinkedList<String> l = new LinkedList<String>();
        l.add("Foo");
        l.add("Bar");

        if (l.remove("Foo")) {
            System.out.println("Dont' report");
        }
    }

    public Set<String> testFPTrinary(boolean b) {
        Set<String> s = new HashSet<String>();
        s.add("foo");
        s.add("bar");

        return b ? s : Collections.<String> emptySet();
    }

    private void helper(int i, Map<String, String> x) {
    }

    public void testFPInnerClass(final Set<String> data) {
        ActionListener al = new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                data.add("Woot");
            }
        };
    }

    public List<String> fpOtherInstance(WOC_Sample ws) {
        return ws.fpList;
    }
    
    public String fpCheckReference(boolean b) {
        List<String> s = null;
        
        if (b) {
            s = new ArrayList<String>();
            s.add("foo");
        }
        
        String result;
        if (s != null) {
            result = "yes";
        } else {
            result = "no";
        }
        
        return result;
    }

    public static class FpContains {
        private List<String> fpSet;

        public FpContains() {
            fpSet = new ArrayList<String>();
        }

        public void add() {
            fpSet.add("Foo");
        }

        protected void contains() {
            for (int i = 0; i < 10; i++) {
                if (fpSet.get(i) != null) {
                    System.out.println("Contains");
                }
            }
        }
    }
}
