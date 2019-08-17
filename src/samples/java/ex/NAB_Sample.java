package ex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@SuppressWarnings("all")
public class NAB_Sample {
    public void testDupCtor() {
        Boolean bo = new Boolean(false);
        Boolean bbo = new Boolean(bo);
        Byte b = new Byte((byte) 0);
        Byte bb = new Byte(b);
        Character c = new Character('a');
        Character cc = new Character(c);
        Short s = new Short((short) 0);
        Short ss = new Short(s);
        Integer i = new Integer(0);
        Integer ii = new Integer(i);
        Long l = new Long(0);
        Long ll = new Long(l);
        Float f = new Float(0.0f);
        Float ff = new Float(f);
        Double d = new Double(0.0);
        Double dd = new Double(d);
    }

    public void testDupValueOf() {
        Boolean bo = new Boolean(false);
        Boolean bbo = Boolean.valueOf(bo);
        Byte b = new Byte((byte) 0);
        Byte bb = Byte.valueOf(b);
        Character c = new Character('a');
        Character cc = Character.valueOf(c);
        Short s = new Short((short) 0);
        Short ss = Short.valueOf(s);
        Integer i = new Integer(0);
        Integer ii = Integer.valueOf(i);
        Long l = new Long(0);
        Long ll = Long.valueOf(l);
        Float f = new Float(0.0f);
        Float ff = Float.valueOf(f);
        Double d = new Double(0.0);
        Double dd = Double.valueOf(d);
    }

    public void testNeedsParse(String data) {
        // The first one is a false positive for < 1.5
        boolean bo = Boolean.valueOf(data).booleanValue();
        byte b = Byte.valueOf(data).byteValue();
        short s = Short.valueOf(data).shortValue();
        int i = Integer.valueOf(data).intValue();
        long l = Long.valueOf(data).longValue();
        float f = Float.valueOf(data).floatValue();
        double d = Double.valueOf(data).doubleValue();
    }

    public void testExtraneousParse() {
        Boolean bo = Boolean.valueOf(Boolean.parseBoolean("true"));
        bo = new Boolean(Boolean.parseBoolean("true"));
        Byte b = Byte.valueOf(Byte.parseByte("1"));
        b = new Byte(Byte.parseByte("1"));
        Short s = Short.valueOf(Short.parseShort("1"));
        s = new Short(Short.parseShort("1"));
        Integer i = Integer.valueOf(Integer.parseInt("1"));
        i = new Integer(Integer.parseInt("1"));
        Long l = Long.valueOf(Long.parseLong("1"));
        l = new Long(Long.parseLong("1"));
        Float f = Float.valueOf(Float.parseFloat("1"));
        f = new Float(Float.parseFloat("1"));
        Double d = Double.valueOf(Double.parseDouble("1"));
        d = new Double(Double.parseDouble("1"));
    }

    public void testBoxToUnbox() {
        boolean bo = new Boolean(true).booleanValue();
        bo = Boolean.valueOf(true).booleanValue();
        byte b = new Byte((byte) 1).byteValue();
        b = Byte.valueOf((byte) 1).byteValue();
        short s = new Short((short) 2).shortValue();
        s = Short.valueOf((short) 2).shortValue();
        int i = new Integer(3).intValue();
        i = Integer.valueOf(3).intValue();
        long l = new Long(4).longValue();
        l = Long.valueOf(4).longValue();
        float f = new Float(5.0f).floatValue();
        f = Float.valueOf(5.0f).floatValue();
        double d = new Double(6.0).doubleValue();
        d = Double.valueOf(6.0).doubleValue();
    }

    public void testBoxedCast() {
        short s = new Short((short) 2).byteValue();
        s = Short.valueOf((short) 2).byteValue();
        int i = new Integer(3).byteValue();
        i = Integer.valueOf(3).byteValue();
        i = new Integer(3).shortValue();
        i = Integer.valueOf(3).shortValue();
        long l = new Long(4).byteValue();
        l = Long.valueOf(4).byteValue();
        l = new Long(4).shortValue();
        l = Long.valueOf(4).shortValue();
        l = new Long(4).intValue();
        l = Long.valueOf(4).intValue();
        float f = new Float(5.0f).byteValue();
        f = Float.valueOf(5.0f).byteValue();
        f = new Float(5.0f).shortValue();
        f = Float.valueOf(5.0f).shortValue();
        f = new Float(5.0f).intValue();
        f = Float.valueOf(5.0f).intValue();
        f = new Float(5.0f).longValue();
        f = Float.valueOf(5.0f).longValue();
        double d = new Double(6.0).byteValue();
        d = Double.valueOf(6.0).byteValue();
        d = new Double(6.0).shortValue();
        d = Double.valueOf(6.0).shortValue();
        d = new Double(6.0).intValue();
        d = Double.valueOf(6.0).intValue();
        d = new Double(6.0).longValue();
        d = Double.valueOf(6.0).longValue();
        d = new Double(6.0).floatValue();
        d = Double.valueOf(6.0).floatValue();
    }

    public Boolean testBooleanConsts(String s) {
        boolean b = Boolean.FALSE;
        b = Boolean.TRUE;
        Boolean bb = false;
        bb = true;

        return Boolean.valueOf(s.equals("true") && bb.booleanValue());
    }

    public Boolean testBooleanReturns() {
        return true;
    }

    public Integer testfpTernary(Integer i, int[] data) {
        Integer j = (i == null) ? data.length : i;
        return j;
    }
    
    public void testFNTernary365() {
    	System.err.println(Arrays.asList( new Object[] {"1", false}));
    	System.err.println(processTwo((Math.random() > 0.5 ? "" : "1"), false));
    }
    
    public static <E> List<E>  processTwo(E e1, E e2) {
    	List<E> l = new ArrayList<>();
    	l.add(e1);
    	l.add(e2);
    	return l;
    }
}
