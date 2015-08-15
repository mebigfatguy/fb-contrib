import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

public class SCII_Sample extends OverEndulgentParent implements MouseListener {
    @Override
    public void mouseClicked(MouseEvent arg0) {
    }
}

class OverEndulgentParent {
    public void mousePressed(MouseEvent arg0) {
    }

    public void mouseReleased(MouseEvent arg0) {
    }

    public void mouseEntered(MouseEvent arg0) {
    }

    public void mouseExited(MouseEvent arg0) {
    }

    interface A {
        public void a();

        public void b();

        public void c();
    }

    interface B extends A {
        @Override
        public void b();
    }

    interface C extends B {
        @Override
        public void c();
    }

    class AA implements A {
        @Override
        public void a() {
        }

        @Override
        public void b() {
        }

        @Override
        public void c() {
        }
    }

    class BB extends AA implements B {

    }

    class CC extends BB implements C {

    }
}
