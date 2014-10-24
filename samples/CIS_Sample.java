import java.util.Date;


public class CIS_Sample {

	enum Sample {Hi, Lo};
	
	String val;
	
	public void testToStringToField() {
		val = Sample.Hi.toString();
	}
	
	public void testSBWithToStringToField(Date d, Integer i) {
		StringBuilder s = new StringBuilder();
		s.append(d);
		s.append(i);
		val = s.toString();
	}
	
	public void fpTestToStringToFieldSB(String s) {
		val = s + "wow";
	}
}
