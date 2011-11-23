
@SuppressWarnings("all")
public final class FCBL_Sample 
{
	public int foo;
	private int moo;
	protected int boo;
	int hoo;
	private int fp;
	private String test;
	private int x = 1;
	private int y = 2;
	
	public FCBL_Sample()
	{
		foo = 0;
		moo = 1;
		boo = 2;
		hoo = 3;
		fp = 4;
	}
	
	public void method1() 
	{
        x = 50;
        System.out.println(x);
        System.out.println(y);
	}
	
	public void test1()
	{
		foo = 2;
		moo = 3;
		boo = 4;
		fp = 5;
	}
	
	public void test2()
	{
		boo = fp;
	}
	
	public void test3(String in)
	{
		boolean found = false;
		if (in.equals("boo"))
			test = "boo";
		else if (in.equals("hoo"))
			test = "hoo";
		else if (in.equals("moo"))
		{
			if (test.equals("loo") && !found)
			{
				found = true;
			}
		}
		
		test = "woowoo";
	}
}
