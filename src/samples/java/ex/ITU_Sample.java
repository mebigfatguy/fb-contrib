package ex;
import java.util.List;

public class ITU_Sample {

    List<String> l;

    public String processList() {
        return l.toString().substring(0, 10);
    }

    public String processList2() {
        String s = l.toString();
        if (s != null) {
            return s.substring(0, 10);
        }
        return null;
    }

    public String ignore() {
        return toString().substring(0, 1);
    }

    @Override
    public String toString() {
        return "1 1";
    }
}
