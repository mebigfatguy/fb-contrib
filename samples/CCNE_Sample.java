public class CCNE_Sample {
    public void compareClassEquals() {
        Object o = new CCNE_Sample();
        Object p = new CCNE_Sample();
        System.out.println(o.getClass().getName()
                .equals(p.getClass().getName()));
    }
}
