package ex;
import java.util.ArrayList;
import java.util.List;

public class SCA_Sample implements Cloneable {
    private List<String> names = new ArrayList<String>();

    @Override
    public Object clone() throws CloneNotSupportedException {
        SCA_Sample s = (SCA_Sample) super.clone();
        names = new ArrayList<String>();
        s.names.addAll(names);
        names.add("New");
        return s;
    }
}
