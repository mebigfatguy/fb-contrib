import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class PSC_Sample {

    public void testPSC(List<PSC_Sample> samples) {
        Set<String> names = new HashSet<String>();
        for (PSC_Sample s : samples) {
            names.add(s.toString());
        }
    }

}
