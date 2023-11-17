package ex;

import ex.SPP_Sample.Flap;

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

    public void equalsOnEnum(Flap f) {
        if (f.equals(Flap.Jack)) {
            System.out.println("Flap Jacks");
        }
    }
}

enum ENMIFP {
    C1 {
    },
    C2 {
    }
}
