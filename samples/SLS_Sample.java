import java.util.List;


public class SLS_Sample {
	private String name;
	
	boolean hasIt(List<SLS_Sample> l, String n) {
		
		boolean found = false;
		for (SLS_Sample s : l) {
			if (s.name.equals(n)) {
				found = true;
			}
		}
		
		return found;
	}
	
}
