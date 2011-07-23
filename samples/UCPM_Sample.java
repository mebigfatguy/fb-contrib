
public class UCPM_Sample 
{
	public int testUcpm1(String s)
	{
		return s.indexOf("*") * 10;
	}

	public String testUcpm2(String s)
	{
		return s.startsWith("*") ? s.substring(1) : s;
	}
}
