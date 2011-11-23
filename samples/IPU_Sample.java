import java.util.Properties;


public class IPU_Sample 
{

	public void testIPUSimple()
	{
		Properties p = new Properties();
		p.put("Key", new Integer(0));
	}
	
	public void testIPUUseSetProperty(Object o)
	{
		Properties p = new Properties();
		p.put("Key", o);
	}
	
	public void testIPUUseMinorSetProperty()
	{
		Properties p = new Properties();
		p.put("Key", "Hello");
	}
}
