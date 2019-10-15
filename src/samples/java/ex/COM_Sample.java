package ex;

import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.Set;

public class COM_Sample {

    public void test1() {
    }

    public String test2(int i) {
        return String.valueOf(i);
    }

    public Set<String> test3(String a, String b, String c) {
        Set<String> ss = new HashSet<>();
        ss.add(a);
        ss.add(b);
        ss.add(c);
        return ss;
    }

    public boolean test4(int w, double x, String y, boolean z) {
        return z;
    }

    protected void fpEnhanceAccess() {
    }

    public static class Derived extends COM_Sample {
        @Override
        public void test1() {
        }

        @Override
        public Set<String> test3(String a, String b, String c) {
            Set<String> ss = new HashSet<>();
            ss.add(a);
            ss.add(b);
            ss.add(c);
            return ss;
        }

        @Override
        public String test2(int i) {
            return String.valueOf(i);
        }

        @Override
        public boolean test4(int w, double x, String y, boolean z) {
            return super.test4(w, x, y, z);
        }

        @Override
        public void fpEnhanceAccess() {
        }
    }
}

class OS357 extends ByteArrayOutputStream {

    @Override
    public void close() {
        // removes throws clause
    }
}

interface Inf {
    void m1();

    void m2();
}

abstract class c1 implements Inf {
    @Override
    public void m1() {
    }
}

abstract class c2 extends c1 {
    @Override
    public void m2() {
    }
}

abstract class s1 {
    public static final String FOO = "foo";

    public String getFoo() {
        return FOO;
    }
}

abstract class s2 extends s1 {
    public static final String FOO = "foo2";

    @Override
    public String getFoo() {
        return FOO;
    }
}

class NonSync {
    public int comExtendedSync() {
        return 42;
    }
}

class Sync extends NonSync {
    @Override
    public synchronized int comExtendedSync() {
        return super.comExtendedSync();
    }
}

class ParentGH232 {

    protected long getN() {
        return 2;
    }

}

class ChildGH232 extends ParentGH232 {

    @Override
    protected long getN() {
        return 4;
    }
}

class GH241 {
    private static final String[] STRINGS = new String[] { "" };

    public String[] getStrings() {
        return STRINGS;
    }
}

class Child241 extends GH241 {
    private static final String[] STRINGS = new String[] { "Bar's string" };

    @Override
    public String[] getStrings() {
        return STRINGS;
    }
}