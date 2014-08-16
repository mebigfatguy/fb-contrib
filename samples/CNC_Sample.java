import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CNC_Sample {
	//tag CNC_COLLECTION_NAMING_CONFUSION
	Map<String, String> argList;
	//tag CNC_COLLECTION_NAMING_CONFUSION
	Set<Integer> targetMap;
	//tag CNC_COLLECTION_NAMING_CONFUSION
	List<Double> bernoulliSet;

	//tag 3xCNC_COLLECTION_NAMING_CONFUSION
	public void testCNC(Map<String, String> argSet, Set<String> nameList, List<String> nameMap) {
    	
    	nameList.addAll(transferData(nameMap));
    }
    
    private Set<String> transferData(List<String> oldList) {
    	if (oldList.isEmpty()) {
    		return Collections.emptySet();
    	}
    	//tag CNC_COLLECTION_NAMING_CONFUSION
    	Set<String> newList = new HashSet<>();
    	for(String s: oldList) {
    		newList.add(s);
    	}
    	return newList;
    	
    }
}
