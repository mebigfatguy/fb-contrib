
public class CAO_Sample {
    public void testci(Character c) {
        System.out.println("Character");
    }

    public void testci(int i) {
        System.out.println("int");
    }

    public void testcj(Character c) {
        System.out.println("Character");
    }

    public void testcj(long l) {
        System.out.println("long");
    }

    public void testcd(Character c) {
        System.out.println("Character");
    }

    public void testcd(double d) {
        System.out.println("double");
    }

    public void testcf(Character c) {
        System.out.println("Character");
    }

    public void testcf(float f) {
        System.out.println("float");
    }

    public static void main(String[] args) {
        CAO_Sample s = new CAO_Sample();
        s.testci('a');
        s.testcj('a');
        s.testcd('a');
        s.testcf('a');
    }
}