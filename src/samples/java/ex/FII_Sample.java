package ex;

import java.math.BigDecimal;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class FII_Sample {

    public List<Bauble> getFreeBees(List<Bauble> baubles) {

        return baubles.stream().filter(b -> b.isFree()).collect(Collectors.toList());
    }

    public List<String> getNames(List<Bauble> baubles) {

        return baubles.stream().map(b -> b.getName()).collect(Collectors.toList());
    }

    public void addBitSet(BitSet bs, List<Integer> ints) {
        ints.forEach(i -> bs.set(i));
    }

    public List<Bauble> getfpFreeBees(List<Bauble> baubles) {

        return baubles.stream().filter(Bauble::isFree).collect(Collectors.toList());
    }

    public List<String> fpGetNames(List<Bauble> baubles) {

        return baubles.stream().map(Bauble::getName).collect(Collectors.toList());
    }

    public Map<String, Long> fpBuildMapper(List<Long> l) {

        return l.stream().collect(Collectors.toMap(Object::toString, e -> e));
    }

    public boolean containsOnACollect(List<Bauble> baubles, String name) {
        return baubles.stream().map(Bauble::getName).collect(Collectors.toSet()).contains(name);
    }

    public boolean poorMansAnyMatch(List<Bauble> baubles, String name) {
        return baubles.stream().map(Bauble::getName).filter(n -> n.equals(name)).findFirst().isPresent();
    }

    public Bauble get0OnCollect(List<Bauble> baubles) {
        return baubles.stream().collect(Collectors.toList()).get(0);
    }

    public List<Bauble> backToBackFilter(Set<Bauble> baubles) {
        return baubles.stream().filter(b -> b.getName().equals("diamonds")).filter(b -> b.isFree())
                .collect(Collectors.toList());
    }

    public Map<String, Bauble> mapIdentity(List<Bauble> baubles) {
        return baubles.stream().collect(Collectors.toMap(Bauble::getName, b -> b));
    }

    public int sizeOnACollect(List<Bauble> baubles, String name) {
        return baubles.stream().filter(b -> b.getName().equals(name)).collect(Collectors.toSet()).size();
    }

    public void fpUnrelatedLambdaValue282(Map<String, Bauble> map, BaubleFactory factory) {
        map.computeIfAbsent("pixie dust", _unused -> factory.getBauble());
    }

    public BigDecimal fpCastEliminatesMethodReference282(List<Bauble> baubles) {
        return baubles.stream().filter(b -> b.getName().equals("special")).map(b -> (BigDecimal) b.getCost())
                .findFirst().get();
    }

    public static <T> Stream<T> fpIiteratorToFiniteStream283(Iterator<T> iterator, boolean parallel) {
        Iterable<T> iterable = () -> iterator;
        return StreamSupport.stream(iterable.spliterator(), parallel);
    }

    public void fpUseIdentity283() {
        put(m -> {
            m.putAll(m);
            return m;
        });
    }

    public static void foo(Consumer<Void> consumer) {
    }

    public static void bar342(Runnable runnable) {
        foo(_unused -> runnable.run());
    }

    public void put(Function<Map<String, Object>, Map<String, Object>> updateFunction) {
    }

    public static class Bauble {

        public String getName() {
            return "golden orb";
        }

        public boolean isFree() {
            return true;
        }

        public Number getCost() {
            return 0.0;
        }
    }

    public static class SpecialBauble extends Bauble {
        @Override
        public BigDecimal getCost() {
            return new BigDecimal("0.0");
        }
    }

    public static class BaubleFactory {
        public Bauble getBauble() {
            return new Bauble();
        }
    }

    public enum GiantSpeak {
        FEE, FI, FO, FUM, BLUB;

        static Set<GiantSpeak> sayings = EnumSet.allOf(GiantSpeak.class);
        static {
            sayings.removeIf(s -> !s.whatGiantSay());
        }

        public boolean whatGiantSay() {
            return this != GiantSpeak.BLUB;
        }
    }

    final class FP363 {
        public void example(Foo363 foo, String string) {
            perform(() -> foo.foo(string)); // (*)
        }

        private void perform(Runnable action) {
            action.run();
        }
    }

    abstract class Foo363 {
        public abstract void foo(String string);
    }
}
