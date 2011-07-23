import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@SuppressWarnings("all")
public class AFBR_Sample
{
	public int test1(boolean b)
	{
		try
		{
			int i = 0;
		}
		finally
		{
			if (b)
				return 0;
			int j = 0;
		}
		return 2;
	}
	
	public int test2()
	{
		try
		{
			return 0;
		}
		finally
		{
			throw new NullPointerException();
		}
	}
	
	public int test3() throws Exception
	{
		try
		{
			return 0;
		}
		finally
		{
			throw new Exception();
		}
	}
	
	public int test4()
	{
		try
		{
			throw new Exception();
		}
		catch (Exception e)
		{
			return 1;
		}
		finally 
		{
			return 0;
		}
	}
	
	public int test5() throws IOException
	{
		InputStream is = null;
		try
		{
			is = new FileInputStream("test");
			return 1;
		}
		catch (IOException ioe) 
		{
			System.out.println("error");
		}
		finally
		{
			if (is != null)
				is.close();
		}
		return 0;
	}
}