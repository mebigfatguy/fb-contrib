package ex;

import com.google.common.cache.CacheLoader;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

public class MOM_Sample {
    public void test(int i) {
    }

    public static void test(int i, int j) {
    }

    public static void loading() {
        CacheLoader<Integer, Integer> cl = new CacheLoader<Integer, Integer>() {

            @Override
            public Integer load(Integer key) {
                return key;
            }

            @Override
            public ImmutableMap<Integer, Integer> loadAll(Iterable<? extends Integer> keys) {
                return Maps.toMap(ImmutableSet.copyOf(keys), this::load);
            }
        };
    }
}
