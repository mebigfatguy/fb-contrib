
public class CTU_Sample {

	public long simpleMillisAndNanos() {
		long millis = System.currentTimeMillis();
		long nanos = System.nanoTime();
		
		return millis + nanos;
	}
}
