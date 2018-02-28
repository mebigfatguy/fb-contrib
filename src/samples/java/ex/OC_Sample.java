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

    public void fpFieldCast262() {
        Reference<String> test = new Reference<>(null);
        test.value = (String) getObject();
    }

    public final class Reference<T> {
        public T value;

        public Reference(T value) {
            this.value = value;
        }
    }

    public Object getObject() {
        return "string";
    }
}