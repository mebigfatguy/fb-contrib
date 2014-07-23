import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.log4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("all")
public class LO_Sample {
	//tag LO_SUSPECT_LOG_CLASS 
	private static Logger l1 = Logger.getLogger(String.class);
	//tag LO_SUSPECT_LOG_CLASS 
	private static Logger l2 = Logger.getLogger("com.foo.LO_Sample");
	//no tag
	private static final org.slf4j.Logger l3 = LoggerFactory.getLogger(LO_Sample.class);
	//tag LO_SUSPECT_LOG_CLASS
	private static Logger l4 = Logger.getLogger(ActionEvent.class.getName());
	//no tag
	private static Logger l5 = Logger.getLogger(LO_Sample.class.getName());
	
	//no tag
	private Logger someLocalLogger;
	
	
	//no tag
	public LO_Sample() {
		this(Logger.getRootLogger());

		someLocalLogger.info("Why am I using a local logger?");
	}

	//tag LO_SUSPECT_LOG_PARAMETER 
	public LO_Sample(Logger someLogger) 
	{
		this.someLocalLogger = someLogger;
	} 



	public void testStutter() throws IOException {
		InputStream is = null;
		try {
			File f = new File("Foo");
			is = new FileInputStream(f);
		} catch (Exception e) {
			//tag LO_STUTTERED_MESSAGE 
			l1.error(e.getMessage(), e);
		} finally {
			is.close();
		}
	}

	public void testParmInExMessage() throws Exception{
		try {
			InputStream is = new FileInputStream("foo/bar");
		} catch (IOException e) {
			//tag LO_EXCEPTION_WITH_LOGGER_PARMS 
			throw new Exception("Failed to parse {}", e);
		}
	}

	public void testInvalidSLF4jParm() {
		//tag LO_INVALID_FORMATTING_ANCHOR
		l3.error("This is a problem {0}", "hello");
	}

	public void testLogAppending(String s) {
		try {
			//tag LO_APPENDED_STRING_IN_FORMAT_STRING 
			l3.info("Got an error with: " + s);
		} catch (Exception e) {
			l3.warn("Go a bad error with: " + s, e);
		}
	}

	public void testWrongNumberOfParms() {
		//tag LO_INCORRECT_NUMBER_OF_ANCHOR_PARAMETERS 
		l3.error("This is a problem {}", "hello", "hello");
		//tag LO_INCORRECT_NUMBER_OF_ANCHOR_PARAMETERS 
		l3.error("This is a problem {} and this {}", "hello");
		//tag LO_INCORRECT_NUMBER_OF_ANCHOR_PARAMETERS 
		l3.error("This is a problem {} and this {} and this {}", "hello", "world");
		//tag LO_INCORRECT_NUMBER_OF_ANCHOR_PARAMETERS 
		l3.error("This is a problem {} and this {} and this {} and this {}", "hello", "hello", "hello");

		//no tag
		l3.error("This is a problem {} and this {} and this {} and this {}", "hello", "hello", "hello", "hello");
	}

	public void testFPWrongNumberOfParms() {
		//no tag An additional exception argument is allowed if found
		l3.error("This is a problem {}", "hello", new IOException("Yikes"));
		//no tag An additional exception argument is allowed if found
		l3.error("This is a problem {} and this {} and this {} and this {}", "hello", "hello", "hello", "hello", new RuntimeException("yikes"));
		//no tag
		l3.error("This is a problem {} and this {}", "hello", new RuntimeException("yikes"));
	}

	public class Inner {
		public void fpUseAnon() {
			ActionListener l = new ActionListener() {
				public void actionPerformed(ActionEvent ae) {
					//no tag
					Logger.getLogger(Inner.class).error("fp");
					//tag LO_SUSPECT_LOG_CLASS 
					Logger.getLogger(LO_Sample.class).error("not fp");
				}
			};
		}
	}
}
