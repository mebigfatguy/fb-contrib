
class AOM_Super
{
	public void test1() {
		test2();
		System.out.println("test");
	}
	
	private void test2() {
		System.out.println("test");
	}
}

public abstract class AOM_Sample extends AOM_Super
{
	@Override
	public abstract void test1();
	public abstract void test2();
}
