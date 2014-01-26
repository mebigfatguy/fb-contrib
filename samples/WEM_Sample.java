public class WEM_Sample {
    public void badException(String s) {
        if (s.length() == 1)
            throw new IllegalArgumentException("You stink");
    }

    public void goodException(String s) {
        if (s.length() == 1)
            throw new IllegalArgumentException("You stink -->" + s);
    }

    public static void ok() {
        throw new RuntimeException("Wow");
    }

    public void fpunimpled() {
        throw new UnsupportedOperationException("fpunimpled is unimpled");
    }
}
