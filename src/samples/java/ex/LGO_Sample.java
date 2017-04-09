package ex;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class LGO_Sample {

    public void testCreate(Graphics g) {
        Graphics g2 = g.create();
    }

    public void testCreateg2D(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
    }

    public void testBufferedImage(BufferedImage bi) {
        Graphics g = bi.getGraphics();
    }

    public void fpWasDisposed(Graphics g) {
        Graphics g2 = g.create();
        try {

        } finally {
            g2.dispose();
        }
    }

    public void fpG2DWasDisposed(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {

        } finally {
            g2.dispose();
        }
    }

    public Graphics returnG(Graphics g) {
        return g.create();
    }
}
