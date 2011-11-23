import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class DSOC_Sample {
	public void testSetOfLists()
	{
		List<String> l = new ArrayList<String>();
		Set<List<String>> s = new HashSet<List<String>>();
		s.add(l);
	}
	
	public void testKeySetOfSets() {
		Set<String> s = new HashSet<String>();
		Map<Set<String>, String> m = new HashMap<Set<String>, String>();
		
		m.put(s, "Foo");
	}
	
	public void testFPListOfSets() {
		Set<String> s = new HashSet<String>();
		List<Set<String>> l = new ArrayList<Set<String>>();
		
		l.add(s);
	}
}
