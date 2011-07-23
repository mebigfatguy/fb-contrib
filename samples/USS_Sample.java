import java.util.StringTokenizer;

public class USS_Sample 
{
	public String[] testUss1(String s)
	{
		StringTokenizer st = new StringTokenizer(s, ";");
		int count = st.countTokens();
		String[] sarray = new String[count];
		
		int i = 0;
		while (st.hasMoreTokens())
		{
			sarray[i] = st.nextToken();
			i++;
		}
		
		return sarray;
	}
	
	public String[] testUss2(String s)
	{
		StringTokenizer st = new StringTokenizer(s, ";");
		int count = st.countTokens();
		String[] sarray = new String[count];
		
		int i = 0;
		while (st.hasMoreTokens())
		{
			sarray[i++] = st.nextToken();
		}
		
		return sarray;
	}
	
	public String[] testUss3(String s)
	{
		StringTokenizer st = new StringTokenizer(s, ";");
		int count = st.countTokens();
		String[] sarray = new String[count];
		
		for (int i = 0; i < count; i++)
		{
			sarray[i++] = st.nextToken();
		}
		
		return sarray;
	}
	
	public String[] testUss4(String s)
	{
		StringTokenizer st = new StringTokenizer(s, ";");
		int count = st.countTokens();
		String[] sarray = new String[count];
		
		int i = 0;
		while (st.hasMoreElements())
		{
			sarray[i++] = (String)st.nextElement();
		}
		
		return sarray;
	}
	
	public String[] testUssFP5(String s)
	{
		StringTokenizer st = new StringTokenizer(s, ";");
		int count = st.countTokens();
		String[] sarray = new String[count];
		
		int i = 0;
		while (st.hasMoreElements())
		{
			sarray[i++] = "***" + (String)st.nextElement();
		}
		
		return sarray;
	}
	
	public String[] testUssFP6(String s)
	{
		StringTokenizer st = new StringTokenizer(s, ";");
		int count = st.countTokens();
		String[] sarray = new String[count];
		
		int i = 0;
		while (st.hasMoreTokens())
		{
			String x = st.nextToken();
			if (x.equals("*"))
				x = "Star";
			
			sarray[i++] = x;
		}
		
		return sarray;
	}
	
	
}
