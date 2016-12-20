import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;

import javax.swing.JFrame;

public class UCC_Sample {
    private Object[] arrayData;
    private List<Object> listData = new ArrayList<>();
    private Bean1 b1;
    private Bean2 b2;

    public void test0() {
        TreeSet<Date> tm = new TreeSet<>();
        tm.add(new Date());
        tm.add(new Date());
    }

    public void test1() {
        arrayData = new Object[3];
        arrayData[0] = new Integer(1);
        arrayData[1] = new StringTokenizer("this");
        arrayData[2] = new JFrame();
        listData.add(new GregorianCalendar());

    }

    public void test2() {
        listData.add(new UCC_Sample() {
        });
    }

    public void test3() {
        Set<Object> s = new HashSet<>();
        s.add(new int[] { 3, 2 });
        s.add(new Color(0, 128, 255));
    }

    public void bug1678805() {
        final File[] files = new File[5];
        for (int i = 0; i < 5; i++) {
            files[i] = getPath();
        }
    }

    public void fpTwoDifferentFieldSources() {
        b1.data.add("Hello");
        b2.data.add(5);
    }

    private File getPath() {
        return new File("c:\\temp");
    }

    static class Bean1 {
        List<String> data;
    }

    static class Bean2 {
        List<Integer> data;
    }
}