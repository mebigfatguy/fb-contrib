package ex;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.regex.Matcher;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class PSC_Sample {

    public void testPSC(List<PSC_Sample> samples) {
        Set<String> names = new HashSet<>();
        for (PSC_Sample s : samples) {
            names.add(s.toString());
        }
    }

    public void testPSCMaps(Map<String, String> input) {
        Map<String, String> output = new HashMap<>();
        for (Map.Entry<String, String> entry : input.entrySet()) {
            output.put(entry.getKey().intern(), entry.getValue());
        }
    }

    public void testPSCEnumerated() {
        Set<String> commonWords = new HashSet<>();
        commonWords.add("a");
        commonWords.add("an");
        commonWords.add("the");
        commonWords.add("by");
        commonWords.add("of");
        commonWords.add("and");
        commonWords.add("or");
        commonWords.add("in");
        commonWords.add("with");
        commonWords.add("my");
        commonWords.add("I");
        commonWords.add("on");
        commonWords.add("over");
        commonWords.add("under");
        commonWords.add("it");
        commonWords.add("they");
        commonWords.add("them");
    }

    public List<String> testAddAllToCtor(List<String> l) {
        List<String> ll = new ArrayList<>();
        ll.addAll(l);

        ll.add("FooBar");
        return ll;
    }

    public List<String> testGuavaLists(List<Integer> ii) {
        List<String> ss = Lists.newArrayList();

        for (Integer i : ii) {
            ss.add(String.valueOf(i));
        }

        return ss;
    }

    public Set<String> testGuavaSets(List<Integer> ii) {
        Set<String> ss = Sets.newHashSet();

        for (Integer i : ii) {
            ss.add(String.valueOf(i));
        }

        return ss;
    }

    public Map<String, Integer> testGuavaMaps(List<Integer> ii) {
        Map<String, Integer> ss = Maps.newHashMap();

        for (Integer i : ii) {
            ss.put(String.valueOf(i), i);
        }

        return ss;
    }

    public String testNaiveSizing(Collection<String> cc) {
        Map<String, String> mm = new HashMap<>(cc.size());
        for (String c : cc) {
            mm.put(c, c);
        }

        Set<String> ss = new HashSet<>(cc.size());
        for (String c : cc) {
            ss.add(c);
        }

        return mm.toString() + " - " + ss.toString();

    }

    public void fpDontHaveCollectionForSizing(Iterator<Long> it) {
        Set<Long> ad = new TreeSet<>();
        while (it.hasNext()) {
            ad.add(it.next());
        }
    }

    public void fpConditionalInLoop(Set<String> source) {
        List<String> dest = new ArrayList<>();
        for (String s : source) {
            if (s.length() > 0) {
                dest.add(s);
            }
        }
    }

    public List<String> fpAddSubCollection(Map<String, Set<String>> s) {
        List<String> l = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : s.entrySet()) {
            l.add(entry.getKey());
            l.addAll(entry.getValue());
        }
        return l;
    }

    public void fpSwitchInLoop(Set<Integer> source) {
        List<Integer> dest = new ArrayList<>();
        for (Integer s : source) {
            switch (s.intValue()) {
                case 0:
                    dest.add(s);
                break;
                case 1:
                    dest.remove(s);
                break;
            }
        }
    }

    public void fpAllocationInLoop(Map<String, String> source) {
        Map<String, List<String>> dest = new HashMap<>();

        for (Map.Entry<String, String> entry : source.entrySet()) {

            List<String> l = new ArrayList<>();
            l.add(entry.getValue());
            dest.put(entry.getKey(), l);
        }
    }

    public List<String> fpUnknownSrcSize(BufferedReader br) throws IOException {
        List<String> l = new ArrayList<>();
        String line;
        while ((line = br.readLine()) != null) {
            l.add(line);
        }

        return l;
    }

    public List<Exception> fpPSCInCatchBlock(List<String> src) {
        List<Exception> exceptions = new ArrayList<>();

        for (String s : src) {
            try {
                s = s.substring(1000, 1001);

            } catch (IndexOutOfBoundsException e) {
                exceptions.add(e);
            }
        }

        List<Exception> exceptions2 = new ArrayList<>();

        for (String s : src) {
            try {
                s = s.substring(1000, 1001);
                if (s == null) {
                    return null;
                }
            } catch (IndexOutOfBoundsException e) {
                exceptions2.add(e);
            }
        }

        return exceptions;
    }

    public void fpNoAllocation(List<String> ss, List<Integer> ii) {
        for (Integer i : ii) {
            ss.add(ii + "");
        }
    }

    public List<String> fpWithEnumeration247(Enumeration<String> e) {
        List<String> result = new ArrayList<>();
        while (e.hasMoreElements()) {
            result.add("A" + e.nextElement());
        }

        return result;
    }

    public List<String> fpTokenizer(StringTokenizer st) {
        List<String> result = new ArrayList<>();
        while (st.hasMoreTokens()) {
            result.add(st.nextToken());
        }

        return result;
    }

    public List<String> fpNoSizedSource(Iterator<String> it) {
        List<String> result = new ArrayList<>();
        while (it.hasNext()) {
            result.add(it.next());
        }

        return result;
    }

    public List<String> fpStreamSource249(BooReader br) throws IOException, ClassNotFoundException {
        List<String> result = new ArrayList<>();
        Object o;
        while (br.tokenType() != BooReader.BooTokenType.END) {
            result.add(br.nextToken());
        }

        return result;
    }

    public void fpInitMultipleOnSameRef(boolean a, boolean b) {
        Set<String> immutable1;
        Set<String> immutable2;

        Set<String> s = new HashSet<>();
        s.add("A1");
        s.add("A2");
        s.add("A3");
        s.add("A4");
        s.add("A5");
        s.add("A6");
        s.add("A7");
        s.add("A8");
        s.add("A9");
        s.add("A10");
        s.add("A11");
        s.add("A12");
        s.add("A13");
        s.add("A14");
        s.add("A15");
        immutable1 = Collections.unmodifiableSet(s);

        s = new HashSet<>();
        s.add("B1");
        s.add("B2");
        s.add("B3");
        s.add("B4");
        s.add("B5");
        s.add("B6");
        s.add("B7");
        s.add("B8");
        immutable2 = Collections.unmodifiableSet(s);
    }

    public List<String> fpMatcher(Matcher m) {
        List<String> ss = new ArrayList<>();
        int start = 0;
        while (m.find(start)) {
            String g = m.group(1);
            ss.add(g);
            start = m.end();
        }

        return ss;
    }

    public List<?> fpDecodeValue249(BsonReader reader, DecoderContext decoderContext) {
        reader.readStartArray();

        List<Object> list = new ArrayList<>();
        while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
            list.add(readValue249(reader, decoderContext));
        }
        reader.readEndArray();

        return list;
    }

    public Object readValue249(BsonReader br, DecoderContext dc) {
        return null;
    }

    enum BsonType {
        END_OF_DOCUMENT
    }

    interface BsonReader {
        void readStartArray();

        BsonType readBsonType();

        Object readEndArray();
    }

    interface DecoderContext {

    }

    interface BooReader {
        enum BooTokenType {
            START, MIDDLE, END
        };

        BooTokenType tokenType();

        String nextToken();
    }
}
