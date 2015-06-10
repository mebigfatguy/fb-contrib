import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

public class LSYC_Sample {
    List<String> syncfield;

    public Object[] test1(String[] s) {
        Vector<String> v = new Vector<String>();
        v.addAll(Arrays.asList(s));
        Collections.sort(v);
        return v.toArray();
    }

    public void test2(Set<String> s) {
        Set<String> ss = Collections.<String> synchronizedSet(s);
        for (String st : ss) {
            System.out.println(st);
        }
    }
    
    public String testNotStoredSB() {
    	final StringBuffer stringBuffer = new StringBuffer().append("agrego ").append("un ");
        stringBuffer.append("string ");
        return stringBuffer.toString();
    }

    public void test3(List<String> ls) {
        // don't report
        List<String> a = Collections.synchronizedList(ls);
        syncfield = a;
        
        System.out.println(syncfield);
    }
    
    public List<String> getList() {
        // don't report
       return Collections.synchronizedList(new ArrayList<String>());
       
    }

    public Map<String, Map<String, String>> test4() {
        // report as low
        Map<String, Map<String, String>> main = new Hashtable<String, Map<String, String>>();

        Map<String, String> m = new Hashtable<String, String>();
        m.put("Hello", "there");
        main.put("First", m);

        return main;

    }

    public String printString() {
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < 50; i++)
            buffer.append("Findbugs ");
        return buffer.toString();
    }
    
    public String printString2() {
    	//no tag, but probably should. 
    	return new StringBuffer().append("Hello").append("World").toString();
    }

}
