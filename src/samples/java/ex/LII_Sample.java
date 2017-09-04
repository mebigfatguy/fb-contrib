package ex;

import java.util.ArrayList;
import java.util.List;

public class LII_Sample {
    public String test1(List<String> l) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < l.size(); i++) {
            sb.append(l.get(i));
        }
        return sb.toString();
    }

    public String test2(List<String> l) {
        StringBuffer sb = new StringBuffer();
        int len = l.size();
        for (int i = 0; i < len; i++) {
            sb.append(l.get(i));
        }
        return sb.toString();
    }

    public String test3FP(List<String> l) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < l.size(); i++) {
            sb.append(i + " " + l.get(i));
        }
        return sb.toString();
    }

    public String test4FP(List<String> l) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < l.size(); i += 2) {
            sb.append(l.get(i));
        }
        return sb.toString();
    }

    public String test5FP(List<String> l) {
        StringBuffer sb = new StringBuffer();
        for (int i = 1; i < l.size(); i++) {
            sb.append(l.get(i));
        }
        return sb.toString();
    }

    public String test6FP(List<String> l) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < 3; i++) {
            sb.append(l.get(i));
        }

        return sb.toString();
    }

    public void test7FP(List<Integer> editedIndexes, List<Integer> pageTokens, List<Integer> tokens) {
        int index = pageTokens.size();
        for (int i = 0; i < tokens.size(); i++) {
            editedIndexes.add(new Integer(index));
            index++;
        }
    }

    class GH228 {

        private final List<String> a = new ArrayList<>();

        int compare(final A b) {
            for (int k = 0; k < a.size(); ++k) {
                final int c = a.get(k).compareTo(b.a.get(k));
                if (c != 0) {
                    return c;
                }
            }
            return 0;
        }
    }
}