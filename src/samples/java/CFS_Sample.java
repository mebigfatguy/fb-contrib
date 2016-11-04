import java.util.Date;

public class CFS_Sample {
    @SuppressWarnings("deprecation")
    public Date getNextDate(Date d) {
        d.setHours(0);
        return d;
    }

    @SuppressWarnings("deprecation")
    public Date getNextDateFP(Date d) {
        d = (Date) d.clone();
        d.setHours(0);
        return d;
    }
}
