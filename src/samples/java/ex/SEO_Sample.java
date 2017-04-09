package ex;
import java.util.Random;

public class SEO_Sample {

    public void seo() {
        int a = calcA();
        int b = calcB();
        
        System.out.println(a + b);
        
        if (hasC() && (a == 0) && (b > 3)) {
            System.out.println(a - b);
        }
    }
    
    
    private int calcA() {
        Random r = new Random();
        return r.nextInt(10);
    }
    
    private int calcB() {
        Random r = new Random();
        return r.nextInt(10);
    }
    
    private boolean hasC() {
        Random r = new Random();
        return r.nextBoolean();
    }
}
