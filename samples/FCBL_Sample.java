import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.swing.SwingUtilities;

@SuppressWarnings("all")
public final class FCBL_Sample {
    public int foo;
    private int moo;
    protected int boo;
    int hoo;
    private int fp;
    private int multiMethodFP;
    private String test;
    private int x = 1;
    private int y = 2;
    private boolean ret;
    @FooAnnotation
    private String notUsed;
    @FooAnnotation
    private String used;

    private String fooS = "Foo";
    private String[] fooSS = { fooS };

    public FCBL_Sample() {
        foo = 0;
        moo = 1;
        boo = 2;
        hoo = 3;
        fp = 4;
        used = "Hello";
    }

    public void method1() {
        x = 50;
        System.out.println(x);
        System.out.println(y);
    }

    public void test1() {
        foo = 2;
        moo = 3;
        boo = 4;
        fp = 5;
    }

    public void testInArray() {
        for (String s : fooSS) {
        }
    }

    public void test2() {
        boo = fp;
    }

    public void test3(String in) {
        boolean found = false;
        if (in.equals("boo"))
            test = "boo";
        else if (in.equals("hoo"))
            test = "hoo";
        else if (in.equals("moo")) {
            if (test.equals("loo") && !found) {
                found = true;
            }
        }

        test = "woowoo";
    }

    public void mm1FP(int i) {
        multiMethodFP = i;
        mm2FP(3);

        if (multiMethodFP == i) {
            System.out.println("FP");
        }
    }

    public void mm2FP(int i) {
        multiMethodFP = i;
    }

    class Foo {
        boolean ret;

        public boolean testFPAnon() {
            ret = false;

            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    ret = false;
                }
            });

            return ret;
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface FooAnnotation {

    }
}
