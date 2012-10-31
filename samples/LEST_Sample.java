import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

@SuppressWarnings("all")
public class LEST_Sample
{
	public Date testLest1(String input)
	{
		try
		{
			DateFormat df = new SimpleDateFormat("YYYY");
			return df.parse(input);
		}
		catch (ParseException pe)
		{
			throw new IllegalArgumentException(pe.getMessage());
		}
	}

	public Date testLest2(String input)
	{
		try
		{
			DateFormat df = new SimpleDateFormat("YYYY");
			return df.parse(input);
		}
		catch (ParseException pe)
		{
			throw new IllegalArgumentException(pe.getMessage(), pe);
		}
	}

	public Date testLestFP1(String input) throws ParseException
	{
		try
		{
			DateFormat df = new SimpleDateFormat("YYYY");
			return df.parse(input);
		}
		catch (ParseException pe)
		{
			throw pe;
		}
	}

	public Date testLestFP2(String input)
	{
		try
		{
			DateFormat df = new SimpleDateFormat("YYYY");
			return df.parse(input);
		}
		catch (ParseException pe)
		{
			IllegalArgumentException iae = new IllegalArgumentException(pe.getMessage());
			iae.initCause(pe);
			throw iae;
		}
	}

	public void testLestFP3(String s)
	{
		double d;
		try
		{
			d = Double.parseDouble(s);
		}
		catch (NumberFormatException nfe)
		{

		}
		throw new RuntimeException("ok");
	}

	public void testLestFP4(String s) throws Exception
	{
		double d;
		try
		{
			d = Double.parseDouble(s);
		}
		catch (NumberFormatException nfe)
		{
			Exception e = wrap(nfe);
			throw e;
		}
	}

	public void testLestFP5(String s) throws Exception
	{
		double d;
		try
		{
			d = Double.parseDouble(s);
		}
		catch (NumberFormatException nfe)
		{
			Exception e = wrapStatic(nfe);
			throw e;
		}
	}

	public void testLestFP6(String s) throws Exception
	{
		double d;
		try
		{
			d = Double.parseDouble(s);
		}
		finally
		{
			throw new Exception("Yikes");
		}
	}

	public void testLestFP7(String s)
	{
	    try
        {
            double d = Double.parseDouble(s);
        }
	    catch (NumberFormatException e)
	    {
	        throw new RuntimeException(e);
	    }
	}

	public void testLestFP8()
	{
	    try
        {
            Thread.sleep(10L);
        }
        catch (Exception ex)
        {
            throw new AssertionError(ex);
        }
    }

	public void testLestFP3510540() throws Exception
	{
        boolean bool = true;
        if (bool)
        {
            try
            {
                throw new IOException();
            }
            catch (IOException ioe)
            {
                throw new Exception(ioe);
            }
        }
        else
        {
            throw new Exception("message");
        }
	}

	private Exception wrap(Exception e) {
		return new Exception(e);
	}

	private static Exception wrapStatic(Exception e) {
		return new Exception(e);
	}
}
