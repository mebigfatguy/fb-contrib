package ex;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import ex.opm.RuleMatcher;

public class OPM_Sample extends OPMSuper implements Comparator<String> {

    public void testNormalMethodCouldBePrivate() {
        someNormalMethod();
    }

    @Override
    public int compare(String s1, String s2) {
        return s2.compareTo(s1);
    }

    public int testFPGenericDerivation() {
        return compare("Hello", "World");
    }

    public void someNormalMethod() {
        List<String> l = getFoo(new ArrayList<String>());
    }

    public Boolean isFoo390() {
        return true;
    }

    @Override
    public List<String> getFoo(List<String> l) {
        return l;
    }

    public void fpUncalledDontReport() {
        fpHasRTAnnotation();
    }

    @RT
    public void fpHasRTAnnotation() {
    }

    public void setFPFoo(int x) {
    }

    public int getFPFoo() {
        return 0;
    }

    public void doIt() {
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(Writer::fpFlush, 1L, 1L, TimeUnit.SECONDS);
    }
}

abstract class OPMSuper {
    public abstract List<String> getFoo(List<String> l);
}

@Retention(RetentionPolicy.RUNTIME)
@interface RT {

}

enum FPEnumValueOf {

    What, Where;

    public static void fpWithValueOf() {
        FPEnumValueOf f = FPEnumValueOf.valueOf(String.valueOf("What"));
    }
}

class Writer {
    public static void fpFlush() {
    }

    public void close() {
        fpFlush();
    }
}

class GitHubIssue206 {

    GitHubIssue206 service;

    @Test
    public void testCustomMatcher() {

        when(service.createRuleBatch(eq(""), argThat(new RuleMatcher("", "", 1)))).thenReturn(Boolean.TRUE);
    }

    public Boolean createRuleBatch(String s, String t) {
        return null;
    }
}

class GitHubIssue437 implements GHI437Inf {

    @Override
    public Integer[] getObj(int type) {
        return new Integer[0];
    }

    public void tryIt() {
        GitHubIssue437 ghi = new GitHubIssue437();
        ghi.getObj(1);
    }
}

interface GHI437Inf {
    Object getObj(int type);
}
