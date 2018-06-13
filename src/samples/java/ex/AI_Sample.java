package ex;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import org.apache.commons.collections.CollectionUtils;

public class AI_Sample {

    @Nullable
    public String getFoo() {
        return null;
    }

    public String getFoo2() {
        return getFoo();
    }

    public String getFoo3() {
        return "";
    }

    public String nullBecauseOfConditional() throws IOException {
        String f = getFoo3();
        if (f == null) {
            return f;
        }

        return "";
    }

    public String fpNullChecked() throws IOException {
        String f = getFoo();
        if (f == null) {
            throw new IOException();
        }

        return f;
    }

    public Void fpVoid() {
        return null;
    }

    public String fpAnon() {

        return new Stringer() {
            @Override
            public String get() {
                return null;
            }
        }.get();
    }

    public void fpLambda(Constructor c) {
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            c.setAccessible(true);
            return null;
        });
    }

    public AI_Sample fpNotableExceptions284() throws Exception {
        return AI_Sample.class.newInstance();
    }

    public Object fpConditionalWithThrow291() {
        Object t = create();
        if (t != null) {
            return t;
        } else {
            throw new RuntimeException();
        }
    }

    public Object fpConditionalWithElseNotNull() {
        Object t = create();
        if (t == null) {
            return new Object();
        } else {
            return t;
        }
    }

    public Object fpIsEmpty(String s) {
        List<String> ss = maybeGetList(s);

        if (CollectionUtils.isEmpty(ss)) {
            return Collections.emptyList();
        }

        return ss;
    }

    public String fpGetProperty296(final String key, final String defaultValue) {
        String strResult = getProperty(key);
        if (strResult != null) {
            return strResult;
        } else {
            putDefault(key, defaultValue);
            return defaultValue;
        }
    }

    public String getProperty(final String key) {
        return Math.random() > 0.5 ? null : "";
    }

    private void putDefault(String key, String value) {
    }

    interface Stringer {
        String get();
    }

    @Nullable
    private static Object create() {
        return Math.random() >= 0.5 ? new Object() : null;
    }

    private List<String> maybeGetList(String s) {
        if (s == null) {
            return null;
        }
        return Collections.singletonList(s);
    }
}
