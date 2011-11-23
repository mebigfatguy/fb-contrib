
import java.util.Date;

public class DDC_Sample
{
	public void test1( Date d1, Date d2 )
	{
		if (d1.equals( d2 ) || d1.after( d2 ))
			System.out.println( "d1 is greater than or equal to d2" );
		else
			System.out.println( "d1 is less than d2" );
	}	
	
	public void test2( Date d1, Date d2 )
	{
		if (d1.before( d2 ) || d1.equals( d2 ))
			System.out.println( "d1 is less than or equal to d2" );
		else
			System.out.println( "d1 is greater than d2" );
	}

	public void test3( Date d1, Date d2 )
	{
		if (d1.before( d2 ) || d1.after( d2 ))
			System.out.println( "d1 is not equal to d2" );
		else
			System.out.println( "d1 is equal to d2" );
	}}