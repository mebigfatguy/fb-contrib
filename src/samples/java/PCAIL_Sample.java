import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class PCAIL_Sample {
    PCAIL_Sample smpl;

    public void testBasicCase() {
        for (int i = 0; i < 10; i++) {
            PCAIL_Sample sample = new PCAIL_Sample();
            URL u = sample.getClass().getResource("/foo");
        }
    }

    public void fpPutField() {
        for (int i = 0; i < 10; i++) {
            PCAIL_Sample sample = new PCAIL_Sample();
            URL u = sample.getClass().getResource("/foo");
            if (i == 0) {
                smpl = sample;
            }
        }
    }

    public void fpTwoRegs() {
        for (int i = 0; i < 10; i++) {
            PCAIL_Sample sample = new PCAIL_Sample();
            URL u = sample.getClass().getResource("/foo");
            PCAIL_Sample s2 = sample;
        }
    }

    public PCAIL_Sample fpReturnAlloc() {
        for (int i = 0; i < 10; i++) {
            PCAIL_Sample sample = new PCAIL_Sample();
            URL u = sample.getClass().getResource("/foo");
            if (u != null) {
                return sample;
            }
        }

        return null;
    }

    public void fpMethodParm() {
        for (int i = 0; i < 10; i++) {
            PCAIL_Sample sample = new PCAIL_Sample();
            if (sample.equals(sample)) {
                return;
            }
        }
    }

    public List<PCAIL_Sample> fpAnonymousMethodParm() {
        List<PCAIL_Sample> col = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            col.add(new PCAIL_Sample());
        }

        return col;
    }

    public List<PCAIL_Sample> fpAnonymousBuilder() {
        List<PCAIL_Sample> col = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            col.add(new PCAIL_Sample().builder());
        }

        return col;
    }

    public void fpArrayStore() {
        PCAIL_Sample[] samples = new PCAIL_Sample[3];
        for (int i = 0; i < 3; i++) {
            samples[i] = new PCAIL_Sample();
        }
    }

    public void fpThrow() {
        for (int i = 0; i < 3; i++) {
            if (i == 3) {
                throw new RuntimeException();
            }
        }
    }

    public void fpTwoAssigns() {
        for (int i = 0; i < 10; i++) {
            Set<String> s;

            if ((i % 2) == 0) {
                s = new HashSet<>();
            } else {
                s = new TreeSet<>();
            }

            s.add(String.valueOf(i));
        }
    }

    public void fpChaining() {
        List<Foo> list = new ArrayList<>();
        for (int i = 0; i <= 10; i++) {
            list.add(new Foo().withNumber(i));
        }
    }

    public List<String> fpPreUse() {
        List<String> l = null;

        while (true) {
            if (l != null) {
                return l;
            }

            l = new ArrayList<>();
            l.add("Foo");

            for (String s : l) {
                System.out.println(s);
            }
        }
    }

    public String fpSwitch(List<String> ss, int i) {
        for (String s : ss) {
            switch (i) {
                case 0:
                    List<String> n = new ArrayList<>();
                    n.add(s);
                    if (n.isEmpty()) {
                        return "yup";
                    }
                break;

                case 1:
                    return null;
            }
        }

        return null;
    }

    public void fpFooBar(List<Bar> barList) {
        List<Foo> fooList = new ArrayList<>();
        Foo foo;
        for (Bar bar : barList) {
            foo = new Foo();
            foo.setAny(bar.getAny());
            fooList.add(foo);
        }
    }

    private PCAIL_Sample builder() {
        return this;
    }

    static class Foo {
        public Foo withNumber(int i) {
            return this;
        }

        public void setAny(String s) {
        }
    }

    static class Bar {
        public String getAny() {
            return "";
        }
    }
}
