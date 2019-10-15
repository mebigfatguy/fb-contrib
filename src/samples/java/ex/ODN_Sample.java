package ex;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

@SuppressWarnings("all")
public class ODN_Sample {
    public Document testSimpleElement(Document d) {
        Element a = d.createElement("foo");
        Element b = d.createElement("bar");
        d.appendChild(a);
        return d;
    }

    public Document testSimpleAttribute(Document d) {
        Element e = d.createElement("foo");
        d.appendChild(e);
        Attr a1 = d.createAttribute("who");
        Attr a2 = d.createAttribute("dat");

        e.setAttributeNode(a1);
        return d;
    }

    public Document testSimpleText(Document d) {
        Element a = d.createElement("foo");
        Element b = d.createElement("bar");
        d.appendChild(a);
        d.appendChild(b);
        Text t1 = d.createTextNode("hello");
        a.appendChild(t1);
        Text t2 = d.createTextNode("there");
        return d;
    }

    public Element testFP(Document d) {
        Element e = d.createElement("this");
        e.setAttribute("is", "ok");
        return e;
    }

}
