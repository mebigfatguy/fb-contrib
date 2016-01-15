import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.Assert;
import org.junit.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;

public class IMC_Sample {

    private String reportMe;

    @SuperSecret
    class FPClassIMC {
        private String dontReportMe;
    }

    class FPFieldIMC {
        @SuperSecret
        private String dontReportMe;
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
