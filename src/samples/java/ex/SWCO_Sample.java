package ex;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

public class SWCO_Sample {
    CountDownLatch cdl;
    CyclicBarrier cb;

    public SWCO_Sample() {
        cdl = new CountDownLatch(2);
        cb = new CyclicBarrier(2);
    }

    public void waitCDL() throws InterruptedException {
        cdl.wait();
    }

    public void waitCB() throws InterruptedException {
        cb.wait();
    }
}
