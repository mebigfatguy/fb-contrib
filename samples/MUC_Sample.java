import java.util.Arrays;
import java.util.List;


public class MUC_Sample {

    public void testMUC() {
        List<String> l = getImmutableList();
        
        l.add("Uhoh");
    }
    
    public List<String> getImmutableList() {
        return Arrays.asList("foo");
    }
}
