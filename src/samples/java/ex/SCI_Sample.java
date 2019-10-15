package ex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public class SCI_Sample {
    public Set<String> s = Collections.synchronizedSet(new HashSet<String>());

    public void testSyncMember() {
        Iterator<String> it = s.iterator();
        while (it.hasNext()) {
            System.out.println(it.next());
        }
    }

    public void testSyncKeySetLocal() {
        Map<String, String> m = Collections.synchronizedMap(new HashMap<String, String>());
        Iterator<String> it = m.keySet().iterator();
        while (it.hasNext()) {
            System.out.println(it.next());
        }
    }

    public void testSyncEntrySetLocal() {
        Map<String, String> m = Collections.synchronizedMap(new HashMap<String, String>());
        Iterator<Map.Entry<String, String>> it = m.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> entry = it.next();
            System.out.println(entry.getKey() + "=" + entry.getValue());
        }
    }

    public void testSyncValueSetLocal() {
        Map<String, String> m = Collections.synchronizedMap(new HashMap<String, String>());
        Iterator<String> it = m.values().iterator();
        while (it.hasNext()) {
            System.out.println(it.next());
        }
    }

    public void testSyncListLocal() {
        List<String> l = Collections.synchronizedList(new ArrayList<String>());
        Iterator<String> it = l.iterator();
        while (it.hasNext()) {
            System.out.println(it.next());
        }
    }

    public void testSyncSortedSetLocal() {
        SortedSet<String> ss = Collections.synchronizedSortedSet(new TreeSet<String>());
        Iterator<String> it = ss.iterator();
        while (it.hasNext()) {
            System.out.println(it.next());
        }
    }

    public void testSyncSortedMapLocal() {
        SortedMap<String, String> sm = Collections.synchronizedSortedMap(new TreeMap<String, String>());
        Iterator<Map.Entry<String, String>> it = sm.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> entry = it.next();
            System.out.println(entry.getKey() + "=" + entry.getValue());
        }
    }

    public void testSyncCollectionInSync() {
        SortedMap<String, String> sm = Collections.synchronizedSortedMap(new TreeMap<String, String>());
        synchronized (sm) {
            Iterator<Map.Entry<String, String>> it = sm.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, String> entry = it.next();
                System.out.println(entry.getKey() + "=" + entry.getValue());
            }
        }
    }

    public void testSyncCollectionInOtherSync() {
        SortedMap<String, String> sm = Collections.synchronizedSortedMap(new TreeMap<String, String>());
        synchronized (this) {
            Iterator<Map.Entry<String, String>> it = sm.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, String> entry = it.next();
                System.out.println(entry.getKey() + "=" + entry.getValue());
            }
        }
    }
}