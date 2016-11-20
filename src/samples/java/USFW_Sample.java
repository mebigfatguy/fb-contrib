import org.springframework.stereotype.Component;

@Component
public class USFW_Sample {

    private String s;

    public void writeToField() {
        s = "Hello";
    }
}
