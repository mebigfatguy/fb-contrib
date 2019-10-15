package ex;

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

    public View fpCreateScrollView(View container, View content) {

        if (!content.shouldAdd()) {
            return container;
        }

        View scroller = View.createScroller(container.getChild(), content);
        container.addView(scroller);
        return scroller;
    }

    static class View {

        public static View createScroller(Object child, View content) {
            return null;
        }

        public void addView(View scroller) {
        }

        public boolean shouldAdd() {
            return false;
        }

        public Object getChild() {
            return null;
        }
    }
}
