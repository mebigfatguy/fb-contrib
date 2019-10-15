package ex;

import java.lang.reflect.Method;

public class ROOM_Sample {
    public static final Class<?>[] STATIC_NO_ARGS = new Class[0];
    public final Class<?>[] NO_ARGS = new Class[0];

    public void testRoomWithLocals() throws Exception {
        Class<?> c = Class.forName("java.lang.Object");
        Method m = c.getMethod("equals", Object.class);

        String s = (String) m.invoke(this, new ROOM_Sample());
    }

    public void testRoomWithField() throws Exception {
        Class<?> c = Class.forName("java.lang.Object");
        Method m = c.getMethod("toString", NO_ARGS);

        String s = (String) m.invoke(this, (Object[]) null);
    }

    public void testRoomWithStatic() throws Exception {
        Class<?> c = Class.forName("java.lang.Object");
        Method m = c.getMethod("hashCode", STATIC_NO_ARGS);

        String s = (String) m.invoke(this, (Object[]) null);
    }

    public void testRoomWithNull() throws Exception {
        Class<?> c = Class.forName("java.lang.Object");
        Method m = c.getMethod("notify", (Class[]) null);

        String s = (String) m.invoke(this, (Object[]) null);
    }
}
