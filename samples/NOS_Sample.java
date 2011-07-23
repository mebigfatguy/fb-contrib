import java.util.Map;

@SuppressWarnings("all")
public class NOS_Sample {
	private Object lock = new Object();
	
	public String test(Object o) {
		synchronized(o) {
			return o.toString();
		}
	}
	
	public String test2(Object o) {
		synchronized(this) {
			return o.toString();
		}
	}
	
	public String test3(Map m) {
		String v = (String)m.get("boo");
		synchronized (v) {
			return v.substring(0,1);
		}
	}
	
	public String test4(Object o) {
		synchronized(lock) {
			return o.toString();
		}
	}
}
