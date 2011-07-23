import java.util.Iterator;
import java.util.List;


public class MTA_Sample
{
	public String[] testForCopy(List<String> l)
	{
		String[] result = new String[l.size()];
		for (int i = 0; i < l.size(); i++)
		{
			result[i] = l.get(i);
		}
		
		return result;
	}
	
	public String[] testIterCopy(List<String> l)
	{
		String[] result = new String[l.size()];
		Iterator<String> it = l.iterator();
		int i = 0;
		while (it.hasNext())
		{
			result[i] = it.next();
			i++;
		}
		
		return result;
	}
}