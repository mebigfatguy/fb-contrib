import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
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
		List<String> l = getFoo(new ArrayList<String>());
	}
	
	public List<String> getFoo(List<String> l) {
		return l;
	}
	
	public void fpUncalledDontReport() {
		fpHasRTAnnotation();
	}
	
	@RT
	public  void fpHasRTAnnotation() {
	}
	
	
}

abstract class OPMSuper {
	public abstract List<String> getFoo(List<String> l);
}

@Retention(RetentionPolicy.RUNTIME)
@interface RT {
	
}
