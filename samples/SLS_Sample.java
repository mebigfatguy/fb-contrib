import java.util.List;
import java.util.Map;

public class SLS_Sample {
	private String name;
	private int age;

	boolean hasIt(List<SLS_Sample> l, String n) {

		boolean found = false;
		for (SLS_Sample s : l) {
			if (s.name.equals(n)) {
				found = true;
			}
		}

		return found;
	}

	boolean hasAge(List<SLS_Sample> l, int age) {

		boolean found = false;
		for (SLS_Sample s : l) {
			if (s.age == age) {
				found = true;
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
