import java.util.Random;

public class SCO_Sample {

    public void sco() {
        int a = calcA();
        int b = calcB();
        
        System.out.println(a + b);
        
        if (hasC() && (a == 0) && (b == 0)) {
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
