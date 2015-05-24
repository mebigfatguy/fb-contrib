import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;
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
	//tag WOC_WRITE_ONLY_COLLECTION_FIELD 
    private final Set<String> memberSet = new HashSet<String>();
    private Set<String> fpSet;
    private final List<String> fpList = new ArrayList<String>();
    private List<String> fp1 = new ArrayList<String>();
    private List<String> fp2 = new ArrayList<String>();
    private List<String> fp3;
    
    public WOC_Sample(List<String> x) {
    	fp3 = x;
    }

    public void testWOCSimple() {
    	//tag WOC_WRITE_ONLY_COLLECTION_LOCAL 
        Set<String> s = new HashSet<String>();
        s.add("Foo");
        memberSet.add("fee");
        if (fpSet.retainAll(new HashSet<String>())) {
            System.out.println("woops");
        }
    }

    public Map<String, String> testFPWOCReturn() {
    	//no tag, value is returned
        Map<String, String> m = new HashMap<String, String>();
        m.put("Foo", "Bar");
        memberSet.add("fi");
        fpSet = new HashSet<String>();
        return m;
    }

    public void testFPWOCAsParm() {
    	//no tag, passed to helper function
        Map<String, String> m = new HashMap<String, String>();
        m.put("Foo", "Bar");
        memberSet.add("fo");
        fpSet.add("boo");
        helper(0, m);
    }

    public void testFPWOCCopy() {
    	//no tag, reference is copied
        Set<String> s = new LinkedHashSet<String>();
        s.add("foo");
        @SuppressWarnings("unused")
		Set<String> c = s;
        memberSet.add("fum");
    }

    public void testFPWOCInArray() {
    	//no tag, object is added to array
        Vector<Integer> v = new Vector<Integer>();
        v.add(Integer.valueOf(0));
        Object[] o = new Object[] { v };
    }

    public void testFPWOCUseReturnVal() {
    	//no tag, return value was looked at
        LinkedList<String> l = new LinkedList<String>();
        l.add("Foo");
        l.add("Bar");

        if (l.remove("Foo")) {
            System.out.println("Dont' report");
        }
    }

    public Set<String> testFPTernary(boolean b) {
        Set<String> s = new HashSet<String>();
        s.add("foo");
        s.add("bar");

        return b ? s : Collections.<String> emptySet();
    }

    private void helper(int i, Map<String, String> x) {
    	System.out.println(x.get(i));
    }

    //no tag, put in anonymous class
    public void testFPInnerClass(final Set<String> data) {
    	
        ActionListener al = new ActionListener() {
            @Override
			public void actionPerformed(ActionEvent ae) {
                data.add("Woot");
            }
        };
        System.out.println(al);
    }

    
    public List<String> fpOtherInstance(WOC_Sample ws) {
        return ws.fpList;
    }

    public String fpCheckReference(boolean b) {
    	//no tag, null check done
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

    public List<String> fpWOCTernary(boolean a, boolean b) {

        if (a) {
            return b ? fp1 : fp2;
        }

        //no tag, either could be returned in the ternary operator
        List<String> used1 = new ArrayList<String>();
        List<String> used2 = new ArrayList<String>();

        return b ? used1 : used2;
    }
    
    public void fpWOCAllowToMap(Map<String, List<String>> m, List<String> l) {
        if (l == null) {
            m.put("FP",  l = new ArrayList<String>());
        }
        l.add("Hello there");
    }
    
    public void fpClone(List<Data> l) {
    	HashSet<String> s = new HashSet<>();
    	for (Data d : l) {
    		d.ss = (Set<String>) s.clone();
    	}
    }

    public static class FpContains {
        private List<String> fpSetList;

        public FpContains() {
            fpSetList = new ArrayList<String>();
        }

        public void add() {
            fpSetList.add("Foo");
        }

        protected void contains() {
            for (int i = 0; i < 10; i++) {
                if (fpSetList.get(i) != null) {
                    System.out.println("Contains");
                }
            }
        }
    }
    
    public void fpAddToCtorParm(String x) {
    	fp3.add(x);
    }
    
    private class Data {
    	Set<String> ss;
    }
}
