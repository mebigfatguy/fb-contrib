package ex;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.security.AccessController;
import java.security.PrivilegedAction;

import javax.annotation.Nullable;

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

    interface Stringer {
        String get();
    }
}
