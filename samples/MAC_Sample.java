
public class MAC_Sample {
    public void test(String[] a) {
        String[] b = new String[a.length];
        for (int i = 0; i < a.length; i++)
            b[i] = a[i];
    }

    public void testFP(String[] a) {
        String[] b = new String[a.length];
        int insPos = 0;
        for (int i = 0; i < a.length; i++) {
            if (!a[0].equals("*"))
                b[insPos++] = a[i];
        }
    }
}
