import java.io.Serializable;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;

import com.google.common.base.Optional;

@SuppressWarnings("all")
public class SPP_Sample implements Serializable {
    public static final long serialVersionUID = -2766574418713802220L;

    private static final double pi = 3.14;
    private static final double e = 2.72;
    public static final String FALSE_POSITIVE = "INTERN_OK_HERE".intern();
    private static final String LIT = "lit";

    static enum Flap {
        Smack, Jack
    };

    public void testSPPBitSet(BitSet b) {
        b.set(-1);
    }

    public String testSPPIntern() {
        return "FOO".intern(); // and yes i've seen this!
    }

    public String testSBWithChars() {
        StringBuffer sb = new StringBuffer('v');
        sb.append("ictory");
        return sb.toString();
    }

    public double area(double radius) {
        return pi * radius * radius;
    }

    public void testStutter(String s) {
        String a = a = s;
    }

    public void testNAN(double d) {
        if (d == Double.NaN) {
            System.out.println("It's a nan");
        }
    }

    public void testNAN2(Double d) {
        if (d == Double.NaN) {
            System.out.println("It's a nan");
        }

        if (d.isNaN()) {
            System.out.println("It's properly a nan");
        }
    }

    public void testNotNAN(double d) {
        if (d != Double.NaN) {
            System.out.println("It's not a nan");
        }

        if (!Double.isNaN(d)) {
            System.out.println("It's properly not a nan");
        }
    }

    public void testNAN(float f) {
        if (f == Float.NaN) {
            System.out.println("It's a nan");
        }
    }

    public void testBigDecimal() {
        BigDecimal d = new BigDecimal(2.1);
        System.out.println(d);
    }

    public void testEmptySB() {
        StringBuffer sb = new StringBuffer("");
    }

    public void equalsOnEnum(Flap f) {
        if (f.equals(Flap.Jack)) {
            System.out.println("Flap Jacks");
        }
    }

    public void testCPPBoolean(Boolean a, Boolean b, Boolean c, Boolean d, Boolean e) {
        if (b && b.booleanValue()) {
            System.out.println("Booya");
        }
        if (e && e.booleanValue()) {
            System.out.println("Booya");
        }
    }

    public char usechatAt(String s) {
        if (s.length() > 0) {
            return s.toCharArray()[0];
        }
        return ' ';
    }

    public boolean testUselessTrinary(boolean b) {
        return (b ? true : false);
    }

    public void testDoubleAppendLiteral(StringBuilder sb, String s) {
        sb.append("hello").append("there");
        sb.append("Hello").append(s).append("there");
    }

    public String testFormatLiteral() {
        return String.format("This string is not parameterized");
    }

    public String testFPDoubleAppendListeralStatic() {
        StringBuilder sb = new StringBuilder();
        sb.append("literal").append(LIT).append("literal");
        return sb.toString();
    }

    public boolean testFPUselessTrinary(boolean a, boolean b) {
        if (a && b) {
            return a || b;
        }

        return a && b;
    }

    public boolean testFPTrinaryOnInt(String s) {
        return (s.length() != 0);
    }

    public void testSuspiciousStringTests(String s) {
        int a = 0, b = 0, c = 0, d = 0;
        String e = "Foo";

        if ((s == null) || (s.length() > 0)) {
            System.out.println("Booya");
        }
        if ((s == null) || (s.length() != 0)) {
            System.out.println("Booya");
        }
        if ((s != null) && (s.length() == 0)) {
            System.out.println("Booya");
        }

        if ((e == null) || (e.length() > 0)) {
            System.out.println("Booya");
        }
        if ((e == null) || (e.length() != 0)) {
            System.out.println("Booya");
        }
        if ((e != null) && (e.length() == 0)) {
            System.out.println("Booya");
        }
    }

    public void testFPSST(String s) {
        int a = 0, b = 0, c = 0, d = 0;
        String e = "Foo";

        if ((s == null) || (s.length() == 0)) {
            System.out.println("Booya");
        }

        if ((s != null) && (s.length() >= 0)) {
            System.out.println("Booya");
        }

        if ((s != null) && (s.length() != 0)) {
            System.out.println("Booya");
        }

        if ((e == null) || (e.length() == 0)) {
            System.out.println("Booya");
        }

        if ((e != null) && (e.length() >= 0)) {
            System.out.println("Booya");
        }

        if ((e != null) && (e.length() != 0)) {
            System.out.println("Booya");
        }

        Set<String> m = new HashSet<String>();
        Iterator<String> it = m.iterator();
        while (it.hasNext()) {
            s = it.next();
            if ((s == null) || (s.length() == 0)) {
                continue;
            }

            System.out.println("Booya");
        }
    }

    public void sbToString(StringBuffer sb) {
        if (sb.toString().length() == 0) {
            System.out.println("Booya");
        } else if (sb.toString().equals("")) {
            System.out.println("Booya");
        }
    }

    public String cpNullOrZero(StringTokenizer tokenizer) {
        while (tokenizer.hasMoreTokens()) {
            String sField = tokenizer.nextToken();

            if ((sField == null) || (sField.length() == 0)) {
                continue;
            }

            return sField;
        }

        return null;
    }

    public boolean testCalBeforeAfter(Calendar c, Date d) {
        return c.after(d) || c.before(d);
    }

    public void testUseContainsKey(Map m) {
        if (m.keySet().contains("Foo")) {
            System.out.println("Yup");
        }
    }

    public void testCollectionSizeEqualsZero(Set<String> s) {
        if (s.size() == 0) {
            System.out.println("empty");
        }

        if (s.size() <= 0) {
            System.out.println("empty");
        }
    }

    public boolean testDerivedGregorianCalendar() {
        Calendar c = new GregorianCalendar() {
        };
        Calendar s = new GregorianCalendar();

        return s.after(c);
    }

    public void testGetProperties() {
        String lf = System.getProperties().getProperty("line.separator");
    }

    public boolean testCasing(String a, String b) {
        if (a.toUpperCase().equalsIgnoreCase(b)) {
            return true;
        }

        if (a.toLowerCase().compareToIgnoreCase(b) == 0) {
            return true;
        }

        return false;
    }

    public void castRandomToInt() {
        int i = (int) Math.random();

        Random r = new Random();
        i = (int) r.nextDouble();

        i = (int) r.nextFloat();
    }

    public void testSAC(List<String> input) {
        String[] copy = new String[input.size()];
        System.arraycopy(input, 0, copy, 0, copy.length);

        System.arraycopy(copy, 0, input, 0, copy.length);
    }

    public void testArray() {
        List<String> notAnArray = new ArrayList<String>();

        Array.getLength(notAnArray);

        Array.getBoolean(notAnArray, 0);

        Array.setInt(notAnArray, 0, 1);
    }

    public boolean testEmptyIgnoreCase(String s) {
        return (s.equalsIgnoreCase(""));
    }

    public void testTrim(String s) {
        if (s.trim().length() > 0)
            System.out.println(s);

        if (s.trim().equals("Booyah")) {
            System.out.println("Booyah->" + s);
        }
    }

    public void testSBAssigning(StringBuilder sb) {
        sb = sb.append("foo");
        sb = sb.append("foo").append("boo").append("hoo");
    }

    public String testListFirst(List<String> l) {
        return l.iterator().next();
    }

    public boolean nullAndInstanceOf(Object o) {
        if ((o != null) && (o instanceof String)) {
            return true;
        }
        return false;
    }

    public boolean nullAndInstanceOf(double d1, double d2, double d3, Object o) {
        if ((o != null) && (o instanceof String) && d1 < d2) {
            return true;
        }
        return false;
    }

    public String testStringToString(String x) {
        // tag SPP_TOSTRING_ON_STRING (fb-contrib) and DM_STRING_TOSTRING
        // (FindBugs)
        System.out.println(x.toString());

        // tag DM_CONVERT_CASE (FindBugs) and SPP_CONVERSION_OF_STRING_LITERAL
        System.out.println("SomeUpperCase".toLowerCase());
        // tag SPP_CONVERSION_OF_STRING_LITERAL
        System.out.println("SomeUpperCase".toLowerCase(Locale.US));
        // tag SPP_CONVERSION_OF_STRING_LITERAL
        System.out.println("SomeUpperCase".toUpperCase());
        // tag SPP_CONVERSION_OF_STRING_LITERAL
        System.out.println("SomeUpperCase".toUpperCase(Locale.CANADA));
        // tag SPP_CONVERSION_OF_STRING_LITERAL
        System.out.println("  SomeUpperCase ".trim());

        // no tag
        System.out.println(x.toLowerCase());
        // no tag
        System.out.println(x.toLowerCase(Locale.US));
        // no tag
        System.out.println(x.toUpperCase());
        // no tag
        System.out.println(x.toUpperCase(Locale.CANADA));
        // no tag
        System.out.println(x.trim());
        return x;
    }
    
    public String testOptional(Optional<String> o) {
        if (o == null) {
            return "";
        }
        
        return o.get();
    }

    public boolean fpNullAndInstanceOf(Object o) {
        if (o != null) {
            if (o instanceof String) {
                return true;
            }
            return false;
        }
        return Math.random() > 0.5;
    }
    
    public boolean fpNullAndInstanceOfUnrelated(Throwable t, Object tag) {
        if ((tag != null) && (t instanceof RuntimeException)) {
            return true;
        }
        
        return false;
    }

    public void testToString() {
        SPP_Sample s = new SPP_Sample();
        System.out.println(s.toString());
        /* only report it once */
        System.out.println(s.toString());

    }

    public void testFPToString(Object o) {
        System.out.println(o);
    }
    
    public boolean testFPTrimNotUsed(StringProducer s, String t) {
        if (s.getString().trim().length() == 0) {
            return true;
        }
        
        return t.equals("foo");
        
    }
    
    public void fpGitHubIssue81(PreparedStatement sqlQuery, String name) throws SQLException {
        if (name != null && !(name = name.trim()).equals("")) {
            sqlQuery.setString(1, name + "%");
        }
    }
}

class StringProducer {
    public String getString() {
        return "foo";
    }
}
