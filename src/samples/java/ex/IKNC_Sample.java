package ex;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@SuppressWarnings("all")
public class IKNC_Sample {
    public void doGet(HttpServletRequest req, HttpServletResponse resp) {
        String id = req.getParameter("id");
    }

    public void doPost(HttpServletRequest req, HttpServletResponse resp) {
        String id = req.getParameter("ID");
    }

    public Object getScore1(HttpSession session) {
        return session.getAttribute("score");
    }

    public void putScore(HttpSession session) {
        session.setAttribute("Score", null);
    }
}
