import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class UTWR_Sample {
    public void utwrStream(File f) throws IOException {
        InputStream is = new FileInputStream(f);
        try {
            is.read();
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    public void fpUTWRStream(File f) throws IOException {
        try (InputStream is = new FileInputStream(f)) {
            is.read();
        }
    }

    public void fpInitializeProperties(InputStream is, Properties props) {
        try (InputStream r = new BufferedInputStream(is)) {
            props.load(r);
        } catch (IOException e) {
            System.out.println("Failed to load properties: " + e);
        }
    }

}
