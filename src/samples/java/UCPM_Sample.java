
public class UCPM_Sample {

    private StringBuffer sb; // made this a field to avoid "unnecessary use of
                             // synchronized class"

    public int test(String foo) {
        sb = new StringBuffer();
        // no tag
        sb.append('f');
        // tag UCPM_USE_CHARACTER_PARAMETERIZED_METHOD
        sb.append("f");
        // tag UCPM_USE_CHARACTER_PARAMETERIZED_METHOD
        sb.append("f").append("o");
        // tag UCPM_USE_CHARACTER_PARAMETERIZED_METHOD
        sb.append('f').append("o");

        StringBuilder sb2 = new StringBuilder();
        // tag UCPM_USE_CHARACTER_PARAMETERIZED_METHOD
        sb2.append("g");
        // no tag
        sb2.append('g');

        // tag UCPM_USE_CHARACTER_PARAMETERIZED_METHOD
        System.out.println(foo.replace(".", ","));

        // no tag
        System.out.println(foo.replace(".", ".."));
        // no tag
        System.out.println(foo.replace("..", ","));
        // no tag
        System.out.println(foo.replace('.', ','));

        // tag UCPM_USE_CHARACTER_PARAMETERIZED_METHOD
        System.out.println(foo.lastIndexOf("."));
        // no tag
        System.out.println(foo.lastIndexOf('.'));

        // tag UCPM_USE_CHARACTER_PARAMETERIZED_METHOD
        return foo.indexOf("*") * 10;

    }

    public String testUcpm2(String s) {
        // here to prevent a "used only as locals" bug notification
        System.out.println(sb);

        // tag UCPM_USE_CHARACTER_PARAMETERIZED_METHOD
        System.out.println(s + ":" + s);

        // no tag, the compiler usually optimizes this by concatenating "a" and
        // "b" into a "ab"
        System.out.println("a" + "b" + s);
        // no tag
        System.out.println(s + " : " + s);

        // no tag, starts with doesn't have a char equivalent
        return s.startsWith("*") ? s.substring(1) : s;
    }

    public String fpGitHubIssue95(int value) {
        return "(" + value + ")";
    }

    public String okIssue95(int value) {
        StringBuilder sb = new StringBuilder();
        return sb.append("(").append(value).append(")").toString();
    }
}
