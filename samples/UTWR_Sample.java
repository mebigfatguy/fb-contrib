import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

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

}
