
public class UMTP_Sample {

    public <T, C> C getT(String s, int i, Class<C> cls) {
        T t = (T) getFoo();
        System.out.println(t);

        return (C) new Object();
    }

    public Object getFoo() {
        return null;
    }
}
