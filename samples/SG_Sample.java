import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.ByteArrayInputStream;
import java.io.FileReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;

@SuppressWarnings("all")
public class SG_Sample implements ActionListener, WindowListener {
    String s = "<xml/>";

    public void actionPerformed(ActionEvent ae) {
        FileReader fr = null;
        try {
            fr = new FileReader("c:/a.out");
        } catch (Exception e) {
        } finally {
            try {
                fr.close();
            } catch (Exception ee) {
            }
        }

    }

    public void windowClosing(WindowEvent we) {
        String s = getRoot();
    }

    private String getRoot() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document d = db.parse(new ByteArrayInputStream(s.getBytes()));
            return d.getDocumentElement().getNodeName();
        } catch (Exception e) {
            return "";
        }
    }

    public void windowActivated(WindowEvent arg0) {
    }

    public void windowClosed(WindowEvent arg0) {
    }

    public void windowDeactivated(WindowEvent arg0) {
    }

    public void windowDeiconified(WindowEvent arg0) {
    }

    public void windowIconified(WindowEvent arg0) {
    }

    public void windowOpened(WindowEvent arg0) {
    }
}
