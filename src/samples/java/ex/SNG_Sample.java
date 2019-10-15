package ex;

import java.io.File;

public class SNG_Sample {
    private static byte[] EMPTY_BYTE_ARRAY = new byte[0];
    private Object f1 = null;
    private final Object f2 = null;
    private final File file = null;
    private final byte[] buffer = null;
    private final SNG_Sample otherSample = null;

    public void badSNGFields() {
        if (f1 != null) {
            f1 = "Foo";
        }
    }

    public void badSNGLocals(Object l1, Object l2) {
        if (l1 != null) {
            l1 = l2;
        }
    }

    public void fpNGFieldSetToNull() {
        if (f1 != null) {
            f1 = null;
        }
    }

    public void fpNGLocalSetToNull(String s1) {
        if (s1 != null) {
            s1 = null;
        }
    }

    public void fpSelfAdjustingLocal(String s) {
        if (s != null) {
            s = s.trim();
        }
    }

    public void fpSelfAdjustingField() {
        if (f1 != null) {
            f1 = f1.toString();
        }
    }

    public void fpOtherParmObject(SNG_Sample s) {

        if (s.f1 != null) {
            this.f1 = s.f1;
        }
    }

    public void fpOtherFieldObject() {

        if (otherSample.f1 != null) {
            this.f1 = otherSample.f1;
        }
    }
}
