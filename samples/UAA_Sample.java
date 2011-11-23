import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class UAA_Sample {
	
	private final Set<String> in = new HashSet<String>();
	private final Set<String> out = new HashSet<String>();
	
	public Set<String> testLocalSet(Set<String> in) {
		Set<String> out = new HashSet<String>();
		out.add("Foo");
		out.add("Bar");
		for (String s : in) {
			out.add(s);
		}
		return out;
	}
	
	public Set<String> testFPCondition(Set<String> in) {
		Set<String> out = new HashSet<String>();
		for (String s : in) {
			if (s.startsWith("a"))
				out.add(s);
		}
		return out;
	}
	
	public Set<String> testKeyOrValueAdd(Map<String, String> in)
	{
		Set<String> out = new HashSet<String>();
		for (String s : in.keySet())
			out.add(s);
		
		for (String s : in.values())
			out.add(s);
		
		return out;
	}

	public void fpPrematureLoopEnd(List<String> ss) 
	{
		for (String s : ss) 
		{
			out.add(s);
			if (s.length() == 0)
			{
				continue;
			}
			out.add(s);
		}
	}
	
	public void testMemberSet() {
		for (String s : in)
			out.add(s);
	}
	
	public Set<String> testFromArray(String[] in) {
		Set<String> out = new HashSet<String>();
		for (String s : in)
			out.add(s);
		
		return out;
	}
	
	public void testFPIfAtEndOfLoop(List<String> d, List<String> s, int i)
	{
		Iterator<String> it = s.iterator();
		while (it.hasNext())
		{
			if (i == 0)
			{
				d.add(it.next());
			}
		}
	}
	
	public void testFPAddWithCheck(List<String> src, List<String> dst)
	{
		for (String s : src)
		{
			if (dst.add(s))
				System.out.println("Hmm");
		}
	}
	
	public void testFP1934619_A(final List<String> out, final Set<String> currentParents) 
	{
		for (String currentOid : currentParents) 
		{
			out.add(currentOid);
			if (currentOid.indexOf("xx") > -1) {
				continue;
			}
			out.add("don't forget me");
		}
	}
	
	public void testFP1934619_B(final List<String> out, final Set<String> currentParents) 
	{
		for (String currentOid : currentParents) 
		{
			if (currentOid.indexOf("xx") > -1) 
			{
					throw new RuntimeException("enough is enough");
			}
			out.add(currentOid);
		}
	}
}
