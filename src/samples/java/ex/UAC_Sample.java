package ex;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;

public class UAC_Sample {

    public Instant getInstant() {
        return new Date().toInstant();
    }

    public Path getPath() {

        return new File("hello.world").toPath();
    }
}
