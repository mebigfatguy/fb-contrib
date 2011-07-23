import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class STB_Sample {
	public void testSTB(File f1, File f2) throws STBException {
		try {
			InputStream is = new FileInputStream(f1);
		} catch (IOException ioe) {
			throw new STBException();
		}

		try {
			InputStream is = new FileInputStream(f2);
		} catch (IOException ioe) {
			throw new STBException();
		}
	}

	public void fpTestMethodDeclaresThrownType(File f1, File f2) throws STBException, IOException {
		try {
			InputStream is = new FileInputStream(f1);
		} catch (IOException ioe) {
			throw new STBException();
		}

		try {
			InputStream is = new FileInputStream(f2);
		} catch (IOException ioe) {
			throw new STBException();
		}
	}

	static class STBException extends Exception {
	}
}
