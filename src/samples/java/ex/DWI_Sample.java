package ex;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class DWI_Sample {
    Set<String> avail;
    List<String> cow = new CopyOnWriteArrayList<String>();

    public void deleteOdds(Set<Integer> bagOInts) {
        Iterator<Integer> it = bagOInts.iterator();
        while (it.hasNext()) {
            Integer i = it.next();
            if ((i.intValue() & 0x01) == 1) {
                bagOInts.remove(i);
            }
        }
    }

    public void addIf(Set<String> s, Collection<String> c) {
        for (String ss : s) {
            if (ss.equals("addem")) {
                s.addAll(c);
            }
        }
    }

    public void fpUnaliased() {
        Iterator<String> it = avail.iterator();
        avail = new HashSet<String>();

        while (it.hasNext()) {
            avail.add(it.next() + "booya");
        }
    }

    public void fpWithBreak(Set<String> ss) {
        for (String s : ss) {
            if (s.equals("foo")) {
                ss.remove("foo");
                break;
            }
        }
    }

    public void fpClearWithBreak(Set<String> ss) {
        for (String s : ss) {
            if (s.equals("foo")) {
                ss.clear();
                break;
            }
        }
    }

    public void fpRemoveWithReturn(Set<String> ss) {
        for (String s : ss) {
            if (s.equals("foo")) {
                ss.remove("foo");
                return;
            }
        }
    }

    public boolean fpRemoveWithReturn2(Set<String> ss) {
        for (String s : ss) {
            if (s.equals("foo")) {
                ss.remove("foo");
                return true;
            }
        }

        return false;
    }

    public void fpNonCMECollection() {
        for (String s : cow) {
            if (s.isEmpty()) {
                cow.remove(s);
            }
        }
    }
}
