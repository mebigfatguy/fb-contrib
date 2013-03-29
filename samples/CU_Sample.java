
public class CU_Sample implements Cloneable {

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    class CU_FP implements Cloneable {
        public CU_FP clone() {
            try {
                return (CU_FP) super.clone();
            } catch (CloneNotSupportedException cnse) {
                throw new Error("Won't happen");
            }
        }
    }

    class Unrelated implements Cloneable {
        public String clone() {
            try {
                return (String) super.clone();
            } catch (CloneNotSupportedException cnse) {
                throw new Error("Won't happen");
            }
        }
    }

    class FPCloneInterface implements Cloneable, Runnable {
        public Runnable clone() {
            try {
                return (Runnable) super.clone();
            } catch (CloneNotSupportedException cnse) {
                throw new Error("Won't happen");
            }
        }

        public void run() {
        }
    }
}
