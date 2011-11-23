
public class TR_Sample 
{
	public int factorial(int n)
	{
		if (n == 1)
			return 1;
		
		return n * factorial(n-1);
	}
}
