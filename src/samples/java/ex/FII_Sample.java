package ex;

import java.util.List;
import java.util.stream.Collectors;

public class FII_Sample {

    public List<Bauble> getFreeBees(List<Bauble> baubles) {

        return baubles.stream().filter(b -> b.isFree()).collect(Collectors.toList());
    }

    public static class Bauble {

        public boolean isFree() {
            return true;
        }

    }
}
