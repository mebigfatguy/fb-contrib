package ex;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocketFactory;

public class MDM_Sample implements Runnable {
    private ReentrantLock myLock;
    private CyclicBarrier barrier;

    public MDM_Sample() throws Exception {
        boolean b;

        { // Halt tests
            Runtime r = Runtime.getRuntime();
            r.exit(0); // WARNING
            r.halt(0); // WARNING
            r.runFinalization(); // WARNING
            System.runFinalization(); // WARNING
        }

        { // equals() tests
            BigDecimal bd1 = new BigDecimal(0);
            BigDecimal bd2 = new BigDecimal(0);
            b = bd1.equals(bd2); // WARNING
        }

        { // Socket tests
            InetAddress localhost = InetAddress.getLocalHost(); // WARNING
            if (localhost == null) {
                localhost = InetAddress.getByName("booya");
            }
            ServerSocket ss = new ServerSocket(0);
            touch(ss); // WARNING
            ss = new ServerSocket(0, 0);
            touch(ss); // WARNING
            ServerSocketFactory ssf = SSLServerSocketFactory.getDefault();
            ss = ssf.createServerSocket(0);
            touch(ss); // WARNING
            ss = ssf.createServerSocket(0, 0);
            touch(ss); // WARNING
        }

        { // RNG tests
            Random r = new Random();// WARNING
            touch(r);
            byte[] seed = SecureRandom.getSeed(1); // WARNING (jdk 1.5 or older)
            r = new SecureRandom(seed); // WARNING (jdk 1.5 or older)
            touch(r);
        }

        { // Thread tests
            Thread t = new Thread(this);
            int priority = t.getPriority(); // WARNING
            t.setPriority(priority); // WARNING
            t.join(); // WARNING

            Thread.sleep(0); // WARNING
            Thread.sleep(0, 0); // WARNING
            Thread.yield(); // WARNING
        }

        { // Timeout tests
            ReentrantLock rl = new ReentrantLock();
            rl.lock(); // WARNING
            rl.lockInterruptibly(); // WARNING
            b = rl.isHeldByCurrentThread(); // WARNING
            b = rl.isLocked(); // WARNING

            Object o = new Object();
            do {
                b = rl.tryLock(); // WARNING
                o.wait(); // WARNING
            } while (b);

            Lock l = rl;
            l.lock(); // WARNING
            b = l.tryLock();
            touch(b); // WARNING
            l.lockInterruptibly(); // WARNING

            Condition c = l.newCondition();
            c.signal(); // WARNING
            c.await(); // WARNING
        }

        { // String tests
            byte[] bytes = "".getBytes(); // WARNING
            String s = new String(bytes); // WARNING
            bytes = s.getBytes("UTF-8");

            Locale.setDefault(Locale.ENGLISH); // WARNING
        }
    }

    @Override
    public void run() {
    }

    private static void touch(Object o) {
    }

    private void fpAssertReentrantLock() {
        assert myLock.isHeldByCurrentThread();
    }

    private void fpCyclicBarrier() throws Exception {
        barrier.await(1, TimeUnit.MINUTES);

    }
}
