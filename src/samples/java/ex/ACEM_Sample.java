package ex;

public abstract class ACEM_Sample implements Foo {

    public static final Runnable fpNOP283 = () -> {
    };

    public void test() {
    }

    public int test1() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void doFPFoo() {
    }

}

interface Foo {
    void doFPFoo();
}