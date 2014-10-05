import java.util.Comparator;
import java.util.List;


public class OPM_Sample extends OPMSuper implements Comparator<String> {

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
		return getFoo(l);
	}
	
	public List<String> getFoo(List<String> l) {
		return l;
	}
	
	public void fpUncalledDontReport() {
	}
	
	
}

abstract class OPMSuper {
	public abstract List<String> getFoo(List<String> l);
}
