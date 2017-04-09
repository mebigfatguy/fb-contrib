package ex;
import java.awt.Color;
import java.awt.Container;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;

@SuppressWarnings("all")
public class S508C_Sample extends JFrame {
    private JLabel fLabel = new JLabel("Hello");
    private JLabel imgLabel = new JLabel(new ImageIcon("/boo.gif"));
    private JComponent c = new MyComponent();

    public S508C_Sample() {
        Container cp = getContentPane();
        cp.setLayout(null);

        cp.add(fLabel);
        JLabel lLabel = new JLabel("there");
        lLabel.setBackground(new Color(255, 0, 0));
        lLabel.setForeground(new Color(255, 255, 100));
        cp.add(lLabel);

        JLabel picLabel = new JLabel(new ImageIcon("/foo.gif"));
        cp.add(picLabel);
        cp.add(c);

        setSize(300, 200);
    }

    public void testI18N() {
        JLabel l = new JLabel("Hello");
        JFrame f = new JFrame("foo");
    }

    public void testAppending(String greeting, String user) {
        JLabel l = new JLabel(greeting + " " + user);
    }

    public void fpAppending(String greeting) {
        JLabel l = new JLabel("<html><body>" + greeting + "</body></html>");
    }
}

class MyComponent extends JComponent {
    private static final long serialVersionUID = -628028159110180711L;
}
