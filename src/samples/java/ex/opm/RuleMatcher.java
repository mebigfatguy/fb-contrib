package ex.opm;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

public class RuleMatcher extends BaseMatcher<String> {

    public RuleMatcher() {
    }

    public RuleMatcher(String source, String target, int priority) {
    }

    @Override
    public boolean matches(Object item) {
        return false;
    }

    @Override
    public void describeTo(Description d) {
    }

}
