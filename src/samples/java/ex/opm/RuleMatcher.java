package ex.opm;

import org.hamcrest.Description;
import org.hamcrest.Matcher;

public class RuleMatcher implements Matcher<String> {

    public RuleMatcher() {
    }

    public RuleMatcher(String source, String target, int priority) {
    }

    @Override
    public void describeTo(Description d) {
    }

    @Override
    public void describeMismatch(Object o, Description d) {
    }

    @Override
    public void _dont_implement_Matcher___instead_extend_BaseMatcher_() {
    }

    @Override
    public boolean matches(Object arg0) {
        return false;
    }

}
