import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Hashtable;
import java.util.zip.DataFormatException;

import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

@SuppressWarnings("all")
public class BED_Sample {
    IOException ioe;

    public BED_Sample() throws IOException {

    }

    public BED_Sample(String name) throws NamingException {
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put("name", name);
        DirContext context = new InitialDirContext(env);
    }

    public void doesTheNasty() throws FileNotFoundException, IOException {

    }

    public void doesTheNasty2() throws IOException, FileNotFoundException {

    }

    public void fpJustAwful() throws IOException, Exception {

    }

    private void badThrow() throws SQLException {

    }

    public static void badStatic() throws DataFormatException {

    }

    public final void badFinal() throws ClassNotFoundException {

    }

    public static void doIt() throws SQLException, IOException {
        InputStream is = new FileInputStream("c:\\temp.txt");
    }

    public static void fp() throws Exception {
        InputStream is = new FileInputStream("c:\\temp.txt");
    }

    private void fpThrowField(boolean b) throws IOException {
        if (b) {
            throw ioe;
        } else {
            IOException e = ioe;
            throw ioe;
        }
    }

    public Object iAmCreatingAnObject() {
        return new Object() {
            private byte[] iHaveToThrowAnException() throws IOException {
                return BED_Sample.this.iThrowAnException();
            }
        };
    }

    private byte[] iThrowAnException() throws IOException {
        File.createTempFile("foo", "bar");
        return "Test".getBytes("UTF-8");
    }
}
