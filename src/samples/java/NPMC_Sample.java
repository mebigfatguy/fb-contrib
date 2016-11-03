import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NPMC_Sample implements Cloneable {
    public void testToString() {
        StringBuilder sb = new StringBuilder();
        sb.append("hello").append("world").toString();
    }

    public void testXValue() {
        Integer i = Integer.valueOf(4);
        i.intValue();
        Long l = Long.valueOf(4);
        l.longValue();
        Double d = Double.valueOf(4);
        d.doubleValue();
        Float f = Float.valueOf(4);
        f.doubleValue();
    }

    public void testEquals(Object o) {
        equals(o);
    }

    public void testHashCode() {
        hashCode();
    }

    @Override
    public NPMC_Sample clone() {
        try {
            super.clone();

            return null;
        } catch (CloneNotSupportedException cnse) {
            throw new Error();
        }
    }

    public String[] testToArrayList() {
        List<String> l = new ArrayList<String>();
        l.toArray();
        Set<String> s = new HashSet<String>();
        s.toArray();

        return null;
    }
}
