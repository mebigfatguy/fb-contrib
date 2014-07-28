public class CU_Sample implements Cloneable {

	@Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    class CU_FP implements Cloneable {
    	@Override
        public CU_FP clone() {
            try {
                return (CU_FP) super.clone();
            } catch (CloneNotSupportedException cnse) {
                throw new Error("Won't happen");
            }
        }
    }

    class Unrelated implements Cloneable {
    	@Override
        public String clone() {
            try {
                return (String) super.clone();
            } catch (CloneNotSupportedException cnse) {
                throw new Error("Won't happen");
            }
        }
    }

    class FPCloneInterface implements Cloneable, Runnable {
    	@Override
        public Runnable clone() {
            try {
                return (Runnable) super.clone();
            } catch (CloneNotSupportedException cnse) {
                throw new Error("Won't happen");
            }
        }

    	@Override
        public void run() {
        }
    }

    class FPActuallyThrow implements Cloneable {
    	@Override
        public FPActuallyThrow clone() throws CloneNotSupportedException {
            throw new CloneNotSupportedException("Silly");
        }
    }
}
