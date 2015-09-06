import java.util.Comparator;

public class SCRV_Sample {
    public static final int T1 = 0;
    public static final int T2 = 1;

    int t = 0;
    int u = 0;

    class SampleComparator implements Comparator<SCRV_Sample> {
        @Override
        public int compare(SCRV_Sample arg0, SCRV_Sample arg1) {
            if (arg0.t == arg1.t)
                return 0;

            return -1;
        }
    }

    class SampleComparable implements Comparable<SCRV_Sample> {
        @Override
        public int compareTo(SCRV_Sample arg0) {
            if (t == arg0.t)
                return 0;

            return 1;
        }
    }
    
    class UnconditionalNonZeroReturnComparable implements Comparable<SCRV_Sample> {
        @Override
        public int compareTo(SCRV_Sample arg0) {
            if (t == arg0.t)
                return 0;
            else if (u < arg0.u)
                return -1;

            return 1;
        }
    }

    class FPComparator implements Comparable<FPComparator> {
        int i = 0;

        @Override
        public int compareTo(FPComparator that) {
            return i < that.i ? -1 : (i == that.i) ? 0 : 1;
        }
    }

    class FPThrowsComparator implements Comparator<SCRV_Sample> {
        @Override
        public int compare(SCRV_Sample arg0, SCRV_Sample arg1) {
            throw new UnsupportedOperationException();
        }
    }

    class FPDefaultComparator implements Comparator<String> {
        @Override
        public int compare(String s1, String s2) {
            return 0;
        }
    }
}
