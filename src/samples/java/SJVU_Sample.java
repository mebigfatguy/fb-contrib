import javax.persistence.Cache;

public class SJVU_Sample {
    public void test14using15(int i) {
        Integer ii = Integer.valueOf(i);
        StringBuilder sb = new StringBuilder();
        sb.append(ii.intValue());
    }

    public void fpExternalJavax() {
        Cache c = new Cache() {

            @Override
            public boolean contains(Class arg0, Object arg1) {
                return false;
            }

            @Override
            public void evict(Class arg0, Object arg1) {
            }

            @Override
            public void evict(Class arg0) {
            }

            @Override
            public void evictAll() {
            }

            @Override
            public <T> T unwrap(Class<T> arg0) {
                return null;
            }
        };
    }

}
