import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PMB_Sample 
{
	private static Set<String> bl_data = new HashSet<String>();
	private static List<String> data = new ArrayList<String>();
	
	public void add(String s)
	{
		bl_data.add(s);
		data.add(s);
	}
	
	public void remove(String s)
	{
		data.remove(s);
	}
}
