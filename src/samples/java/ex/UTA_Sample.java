package ex;

import java.util.Iterator;
import java.util.List;

public class UTA_Sample {
    public String[] testList1(List<String> l) {
        String[] data = new String[l.size()];
        for (int i = 0; i < l.size(); i++)
            data[i] = l.get(i);

        return data;
    }

    public Integer[] testList2(List<Integer> l) {
        int size = l.size();
        Integer[] data = new Integer[size];
        for (int i = 0; i < size; i++)
            data[i] = l.get(i);

        return data;
    }

    public Long[] testList3(List<Long> l) {
        Iterator<Long> it = l.iterator();
        Long[] data = new Long[l.size()];
        for (int i = 0; i < l.size(); i++)
            data[i] = it.next();

        return data;
    }
}
