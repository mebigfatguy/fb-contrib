import java.util.HashSet;
import java.util.Set;


@SuppressWarnings("all")
public class SMII_Sample
{
    private Inner i = new Inner();
    
	public static void static_empty()
	{
	}
	
	public static void static_one(String s)
	{
	}
		
	public void test_empty(SMII_Sample smii)
	{
		smii.static_empty();
	}
	
	public void test_empty2(SMII_Sample smii)
	{
		smii.static_one("foo".toUpperCase());
	}
	
	public SMII_Sample getSMI() 
	{
		return new SMII_Sample();
	}
	
	public void test_chaining()
	{
		new SMII_Sample().getSMI().static_one("hello");
	}
	
	public void test_dotclass()
	{
		Set s = new HashSet();
		s.add(String.class);
	}
	
	public void test_ClassForName() throws ClassNotFoundException
	{
		Class c = Class.forName("java.lang.Object");
	}
	
	public void test_ClassGetName()
	{
		String name = Object.class.getName();
	}
    
    public void avoidGeneratedMethods(final Inner inner)
    {
        inner.next = null;
    }
    
    public static class Inner
    {
        private Inner next;   
    }
    
    public void testFPInstance(SMII_Sample sample)
    {
    	SMII_Sample.testInstanceAsFirstParm(sample, "");
    	SMII_Sample.testInstanceAsFirstParm("");
    }
    
    public static SMII_Sample testInstanceAsFirstParm(String s1)
    {
    	return new SMII_Sample();
    }
    
    public static SMII_Sample testInstanceAsFirstParm(SMII_Sample sample, String s1)
    {
    	return sample;
    }
}