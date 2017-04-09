package ex;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class PMB_Sample {
    private static Set<String> bl_data = new HashSet<String>(); // tag
    private static List<String> data = new ArrayList<String>(); // no tag
    private static Set<String> inner_data = new HashSet<String>(); // no tag
    private static StringBuilder return_data = new StringBuilder(); // no tag
    private static Map<String, String> fp_data = new WeakHashMap<String, String>();

    private static final Set<String> bloatableSigs = new HashSet<String>();

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
}
