import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

@SuppressWarnings("all")
public class NIR_Sample extends JFrame {
    public static final int NIR_VALUE = 1;

    public NIR_Sample getSample() {
        return this;
    }

    public void test1() {
        System.out.println(getSample().NIR_VALUE);
    }

    public void test2() {
        System.out.println(getColorModel().TRANSLUCENT);
    }

    public void test3() {
        getSample().staticMethod();
    }

    public void fpStacked() {
        JButton ok = new JButton("ok");
        JButton cancel = new JButton("cancel");
        JPanel p = new JPanel();
        p.add(ok);
        p.add(cancel);
    }

    public static void staticMethod() {
    }
}
