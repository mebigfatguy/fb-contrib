import java.util.concurrent.TimeUnit;


public class CTU_Sample {

	public long simpleMillisAndNanos() {
		long millis = System.currentTimeMillis();
		long nanos = System.nanoTime();
		
		return millis + nanos;
	}
	
	public long badUseOfConvert() {
		long secs = TimeUnit.SECONDS.convert(1000, TimeUnit.MILLISECONDS);
		long millis = System.currentTimeMillis();
		
		return secs + millis;
	}
}
