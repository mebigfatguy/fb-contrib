package ex;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("all")
public class UEC_Sample {

    Map<FalsePositive, Integer> fpConcur = new ConcurrentHashMap<>();

    public enum Suite {
        Spades, Hearts, Clubs, Diamonds
    };

    public enum FalsePositive {
        A, B, C
    };

    private final Set<Suite> wildSuites = new HashSet<>();
    private final EnumSet<Suite> eWildSuites = EnumSet.noneOf(Suite.class);

    private interface EnumStub {
    }

    public enum EnumWithInf implements EnumStub {
        A, B, C
    };

    public UEC_Sample() {
        wildSuites.add(Suite.Spades);
    }

    public UEC_Sample(Suite s) {
        wildSuites.add(s);
    }

    public Map<Suite, Integer> deal() {
        Map<Suite, Integer> hand = new HashMap<>();
        hand.put(Suite.Spades, new Integer(10));
        hand.put(Suite.Hearts, new Integer(9));

        return hand;
    }

    public EnumMap<Suite, Integer> eDeal() {
        EnumMap<Suite, Integer> hand = new EnumMap(Suite.class);
        hand.put(Suite.Spades, new Integer(10));
        hand.put(Suite.Hearts, new Integer(9));

        return hand;
    }

    public void uecFP() {
        Set<FalsePositive> testSet = EnumSet.of(FalsePositive.A);

        testSet.add(FalsePositive.B);
    }

    public Set<Suite> getSuites() {
        return EnumSet.<Suite> allOf(Suite.class);
    }

    public void uecFP2() {
        Set<Suite> suites = getSuites();

        suites.add(Suite.Clubs);
    }

    public void fpEnumsWithInf() {
        Set<EnumStub> es = new HashSet<>();
        es.add(EnumWithInf.A);
        es.add(EnumWithInf.B);
        es.add(EnumWithInf.C);
    }

    public void fpDontRecommendOnConcurrent(FalsePositive fp) {
        fpConcur.put(fp, Integer.valueOf(1));
    }
}
