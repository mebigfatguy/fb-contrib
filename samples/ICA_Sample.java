import java.awt.Adjustable;
import java.awt.Color;
import java.awt.image.ImageObserver;

import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JScrollBar;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;


public class ICA_Sample {

    public void testJOptionPane() 
    {
        JOptionPane.showMessageDialog(null, "hi", "there", ImageObserver.ERROR);
    }
    
    public void testBorder() {
        Border b = BorderFactory.createBevelBorder(2, Color.RED, Color.BLUE);
        b = BorderFactory.createEtchedBorder(2, Color.RED, Color.BLUE);
    }
    
    public void testThread() {
        Thread t = new Thread();
        t.setPriority(100);
    }
    
    public void testJScrollBar() {
        JScrollBar b = new JScrollBar(3);
    }
    
    public void fpICA() {
        JOptionPane.showMessageDialog(null, "hi", "there", JOptionPane.ERROR_MESSAGE);
        Border b = BorderFactory.createBevelBorder(BevelBorder.RAISED);
        b = BorderFactory.createEtchedBorder(EtchedBorder.RAISED, Color.RED, Color.BLUE);
        new Thread().setPriority(Thread.MAX_PRIORITY);
        JScrollBar sb = new JScrollBar(Adjustable.HORIZONTAL, 0, 10, 0, 100);
    }
}
