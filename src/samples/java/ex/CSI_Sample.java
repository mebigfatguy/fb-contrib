package ex;

import java.beans.XMLEncoder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.logging.MemoryHandler;
import java.util.logging.StreamHandler;

public class CSI_Sample {

    private Scanner scan;

    public void testReaderCS(String fileName) throws UnsupportedEncodingException {

        // tag CSI_CHAR_SET_ISSUES_USE_STANDARD_CHARSET
        try (Reader r = new InputStreamReader(new FileInputStream(fileName), "UTF-8")) {
            char[] c = new char[1000];
            r.read(c);
        } catch (IOException e) {
        }

        // tag CSI_CHAR_SET_ISSUES_USE_STANDARD_CHARSET
        byte[] bytes = "test".getBytes("UTF-8");

        // tag CSI_CHAR_SET_ISSUES_USE_STANDARD_CHARSET
        String oddlyConstructedString = new String(bytes, "US-ASCII");

        // no tag
        oddlyConstructedString.getBytes(StandardCharsets.UTF_16);

        // tag CSI_CHAR_SET_ISSUES_USE_STANDARD_CHARSET
        oddlyConstructedString = new String(bytes, 0, 10, "UTF-16");
    }

    public String testUnknownEncoding(String url) throws UnsupportedEncodingException {
        // tag CSI_CHAR_SET_ISSUES_UNKNOWN_ENCODING
        return URLEncoder.encode(url, "FOO");
    }

    public void testLowerCaseEncoding(String fileName) throws UnsupportedEncodingException {
        try (Reader r = new InputStreamReader(new FileInputStream(fileName), "utf-8")) {
            char[] c = new char[1000];
            r.read(c);
        } catch (IOException e) {
        }
    }

    @SuppressWarnings("resource")
    public void testUseConstants(File f) throws UnsupportedEncodingException, FileNotFoundException {
        // tag CSI_CHAR_SET_ISSUES_USE_STANDARD_CHARSET_NAME
        try (PrintWriter pw = new PrintWriter(f, "UTF-8")) {
            pw.println("Hello world");
        }
        // tag CSI_CHAR_SET_ISSUES_USE_STANDARD_CHARSET_NAME
        try (Scanner s = new Scanner(f, "UTF-8")) {
            System.out.println(s.nextLine());
        }

        if (scan == null) {
            // tag CSI_CHAR_SET_ISSUES_USE_STANDARD_CHARSET_NAME
            scan = new Scanner(new FileInputStream(f), "UTF-8");
            scan.close();
        }

        // tag CSI_CHAR_SET_ISSUES_USE_STANDARD_CHARSET_NAME
        XMLEncoder foo = new XMLEncoder(null, "UTF-8", true, 0);
        foo.close();
        // tag CSI_CHAR_SET_ISSUES_USE_STANDARD_CHARSET_NAME
        new MemoryHandler().setEncoding("UTF-16");
        // tag CSI_CHAR_SET_ISSUES_USE_STANDARD_CHARSET_NAME
        new StreamHandler().setEncoding("UTF-16BE");
        // tag CSI_CHAR_SET_ISSUES_USE_STANDARD_CHARSET_NAME
        Channels.newReader(null, "UTF-8");
        // tag CSI_CHAR_SET_ISSUES_USE_STANDARD_CHARSET_NAME
        Channels.newWriter(null, "UTF-8");
    }
}
