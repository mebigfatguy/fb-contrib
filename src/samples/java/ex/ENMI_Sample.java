package ex;

public class ENMI_Sample {

    public enum Johnny {
        ONE_NOTE
    }

    private Johnny j;

    public ENMI_Sample() {
    }

    public void improperEnum(boolean b, boolean x) {
        j = null;

        if (b) {
            j = Johnny.ONE_NOTE;
            Johnny other = null;

            if (x) {
                other = Johnny.ONE_NOTE;
            }
        }

    }
}
