import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class PUS_Sample implements Serializable {
    private int a;
    private int b;

    private class Inner implements Serializable {
        private int c;
        private int d;
    }

    private static class StaticInner implements Serializable {
        private int e;
        private int f;
    }

    public void writeIt(String path) throws IOException {
        Inner i = new Inner();
        FileOutputStream fos = new FileOutputStream(path);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(i);
        oos.writeObject(new StaticInner());
        oos.flush();
        oos.close();
    }

}
