import java.util.Comparator;


public class OPM_Sample implements Comparator<String> {

	public void testNormalMethodCouldBePrivate() {
		someNormalMethod();
	}
	
	@Override
	public int compare(String s1, String s2) {
		return s2.compareTo(s1);
	}
	
	public int testFPGenericDerivation() {
		return compare("Hello", "World");
	}

	public void someNormalMethod() {
		
	}
}
