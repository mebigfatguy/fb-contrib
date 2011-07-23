import java.util.ArrayList;
import java.util.List;

public class DLC_Sample
{
	private List<String> badList = new ArrayList<String>();
	private List<String> goodList = new ArrayList<String>();
	private List<String> questionableList = new ArrayList<String>();
	
	public void test1(String s1, String s2) {
		if (badList.contains(s1))
			badList.add(s2);
		
		for (int i = 0; i < badList.size(); i++) {
			String s3 = badList.get(i);
			if (badList.indexOf(s1+s2) >= 0)
				badList.remove(s3);
		}
	}
		
	public void test2(String s1, String s2) {
		int idx = goodList.indexOf(s1);
		goodList.set(idx, s2);
	}
	
	public List<String> test3(String s1) {
		if (questionableList.contains(s1))
			return questionableList;
		List<String> nl = new ArrayList<String>();
		nl.add(s1);
		return nl;
	}
}