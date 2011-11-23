import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.log4j.Logger;


@SuppressWarnings("all")
public class LO_Sample 
{
	private static Logger l1 = Logger.getLogger(String.class);
	private static Logger l2 = Logger.getLogger("com.foo.LO_Sample");
	
	public LO_Sample(Logger l3)
	{
		
	}
	
	public void testStutter() throws IOException
	{
		InputStream is = null;
		try
		{
			File f = new File("Foo");
			is = new FileInputStream(f);
		}
		catch (Exception e)
		{
			l1.error(e.getMessage(), e);
		}
		finally
		{
			is.close();
		}
	}
}
