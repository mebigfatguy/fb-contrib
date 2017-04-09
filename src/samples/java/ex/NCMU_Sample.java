package ex;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

@SuppressWarnings("all")
public class NCMU_Sample {
    public void test(Vector v, Hashtable ht) {
        if (ht.contains("test")) {
            Enumeration e = ht.elements();
            if (e.equals(ht.keys()))
                return;
        }

        v.addElement("test");
        String s = (String) v.elementAt(0);
        v.insertElementAt("test", 0);
        v.removeAllElements();
        v.removeElement("test");
        v.removeElementAt(0);
        v.setElementAt("test", 0);

    }
}