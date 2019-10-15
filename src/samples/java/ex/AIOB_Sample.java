package ex;

import java.util.List;

public class AIOB_Sample {
    int[] fa = new int[4];
    int[] fb;
    String[] unknownFields = new String[] {};

    public void testOutOfBounds() {
        int[] a = new int[4];

        a[4] = 2;
        fa[4] = 2;
    }

    public void testOutOfBoundsGuard() {
        int[] a = new int[4];

        if (a.length > 4) {
            a[4] = 2;
        }
    }

    public void testUnallocated() {
        int[] b = null;

        b[4] = 4;
        fb[4] = 4;
    }

    public void testSystemArrayCopy() {
        String[] s = { "Fee", "Fi", "Fo", "Fum" };

        String[] copy = new String[s.length];
        System.arraycopy(s, 1, copy, 1, s.length);
    }

    public int[] fpPlusPlusNotRecognized(List<String> l) {
        int size = 0;

        for (String s : l) {
            size++;
        }

        int[] data = new int[size];

        data[0] = 0;
        return data;
    }

    public void fpPostAllocate() {
        double[] da = null;

        for (int i = 0; i < 10; i++) {
            if ((i & 1) == 1) {
                da[0] = 0.0;
            }

            if (da == null) {
                da = new double[10];
            }
        }
    }

    public void fpPlusEquals(List<String> ss) {
        int size = 0;

        for (String s : ss) {
            size += s.length();
        }

        int[] a = new int[size];

        a[0] = 1;
    }

    public String[] fpCopyFieldOfUnknownSize() {
        String[] fields = new String[unknownFields.length];
        System.arraycopy(unknownFields, 0, fields, 0, unknownFields.length);

        return fields;
    }
}
