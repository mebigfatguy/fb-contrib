import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class WEM_Sample {
    public void badException(String s) {
        if (s.length() == 1)
            throw new IllegalArgumentException("You stink");
    }

    public void goodException(String s) {
        if (s.length() == 1)
            throw new IllegalArgumentException("You stink -->" + s);
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
}
