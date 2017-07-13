package ex;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;

public class RFI_Sample {

    public void accessSingleField() throws Exception {

        String s = "Hello";

        Field f = String.class.getDeclaredField("value");
        f.setAccessible(true);

        byte[] data = (byte[]) f.get(s);
        data[1] = 'a';

        System.out.println(s);
    }

    public void accessMultipleFields() throws Exception {
        String s = "Hello";

        Field f = String.class.getDeclaredField("value");
        Field g = String.class.getDeclaredField("hash");
        AccessibleObject.setAccessible(new Field[] { f, g }, true);

        byte[] data = (byte[]) f.get(s);
        data[1] = 'a';

        g.set(s, 0);

        System.out.println(s);
    }
}
