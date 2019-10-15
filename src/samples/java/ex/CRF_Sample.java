package ex;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

public class CRF_Sample {

    public long testFileFromCPURL() throws URISyntaxException {

        URL u = CRF_Sample.class.getResource("/CRF_Sample.class");
        File f = new File(u.toURI());
        return f.length();
    }

    public long testFileFromCPURL2() {
        URL u = CRF_Sample.class.getResource("/CRF_Sample.class");
        File f = new File(u.getFile());
        return f.length();
    }

    public long testFileFromCPURL3() throws MalformedURLException {
        URL u = new URL("http://www.google.com");
        File f = new File(u.getFile());
        return f.length();
    }
}
