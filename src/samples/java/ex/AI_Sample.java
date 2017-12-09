package ex;

import java.io.IOException;

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

    public String fpAnon() {

        return new Stringer() {
            @Override
            public String get() {
                return null;
            }
        }.get();
    }

    interface Stringer {
        String get();
    }
}
