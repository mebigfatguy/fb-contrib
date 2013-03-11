
public class UMTP_Sample {

    public <T, C> C getT(String s, int i, Class<C> cls) {
        T t = (T) getFoo();
        System.out.println(t);

        return (C) new Object();
    }

    public <T> String fpUseClass(T t) {
        return t.toString();
    }

    public <T> String fpUseClass(Class<T> c) {
        return c.getName().toString();
    }

    public <T> String fpUseArray(T[] t) {
        return t[0].toString();
    }

    public Object getFoo() {
        return null;
    }
}
