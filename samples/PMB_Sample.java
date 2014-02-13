import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PMB_Sample {
    private static Set<String> bl_data = new HashSet<String>();
    private static List<String> data = new ArrayList<String>();
    private static Set<String> inner_data = new HashSet<String>();

    private final ThreadLocal<DateFormat> local = new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
            return new SimpleDateFormat();
        }
    };
    
    public void add(String s) {
        bl_data.add(s);
        data.add(s);
    }

    public void remove(String s) {
        data.remove(s);
    }

    public void fpInnerDoesRemove() {
        inner_data.add("Hello");
        Runnable r = new Runnable() {
            public void run() {
                inner_data.remove("Hello");
            }
        };
        r.run();
    }
}
