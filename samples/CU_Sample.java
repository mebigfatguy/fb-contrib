
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
}
