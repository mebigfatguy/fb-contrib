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

    private void fp223Aliasing() {
        Holder h = new Holder();
        int[] data = h.getData();

        if (h.checkIt() && (data[0] == 1)) {
            System.out.println("Got It");
        }
    }
    
    private String fpArrays(String[] data) {
    	if (isEmpty(data) && data[0] != null && data[1] != null)
    		return data[0] + data[1];
    	else {
    		return "";
    	}
    }
    
    private boolean isEmpty(String[] d) {
    	return d == null || d.length == 0;
    }

    class Holder {
        private int[] data;

        public int[] getData() {
            return data;
        }

        boolean checkIt() {
            return data.length > 0;
        }
    }

    class Issue248 {
        Issue248 b;
        boolean c;

        public Issue248 b() {
            return b;
        }

        public void fpFieldOffUnknownObj(Issue248 a) {
            if ((a.b() != null) && a.b().c) {
                System.out.println("Not a SEO");
            }
        }
    }

    class FNIssue266 {

        public void testStoredResult() {
            boolean c1 = aComedy("Baz");

            if (aComedy("Baf") || c1) {
                System.out.println("Booyah");
            }
        }

        public boolean aComedy(String input) {
            String result = "foo";
            while ((result.length() > 0) || result.startsWith(input)) {
                if (Math.random() > 0.5) {
                    result += "bar";
                } else {
                    result = result.substring(1);
                }
            }

            return (result.length() > 10) && (result.length() < 20);
        }
    }
}
