package ex;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class WEM_Sample {
    public void badException(String s) {
        if (s.length() == 1) {
            throw new IllegalArgumentException("You stink");
        }
    }

    public void goodException(String s) {
        if (s.length() == 1) {
            throw new IllegalArgumentException("You stink -->" + s);
        }
    }

    public static void ok() {
        throw new RuntimeException("Wow");
    }

    public static void wrappingWithException() throws Exception {
        InputStream is = null;
        try {
            is = new FileInputStream("who dat");

        } catch (IOException ioe) {
            throw new Exception(ioe);
        }
    }

    public void fpunimpled() {
        throw new UnsupportedOperationException("fpunimpled is unimpled");
    }

    static {
        try (InputStream is = WEM_Sample.class.getResourceAsStream("/foo/bar")) {
            if (is == null) {
                throw new Error("Couldn't open foo/bar");
            }
        } catch (IOException e) {
            throw new Error("Couldn't open foo/bar", e);
        }
    }
}
