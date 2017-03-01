import java.util.List;
import java.util.Map;

public class SLS_Sample {
    private String name;
    private int age;
    private int height;

    SLS_Sample hasIt(List<SLS_Sample> l, String n) {

        SLS_Sample found = null;
        for (SLS_Sample s : l) {
            if (s.name.equals(n)) {
                found = s;
            }
        }

        return found;
    }

    SLS_Sample hasAge(List<SLS_Sample> l, int age) {

        SLS_Sample found = null;
        for (SLS_Sample s : l) {
            if (s.age == age) {
                found = s;
            }
        }

        return found;
    }

    SLS_Sample fpBreak(List<SLS_Sample> l, String n) {

        SLS_Sample found = null;
        for (SLS_Sample s : l) {
            if (s.name.equals(n)) {
                found = s;
                break;
            }
        }

        return found;
    }

    SLS_Sample fpReturn(List<SLS_Sample> l, String n) {

        SLS_Sample found = null;
        for (SLS_Sample s : l) {
            if (s.name.equals(n)) {
                found = s;
                return found;
            }
        }

        return found;
    }

    boolean fpSetFlag(Map<String, SLS_Sample> m, String name) {
        boolean found = false;
        for (SLS_Sample s : m.values()) {
            if ((s == null) || s.name.equals(name)) {
                found = true;
                s.age = 1;
            }
        }

        return found;
    }

    int fpCalcTotal(List<SLS_Sample> l, String name) {
        int total = 0;
        for (SLS_Sample s : l) {
            if (s.name.equals(name)) {
                total += s.age;
            }
        }

        return total;
    }

    void fpTwoSets(List<SLS_Sample> l, String name, int age, int height) {
        String n = null;
        int a = 0;
        int h = 0;

        for (SLS_Sample s : l) {
            if (s.name.equals(name)) {
                n = s.name;
            } else if (s.age == age) {
                a = s.age;
            } else if (s.height == height) {
                h = s.height;
            }
        }

        System.out.println("Found: " + n + " " + a + " " + h);
    }

}
