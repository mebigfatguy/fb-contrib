package ex;
public class PME_Sample {

    private int v;
    private String s;
    private String fps;
    private boolean fpb;

    public void foo() {
        v = 1;
        s = "Fee";
        fps = "Hello";
        fpb = true;
    }

    public void foo2() {
        v = -2;
        s = "Fi";
        fps = "Hi";
        fpb = false;
    }

    public void foo3() {
        v = 3;
        s = "Fo";
        fps = "Chow";
    }

    public void foo4(String x) {
        fps = x;
    }
}
