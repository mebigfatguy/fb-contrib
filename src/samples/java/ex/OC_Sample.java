package ex;
import java.util.ArrayList;
import java.util.Collection;

@SuppressWarnings("all")
public class OC_Sample {
    private java.util.Date ud;

    public void castListInReg(Object o) {
        Collection<String> c = (ArrayList<String>) o;
    }

    public void castDateInField(Object o) {
        ud = (java.sql.Date) o;
    }
}