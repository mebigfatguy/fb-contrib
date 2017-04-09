package ex;
public class SACM_Sample {
    public String test(int i) {
        String[] giantSounds = new String[] { "fee", "fi", "fo", "fum", "burp", "fart" };
        if ((i < 0) || (i >= giantSounds.length))
            return "";

        return giantSounds[i];
    }
}
