import java.util.List;
import java.util.Map;

public class SLS_Sample {
	private String name;
	private int age;

	SLS_Sample hasIt(List<SLS_Sample> l, String n) {

		SLS_Sample found = null;
		for (SLS_Sample s : l) {
			if (s.name.equals(n)) {
				found = s;
			}
		}

		return found;
	}

	SLS_Sample hasAge(List<SLS_Sample> l, int age) {

		SLS_Sample found = null;
		for (SLS_Sample s : l) {
			if (s.age == age) {
				found = s;
			}
		}

		return found;
	}

	boolean fpSetFlag(Map<String, SLS_Sample> m, String name) {
		boolean found = false;
		for (SLS_Sample s : m.values()) {
			if (s == null || s.name.equals(name)) {
				found = true;
				s.age = 1;
			}
		}
		
		return found;
	}
	
	int fpCalcTotal(List<SLS_Sample> l, String name) {
		int total = 0;
		for (SLS_Sample s : l) {
			if (s.name.equals(name)) {
				total += s.age;
			}
		}
		
		return total;
	}

}
