package ex;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.io.IOUtils;

public class IOI_Sample {

    public byte[] getIOIData(File f) throws IOException {
        try (InputStream is = new BufferedInputStream(new FileInputStream(f)); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            IOUtils.copy(is, baos);

            return baos.toByteArray();
        }
    }

    public byte[] getIOIReaderData(File f) throws IOException {
        try (FileReader r = new FileReader(f); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            IOUtils.copy(r, baos);

            return baos.toByteArray();
        }
    }

    public void copy(String inName, String outName) throws IOException {
        byte[] buf = new byte[1024];

        try (InputStream is = new FileInputStream(inName); OutputStream os = new FileOutputStream(outName);) {

            int size = is.read(buf);
            while (size >= 0) {
                os.write(buf, 0, size);
                size = is.read(buf);
            }
        }
    }
}
