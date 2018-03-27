package ex;

import java.util.List;
import java.util.stream.Collectors;

public class FII_Sample {

    public List<Bauble> getFreeBees(List<Bauble> baubles) {

        return baubles.stream().filter(b -> b.isFree()).collect(Collectors.toList());
    }

    public List<String> getNames(List<Bauble> baubles) {

        return baubles.stream().map(b -> b.getName()).collect(Collectors.toList());
    }

    public List<Bauble> getfpFreeBees(List<Bauble> baubles) {

        return baubles.stream().filter(Bauble::isFree).collect(Collectors.toList());
    }

    public List<String> fpGetNames(List<Bauble> baubles) {

        return baubles.stream().map(Bauble::getName).collect(Collectors.toList());
    }

    public static class Bauble {

        public String getName() {
            return "golden orb";
        }

        public boolean isFree() {
            return true;
        }
    }
}
