package ex;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.log4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("all")
public class LO_Sample {
    // tag LO_SUSPECT_LOG_CLASS
    private static Logger l1 = Logger.getLogger(String.class);
    // no tag
    private static Logger l2 = Logger.getLogger("com.foo.LO_Sample");
    // no tag
    private static final org.slf4j.Logger l3 = LoggerFactory.getLogger(LO_Sample.class);
    // tag LO_SUSPECT_LOG_CLASS
    private static Logger l4 = Logger.getLogger(ActionEvent.class.getName());
    // no tag
    private static Logger l5 = Logger.getLogger(LO_Sample.class.getName());
    // no tag
    private static Logger l6 = Logger.getLogger("my.nasty.logger.LOGGER");

    // no tag
    private Logger someLocalLogger;

    // no tag
    public LO_Sample() {
        this(Logger.getRootLogger());

        someLocalLogger.info("Why am I using a local logger?");
    }

    // tag LO_SUSPECT_LOG_PARAMETER
    public LO_Sample(Logger someLogger) {
        this.someLocalLogger = someLogger;
    }

    public void testStutter() throws IOException {
        InputStream is = null;
        try {
            File f = new File("Foo");
            is = new FileInputStream(f);
        } catch (Exception e) {
            // tag LO_STUTTERED_MESSAGE
            l1.error(e.getMessage(), e);
        } finally {
            is.close();
        }
    }

    public void testParmInExMessage() throws Exception {
        try {
            InputStream is = new FileInputStream("foo/bar");
        } catch (IOException e) {
            // tag LO_EXCEPTION_WITH_LOGGER_PARMS
            throw new Exception("Failed to parse {}", e);
        }
    }

    public void testInvalidSLF4jParm() {
        // tag LO_INVALID_FORMATTING_ANCHOR
        l3.error("This is a problem {0}", "hello");
    }

    public void testInvalidSLF4jParm2() {
        // tag LO_INVALID_FORMATTING_ANCHOR
        l3.error("This is a problem %s", "hello");
        l3.error("This is a problem %3$-2.3e", 1);
        l3.error("This is a problem %5d", 3.1);
    }

    public void testLogAppending(String s) {
        try {
            // tag LO_APPENDED_STRING_IN_FORMAT_STRING
            l3.info("Got an error with: " + s);
        } catch (Exception e) {
            l3.warn("Go a bad error with: " + s, e);
        }
    }

    public void testWrongNumberOfParms() {
        // tag LO_INCORRECT_NUMBER_OF_ANCHOR_PARAMETERS
        l3.error("This is a problem {}", "hello", "hello");
        // tag LO_INCORRECT_NUMBER_OF_ANCHOR_PARAMETERS
        l3.error("This is a problem {} and this {}", "hello");
        // tag LO_INCORRECT_NUMBER_OF_ANCHOR_PARAMETERS
        l3.error("This is a problem {} and this {} and this {}", "hello", "world");
        // tag LO_INCORRECT_NUMBER_OF_ANCHOR_PARAMETERS
        l3.error("This is a problem {} and this {} and this {} and this {}", "hello", "hello", "hello");

        // no tag
        l3.error("This is a problem {} and this {} and this {} and this {}", "hello", "hello", "hello", "hello");
    }

    public void testSimpleFormatInLogger(String poo) {
        l3.error(String.format("The error was %s", poo));
    }

    public void testToStringInParm(List<Long> data) {
        l3.info("This is a parm: {}", data.toString());
    }

    public String testReuseToStringInParm(List<Long> data) {
        String info = data.toString();
        l3.info("This is a parm: {}", info);
        return info;
    }

    public void testFPReuseofSimpleFormatter(String poo) {
        String s = String.format("The error was %s", poo);
        l3.error(s);
        throw new RuntimeException(s);
    }

    public void testFPWrongNumberOfParms() {
        // no tag An additional exception argument is allowed if found
        l3.error("This is a problem {}", "hello", new IOException("Yikes"));
        // no tag An additional exception argument is allowed if found
        l3.error("This is a problem {} and this {} and this {} and this {}", "hello", "hello", "hello", "hello", new RuntimeException("yikes"));
        // no tag
        l3.error("This is a problem {} and this {}", "hello", new RuntimeException("yikes"));
    }

    public void testFPRealStringBuilderUser(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) {
            sb.append(s).append(":");
        }

        l3.warn(sb.toString());
    }

    public void fpOKPattern(File f) {
        l3.error("Specify the path to {} with %TEMP% or using system property", f);
    }

    public class Inner {
        public void fpUseAnon() {
            ActionListener l = new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent ae) {
                    // no tag
                    Logger.getLogger(Inner.class).error("fp");
                    // tag LO_SUSPECT_LOG_CLASS
                    Logger.getLogger(LO_Sample.class).error("not fp");
                }
            };
        }
    }
}
