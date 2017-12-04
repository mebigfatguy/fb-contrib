package ex;

import java.io.IOException;
import java.io.InputStream;

import javax.annotation.Nullable;

public class AI_Sample {

    @Nullable
    public String getFoo() {
        return null;
    }

    public String getFoo2() {
        return getFoo();
    }

    public InputStream fpNullChecked() throws IOException {
        InputStream is = AI_Sample.class.getResourceAsStream("/foo");
        if (is == null) {
            throw new IOException();
        }

        return is;
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
