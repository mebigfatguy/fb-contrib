import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

@SuppressWarnings("all")
public class OCP_Sample extends Z implements ActionListener, Serializable
{
	public String getDisplay(HashSet<String> s, String a, String b)
	{
		if (s.contains(a)) {
			s.add(b);
		} else {
			s.add(a + b);
		}

		StringBuffer sb = new StringBuffer();
		Iterator<String> it = s.iterator();
		while (it.hasNext()) {
			sb.append(it.next());
		}
		return sb.toString();
	}

	public void parse(DefaultHandler dh, File f)
		throws SAXException, ParserConfigurationException, IOException
	{
		SAXParserFactory spf = SAXParserFactory.newInstance();
		SAXParser sp = spf.newSAXParser();
		XMLReader xr = sp.getXMLReader();

		xr.setContentHandler( dh );
		xr.parse(new InputSource( new FileInputStream(f)));
	}

	public void falsePositive(B b)
	{
		b.test();
		b.x = 4;
	}

	public Color fpGetColor(Color c) {
		return c;
	}

	public static interface A
	{
		public void test();

		public void fp() throws IOException;
	}

	public static class B implements A
	{
		public int x = 0;

		public void test()
		{

		}

		public void fp()
		{

		}
	}

	public void actionPerformed(ActionEvent ae)
	{

	}

	public void ocpFalseFPDueToExceptionSig(B b)
	{
		b.fp();
	}

	@Override
	public void usesOCP(LinkedList<String> ll)
	{
		ll.add("foo");
	}

	public void testFPaastore(Color c)
	{
		Color[] cc = new Color[] { c, c };
	}

	private void readObject(ObjectInputStream ois)
	{

	}
}

class Z
{
	public void usesOCP(LinkedList<String> ll)
	{
	}
}

