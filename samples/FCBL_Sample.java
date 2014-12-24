import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Locale;

import javax.swing.SwingUtilities;

@SuppressWarnings("all")
public final class FCBL_Sample {
    public int foo;				//no warning (scope)
    private int moo;			//warning
    private int boo;			//warning
    int hoo;					//no warning (scope)
    private int fp;				//no warning (read in method test2)	
    private int multiMethodFP;	//no warning (used in a couple methods)
    private String test;		//warning
    private String testNestedIfs;//no warning (in one branch of the if, this is read first)
    private int x = 1;			//warning
    private int y = 2;			//no warning (read first in method1)
    private boolean ret;		//warning (shielded in foo)
    @FooAnnotation
    private String notUsed;		//warning 
    @FooAnnotation
    private String used;		//no warning (annotation+stored)

    private String fooS = "Foo";	//warning
    private String[] fooSS = { fooS };	//no warning (read in testInArray)

    public FCBL_Sample() {
        foo = 0;
        moo = 1;
        boo = 2;
        hoo = 3;
        fp = 4;
        used = "Hello";
        ret = false;
        
        //to mask standard URF_UNREAD_FIELD 
        System.out.println(foo + hoo + used + moo + boo + ret);
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
        if ("boo".equals(in))
            test = "boo";
        else if ("hoo".equals(in))
            test = "hoo";
        else if ("moo".equals(in)) {
            if ("loo".equals(in) && !found) {
                found = true;
            }
        }

        test = "WooWoo".toLowerCase(Locale.ENGLISH);		//hides the PME warning and the standard DM_CONVERT_CASE 
        if (in.length() > 1) {
        	test = "woowoo";
        	System.out.println(test);
        }
    }
    
    public void testNestedIfs(String in) {
        boolean found = false;
        if ("boo".equals(in))
            testNestedIfs = "boo";
        else if ("hoo".equals(in))
        	testNestedIfs = "hoo";
        else if ("moo".equals(in)) {
            if ("loo".equals(in) && !found) {
                System.out.println(in+testNestedIfs);
            }
        }

        testNestedIfs = "WooWoo".toLowerCase(Locale.ENGLISH);		//hides the PME warning and the standard DM_CONVERT_CASE 
        if (in.length() > 1) {
        	testNestedIfs = "woowoo";
        }
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

    static class Foo {
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
