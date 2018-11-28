package ex;

import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

public class SAT_Sample {

    public void testHasEntry() {
        Matcher m =  Matchers.hasEntry("key", Matchers.containsString("val"));
    }
}
