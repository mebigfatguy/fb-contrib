import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

@Component
public class USFW_Sample {

    private String s;

    public void writeToField() {
        s = "Hello";
    }
}

@Controller
@Scope("singleton")
class USFW2_Sample {

    private String s;

    public void writeToField() {
        s = "Hello";
    }
}

@Repository
@Scope(scopeName = "singleton")
class USFW3_Sample {

    private String s;

    public void writeToField() {
        s = "Hello";
    }
}

@Service
@Scope("prototype")
class USFW1_Sample {

    private String s;

    public void fpWriteToField() {
        s = "Hello";
    }
}
