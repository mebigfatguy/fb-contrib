package ex;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpSession;

@SuppressWarnings("all")
public class SCSS_Sample {
    public void setChange(HttpSession session) {
        Set<String> ss = (Set<String>) session.getAttribute("mykeys");
        ss.add("Foo");
    }

    public void arrayChange(HttpSession session) {
        double[] d = (double[]) session.getAttribute("mynums");
        d[3] = 0.0;
    }

    public void ok(HttpSession session) {
        Map<String, String> mm = (Map<String, String>) session.getAttribute("mymapping");
        mm.put("foo", "bar");
        session.setAttribute("mymapping", mm);
    }
}
