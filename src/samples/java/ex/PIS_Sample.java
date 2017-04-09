package ex;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

@SuppressWarnings("all")
public class PIS_Sample {
    public static void main(String[] args) {
        try {
            B b = new B();
            b.a = 100;
            b.b = 100;
            D d = new D();
            d.a = 100;
            d.b = 100;
            d.c = 100;
            d.d = 100;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(b);
            oos.writeObject(d);
            oos.flush();
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            B b2 = (B) ois.readObject();
            D d2 = (D) ois.readObject();
            if ((b.a == b2.a) && (b.b == b2.b))
                System.out.println("Equal!");
            if ((d.a == d2.a) && (d.b == d2.b) && (d.c == d2.c) && (d.d == d2.d))
                System.out.println("Equal!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class A {
        public int a = 0;
    }

    public static class B extends A implements Serializable {
        public int b = 1;
    }

    public static class C extends B {
        public int c = 2;
    }

    public static class D extends C {
        public int d = 3;
    }
}
