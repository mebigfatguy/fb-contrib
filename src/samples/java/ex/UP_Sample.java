package ex;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class UP_Sample implements Serializable {

    private int testUP1(int x, double d, float f, char c) {
        if (f == 0f) {
            return x;
        }

        return 0;
    }

    public static int testUP2(int x, double d, float f, char c) {
        if (f == 0f) {
            return x;
        }

        return 0;
    }

    public int fpTestUP3(int x, double d, float f, char c) {
        if (f == 0f) {
            return x;
        }

        return 0;
    }

    public UP_Sample(int x, double d, float f, char c) {
        if (f == 0f) {
            x = 1;
        }

        c += x;
    }

    private void readObject(ObjectInputStream in) {

    }

    private void writeObject(ObjectOutputStream out) {

    }
}
