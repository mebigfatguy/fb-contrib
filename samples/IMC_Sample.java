import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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
@Target({ElementType.TYPE, ElementType.FIELD})
@interface SuperSecret {
}
