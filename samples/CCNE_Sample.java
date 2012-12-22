public class CCNE_Sample {
    public void compareClassEquals() {
        Object o = new CCNE_Sample();
        Object p = new CCNE_Sample();
        System.out.println(o.getClass().getName()
                .equals(p.getClass().getName()));
    }

    public void fpCompareAgainstString(String name) {
        Object o = new CCNE_Sample();

        if (o.getClass().getName().equals(name))
            System.out.println("booya");
    }
}
