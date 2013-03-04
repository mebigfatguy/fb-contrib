import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;


public class PSC_Sample {

    public void testPSC(List<PSC_Sample> samples) {
        Set<String> names = new HashSet<String>();
        for (PSC_Sample s : samples) {
            names.add(s.toString());
        }
    }

    public void testPSCEnumerated() {
        Set<String> commonWords = new HashSet<String>();
        commonWords.add("a");
        commonWords.add("an");
        commonWords.add("the");
        commonWords.add("by");
        commonWords.add("of");
        commonWords.add("and");
        commonWords.add("or");
        commonWords.add("in");
        commonWords.add("with");
        commonWords.add("my");
        commonWords.add("I");
        commonWords.add("on");
        commonWords.add("over");
        commonWords.add("under");
        commonWords.add("it");
        commonWords.add("they");
        commonWords.add("them");
    }

    public void testFPDontHaveCollectionForSizing(Iterator<Long> it) {
        Deque<Long> ad = new ArrayDeque<Long>();
        while (it.hasNext()) {
            ad.add(it.next());
        }
    }
}
