import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("all")
public class BAS_Sample
{
	private final Map<String, String> m = new HashMap<String, String>();

	public void testIfScope(String s)
	{
		Object o = new Object();
		if (s.equals("Foo"))
		{
			s = o.toString();
		}
	}

	public String testFPForScope(String s)
	{
		Object o = new Object();
		while (s.length() > 0)
		{
			o = s.substring(0, 1);
			s = s.substring(1);
		}
		return s;
	}

	public String testFP2Scopes(String s)
	{
		Object o = new Object();
		if (s.equals("Boo"))
        {
            s = o.toString();
        }
        else if (s.equals("Hoo"))
        {
            s = o.toString();
        }

		return s;
	}

	public String test2InnerScopes(String s)
	{
		Object o = new Object();
		if (s != null)
		{
			if (s.equals("Boo"))
            {
                s = o.toString();
            }
            else if (s.equals("Hoo"))
            {
                s = o.toString();
            }
		}

		return s;
	}

	public String testFPLoopCond(List<String> in)
	{
		StringBuilder sb = new StringBuilder();
		for (String s : in)
		{
			sb.append(s);
		}
		return sb.toString();
	}

	public List<String> getList()
	{
		return null;
	}

	public String testSwitch(int a)
	{
		String v = "Test";

		switch (a)
		{
			case 1:
				v = "Testa";
			break;

			case 2:
				v = "Tesseract";
			break;

			case 3:
				v = "Testy";
			break;

			default:
				v = "Rossa";
			break;
		}

		return null;
	}

	public void testFPSync(Set<String> a, Set<String> b)
	{
		String c, d;

		synchronized(this)
		{
			c = a.iterator().next();
			d = b.iterator().next();
		}

		if (d.length() > 0)
		{
			d = c;
		}
	}

	public int testFPObjChange(Calendar c, boolean b)
	{
	    int hour = c.get(Calendar.HOUR_OF_DAY);
	    c.set(2000, Calendar.JANUARY, 1);

	    if (b)
	    {
	        return hour;
	    }

	    return 0;
	}

	public void testFPSrcOverwrite(int src, boolean b)
	{
		int d = src;
		src = 0;

		if (b)
		{
			System.out.println(d);
		}
	}

	public void testFPRiskies1(boolean b) {
		long start = System.currentTimeMillis();

		if (b) {
			long delta = System.currentTimeMillis() - start;
			System.out.println(delta);
		}
	}
}
