
public class CIS_Sample {

	enum Sample {Hi, Lo};
	
	String val;
	
	public void testToStringToField() {
		val = Sample.Hi.toString();
	}
	
	public void fpTestToStringToFieldSB(String s) {
		val = s + "wow";
	}
}
