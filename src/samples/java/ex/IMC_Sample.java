package ex;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.persistence.Entity;

import org.junit.Assert;
import org.junit.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;

public class IMC_Sample implements Serializable {

    private static final long serialVersionUID = 5213802770984511942L;

    private String reportMe;

    private static final int OUT_OF_PLACE_STATIC = 0;

    @SuperSecret
    class FPClassIMC {
        private String dontReportMe;
    }

    class FPFieldIMC {
        @SuperSecret
        private String dontReportMe;
    }

    public void psf(File f) {
        try (InputStream is = new FileInputStream(f)) {
            is.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class IMCFPHasAToString {
    @SuperSecret
    private String fooo;

    @Override
    public String toString() {
        return fooo;
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.FIELD })
@interface SuperSecret {
}

class FPIMCTestClass {

    private int data1, data2;

    @Test
    public void doTest() {
        Assert.assertEquals(data1, data2);
    }
}

@Entity
class FPIMCEntity {
    private int id;
    private String name;
}

class MyVisitor extends AnnotationVisitor {

    String name;

    public MyVisitor() {
        super(Opcodes.ASM4);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String arg0, String arg1) {
        name = arg0;

        return super.visitAnnotation(arg0, arg1);
    }
}
