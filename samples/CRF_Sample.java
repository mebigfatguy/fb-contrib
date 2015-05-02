import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;


public class CRF_Sample {

	public long testFileFromCPURL() throws URISyntaxException {
		
		URL u = CRF_Sample.class.getResource("/CRF_Sample.class");
		File f = new File(u.toURI());
		return f.length();
	}
}
