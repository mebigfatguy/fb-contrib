

public class LSC_Sample
{
	public boolean test1(String s) {
		return s.equals("Hello");
	}
	
	public boolean test2(String s) {
		return "Hello".equals(s);
	}
	
	public boolean test3(String s1, String s2) {
		return s1.equals(s2);
	}
	
	public int test4(String s) {
		return s.compareTo("Hello");
	}
	
	public int test5(String s) {
		return "Hello".compareTo(s);
	}
}