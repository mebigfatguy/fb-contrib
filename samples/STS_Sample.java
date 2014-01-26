import java.util.Date;

public class STS_Sample {

    public static void main(String[] args) throws InterruptedException {
        // creating the calculator instance, to pass it to the Reader threads
        Calculator calculator = new Calculator();

        // starting the Reader threads(s)
        new Reader(calculator).start();
        new Reader(calculator).start();
        new Reader(calculator).start();

        // starting the calculator thread
        System.out.println(new Date() + ": I will start now a delaty time of" + " 2 seconds before starting the calculator thread");
        Thread.sleep(2000);
        System.out.println(new Date() + ": I just finished the 2 seconds delay " + " and I will start the calculator thread");
        calculator.start();
    }

    static class Reader extends Thread {
        Calculator c;

        public Reader(Calculator calc) {
            c = calc;
        }

        @Override
        public void run() {
            synchronized (c) {
                try {
                    System.out.println(new Date() + ": Waiting for calculation...");
                    c.wait();
                    System.out.println(new Date() + ": I am just after the wait()");
                } catch (InterruptedException e) {
                }

                System.out.println(new Date() + ": Total is: " + c.total);
            }
        }
    }

    static class Calculator extends Thread {
        int total;

        @Override
        public void run() {
            synchronized (this) {
                try {
                    for (int i = 0; i < 100; i++) {
                        total += 1;
                    }

                    Thread.sleep(1000);
                    notify();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
