package ex;

public class FPL_Sample {
    public void testFloat() {
        for (float f = 0.0f; f < 10.0f; f += 1.0f) {
        }
    }

    public void testDouble() {
        for (double d = 0.0; d < 10.0; d += 1.0) {
        }
    }

    public void testTougherFloat(String s) {
        for (float f = 0.0f; f < Double.valueOf(s).doubleValue(); f += 1.0f) {
        }

    }
}