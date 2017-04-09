package ex;
import java.util.HashSet;
import java.util.Set;

public class CVAA_Sample {
    Base[] b2;

    class Base {
    }

    class Derived extends Base {
    }

    public void cvaa() {
        Derived[] d2 = new Derived[4];
        Base[] b = d2;
        b2 = d2;
        Derived d = new Derived();
        b[0] = new Base();
        b[1] = doDerived();
        b[2] = null;
        b[3] = d;

        Integer[] a = new Integer[1];
        System.arraycopy(a[0], 0, a, 1, 1);
    }

    private Derived doDerived() {
        return new Derived();
    }

    public void generics() {
        Set<String>[] myStringSets = new HashSet[2];
        Set<String>[] myStringSets2 = new Set[2];
    }

    public String fpVarArgs(String s1, Set<String> s2) {
        return String.format("s1=%s s2=%s", s1, s2);
    }
}
