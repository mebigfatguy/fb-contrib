import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("unused")
public class PMB_Sample {
    private static Set<String> bl_data = new HashSet<String>();		//report
    private static List<String> data = new ArrayList<String>();		//noreport
    private static Set<String> inner_data = new HashSet<String>();	//noreport
    private static StringBuilder return_data = new StringBuilder();	//noreport
    
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
    }

    public void remove(String s) {
        data.remove(s);
    }
    
    
    public void makeThings(String s) {
    	return_data.append(s);
    	return_data.append(s);
    	return_data.append(s);
    }
    
    //Does not stop the bug reporting
    public String getBufferString() {
    	return internalGetBuffer().toString();
    }
    
    private StringBuilder internalGetBuffer() {
    	//shouldn't prevent bugReporting, but does
    	return return_data;
    }
    
    //prevents bug reporting
    public StringBuilder getBuffer() {
    	return return_data;
    }
    
    
    public void getInfo(){ //to quash  WOC_WRITE_ONLY_COLLECTION_FIELD 
    	System.out.println(data.size());
    	System.out.println(bl_data.size());
    	System.out.println(bloatableSigs.size());
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
}
