import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;


public class CSI_Sample {

	public void testReaderCS(String fileName) {
		
		try (Reader r = new InputStreamReader(new FileInputStream(fileName), "UTF-8")) {
			char[] c = new char[1000];
			r.read(c);
		} catch (IOException e) {
		}
	}
	
	public String testUnknownEncoding(String url) throws UnsupportedEncodingException {
		return URLEncoder.encode(url, "FOO");
	}
	
	public void testUseConstants(File f) throws UnsupportedEncodingException, FileNotFoundException {
		try (PrintWriter pw = new PrintWriter(f, "UTF-8")) {
			pw.println("Hello world");
		}
	}
}
