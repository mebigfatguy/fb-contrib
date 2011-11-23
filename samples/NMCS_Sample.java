import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

public class NMCS_Sample 
{
	private static List<String> test1 = new Vector<String>();
	static {
		test1.add("one");
		test1.add("two");
		test1.add("three");
	}
	
	private Map<String, String> test2 = new Hashtable<String, String>();
	
	private Set<String> test3 = new HashSet<String>();
	
	private List<String> test4 = new Vector<String>();
	
	public String test1()
	{
		StringBuffer sb = new StringBuffer();
		String comma = "";
		for (String s : test1)
		{
			sb.append(comma);
			comma = ",";
			sb.append(s);
		}
		
		return sb.toString();
	}
	
	public String test2()
	{
		test2 = new Hashtable<String, String>();
		
		return test2.get("foo");
	}
	
	public Set<String> test3()
	{
		Set<String> temp = test3;
		temp.add("Foo");
		return temp;
	}
	
	public List<String> test4(boolean b1, boolean b2)
	{
		return b1 ? test4 : 
			b2 ? new Vector<String>() : test4;
	}
	
}
