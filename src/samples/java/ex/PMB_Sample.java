package ex;

import java.io.ByteArrayInputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

public class PMB_Sample {
    private static Set<String> bl_data = new HashSet<>(); // tag
    private static List<String> data = new ArrayList<>(); // no tag
    private static Set<String> inner_data = new HashSet<>(); // no tag
    private static StringBuilder return_data = new StringBuilder(); // no tag
    private static Map<String, String> fp_data = new WeakHashMap<>();

    private static Map<String, String> fpEmptyWithIterator = new HashMap<>();

    private static final Set<String> bloatableSigs = new HashSet<>();

    static {
        bloatableSigs.add("Ljava/util/concurrent/ArrayBlockingQueue;");
        bloatableSigs.add("Ljava/util/ArrayList;");
        bloatableSigs.add("Ljava/util/concurrent/BlockingQueue;");
        bloatableSigs.add("Ljava/util/Collection;");
        bloatableSigs.add("Ljava/util/concurrent/ConcurrentHashMap;");
        bloatableSigs.add("Ljava/util/concurrent/ConcurrentSkipListMap;");
        bloatableSigs.add("Ljava/util/concurrent/ConcurrentSkipListSet;");
        bloatableSigs.add("Ljava/util/concurrent/CopyOnWriteArraySet;");
        bloatableSigs.add("Ljava/util/EnumSet;");
        bloatableSigs.add("Ljava/util/EnumMap;");
        bloatableSigs.add("Ljava/util/HashMap;");
        bloatableSigs.add("Ljava/util/HashSet;");
        bloatableSigs.add("Ljava/util/Hashtable;");
        bloatableSigs.add("Ljava/util/IdentityHashMap;");
        bloatableSigs.add("Ljava/util/concurrent/LinkedBlockingQueue;");
        bloatableSigs.add("Ljava/util/LinkedHashMap;");
        bloatableSigs.add("Ljava/util/LinkedHashSet;");
        bloatableSigs.add("Ljava/util/LinkedList;");
        bloatableSigs.add("Ljava/util/List;");
        bloatableSigs.add("Ljava/util/concurrent/PriorityBlockingQueue;");
        bloatableSigs.add("Ljava/util/PriorityQueue;");
        bloatableSigs.add("Ljava/util/Map;");
        bloatableSigs.add("Ljava/util/Queue;");
        bloatableSigs.add("Ljava/util/Set;");
        bloatableSigs.add("Ljava/util/SortedSet;");
        bloatableSigs.add("Ljava/util/SortedMap;");
        bloatableSigs.add("Ljava/util/Stack;");
        bloatableSigs.add("Ljava/lang/StringBuffer;");
        bloatableSigs.add("Ljava/lang/StringBuilder;");
        bloatableSigs.add("Ljava/util/TreeMap;");
        bloatableSigs.add("Ljava/util/TreeSet;");
        bloatableSigs.add("Ljava/util/Vector;");
    }

    // tag
    private ThreadLocal<DateFormat> local = new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
            return new SimpleDateFormat();
        }
    };

    private static ThreadLocal<DateFormat> staticLocal = new ThreadLocal<DateFormat>() {
        @Override
        protected DateFormat initialValue() {
            return new SimpleDateFormat();
        }
    };

    public void add(String s) {
        bl_data.add(s);
        data.add(s);
        System.out.println(staticLocal);
        System.out.println(local);
    }

    public void remove(String s) {
        data.remove(s);
    }

    public X instanceJAXBFactory(String xml) throws JAXBException {
        JAXBContext jc = JAXBContext.newInstance(X.class);
        Unmarshaller unmarshaller = jc.createUnmarshaller();
        return (X) unmarshaller.unmarshal(new ByteArrayInputStream(xml.getBytes()));
    }

    public void fpInnerDoesRemove() {
        inner_data.add("Hello");
        Runnable r = new Runnable() {
            @Override
            public void run() {
                inner_data.remove("Hello");
            }
        };
        r.run();
    }

    public void fpAddToWeakHashMap() {
        fp_data.put("Hello", "There");
    }

    public static void fpCleanUpWithIterator276(String key) {

        Random r = new Random();

        Iterator<Map.Entry<String, String>> it = fpEmptyWithIterator.entrySet().iterator();
        while (it.hasNext()) {
            it.next();
            it.remove();
            if (r.nextBoolean()) {
                break;
            }

            fpEmptyWithIterator.put(key, "foo");
        }
    }

    class X {
    }

}
