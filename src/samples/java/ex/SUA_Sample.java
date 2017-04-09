package ex;
import java.util.Date;

public class SUA_Sample {
    boolean[] b;

    public int[] getAnArray() {
        return new int[10];
    }

    public char[] normalCase() {
        char[] c = new char[5];
        c[0] = 'h';
        c[1] = 'e';
        c[2] = 'l';
        c[3] = 'l';
        c[4] = 'o';

        return c;
    }

    public boolean[] buildAMemberFP() {
        boolean[] bb = new boolean[2];
        b = bb;
        return bb;
    }

    public String[] getDetailedArray() {
        String[] s = new String[3];

        int sum = 0;
        for (int i = 0; i < 3; i++) {
            sum += s[i].hashCode();
        }

        return s;
    }

    public Date[] ok() {
        return new Date[0];
    }

    public Long[][] getMulti() {
        Long[][] multi = new Long[3][4];
        return multi;
    }

    public float[][] getMultiFP() {
        float[][] multi = new float[1][1];
        multi[0][0] = 1.0f;
        return multi;
    }

    public int[] useMethodToInitArray() {
        int[] i = new int[3];
        initArray(i);
        return i;
    }

    private void initArray(int[] i) {
    }

    public int[] copy(int[] src) {
        int[] dst = new int[src.length];
        System.arraycopy(src, 0, dst, 0, src.length);
        return dst;
    }

    public double[] fpSFBug65tipOff() {
        String[] elems = "1,2.0,3".split(",");
        double[] result = new double[elems.length];
        for (int i = 0; i < elems.length; i++) {
            result[i] = 0.0;
        }
        return result;
    }

    static class ThreadLocalFP extends ThreadLocal<byte[]> {
        @Override
        protected byte[] initialValue() {
            return new byte[256];
        }
    }
}