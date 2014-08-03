import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.methods.HttpPut;
import org.apache.log4j.Logger;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

@SuppressWarnings("all")
public class OCP_Sample extends Z implements ActionListener, Serializable {
	
	private static final Logger logger = Logger.getLogger(OCP_Sample.class);
	
	//tag OCP, hashset could be Set instead
    public String getDisplay(HashSet<String> s, String a, String b) {
        if (s.contains(a)) {
            s.add(b);
        } else {
            s.add(a + b);
        }

        StringBuilder sb = new StringBuilder();
        Iterator<String> it = s.iterator();
        while (it.hasNext()) {
            sb.append(it.next());
        }
        return sb.toString();
    }

    //tag OCP dh could be a ContentHandler instead
    public void parse(DefaultHandler dh, File f) throws SAXException, ParserConfigurationException, IOException {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        SAXParser sp = spf.newSAXParser();
        XMLReader xr = sp.getXMLReader();

        xr.setContentHandler(dh);
        xr.parse(new InputSource(new FileInputStream(f)));
    }

    //no tag, x only exists in B, not A
    public void falsePositive(B b) {
        b.test();
        b.x = 4;
    }

    //no tag, we are returning the Color object
    public Color fpGetColor(Color c) {
        return c;
    }

    public static interface A {
        public void test();

        public void fp() throws IOException;
    }

    public static class B implements A {
        public int x = 0;

        public void test() {

        }

        public void fp() {

        }
    }
    
    //no tag, we are overriding actionPerformed()
    public void actionPerformed(ActionEvent ae) {

    }

    //no tag, B's fp() doesn't throw an exception, but its interface (A) does
    public void ocpFalseFPDueToExceptionSig(B b) {
        b.fp();
    }

    @Override  //no tag, override
    public void usesOCP(LinkedList<String> ll) {
        ll.add("foo");
    }

    //no tag, Storing the object
    public void testFPaastore(Color c) {
        Color[] cc = new Color[] { c, c };
    }

    //no tag, isn't used
    private void readObject(ObjectInputStream ois) {

    }
    
    
    //tag OCP request -> HTTPMessage
    public static void httpComponent(HttpPut request, String auth) {
    	auth = auth + "password";
    	request.addHeader("Authorization", Base64.encodeBase64String(auth.getBytes(StandardCharsets.UTF_8)));
    }
  
    
    //should tag OCP request -> HTTPMessage, but doesnt
    public static void httpComponentWithTryFalseNegative(HttpPut request, String auth) {
    	auth = auth + "password";
    	try {
    		//this will probably be tagged with CSI_CHAR_SET_ISSUES_USE_STANDARD_CHARSET, if compiled under JDK 7 or later
			request.addHeader("Authorization", Base64.encodeBase64String(auth.getBytes("UTF-8")));
		} catch (UnsupportedEncodingException e) {
			logger.fatal("There was a problem encoding "+ auth, e);
		}
    }
    
    //tag OCP request -> HTTPMessage 
    public static void httpComponentWithTry(HttpPut request, String auth) {
    	auth = auth + "password";
    	try {
    		//this will probably be tagged with CSI_CHAR_SET_ISSUES_USE_STANDARD_CHARSET, if compiled under JDK 7 or later
			request.addHeader("Authorization", Base64.encodeBase64String(auth.getBytes("UTF-8")));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
    }
    
}

class Z {
    @SuppressWarnings("unused")
	public void usesOCP(LinkedList<String> ll) {
    }
}

class fpOverride {
    public static final Comparator<Date> COMPARATOR = new Comparator<Date>() {

    	@Override   //no tag, override
        public int compare(Date o1, Date o2) {
            return o1.getYear() - o2.getYear();
        }
    }; 
}
