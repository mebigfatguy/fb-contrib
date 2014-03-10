import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.util.Collections;


public class MUC_Sample {

    public void testMUC() {
        List<String> l = getImmutableList();
        
        l.add("Uhoh");
    }
    
    public void testPossiblyMUC() {
        Set<String> s = getPossiblyImmutableSet(Math.random() > 0.5);
        
        s.add("Yowsers");
    }
    
    public List<String> getImmutableList() {
        return Arrays.asList("foo");
    }
    
    public Set<String> getPossiblyImmutableSet(boolean b) {
        if (b)
            return Collections.unmodifiableSet(new HashSet<String>());
        
        return new HashSet<String>();
    }
    
    
}
