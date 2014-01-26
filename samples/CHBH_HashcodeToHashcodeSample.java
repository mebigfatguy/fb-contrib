import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class CHBH_HashcodeToHashcodeSample {
    public final String name;
    public final int age;

    CHBH_HashcodeToHashcodeSample(String name, int age) {
        this.name = name;
        this.age = age;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(name).append(age).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CHBH_HashcodeToHashcodeSample other = (CHBH_HashcodeToHashcodeSample) obj;
        return new EqualsBuilder().append(this.name, other.name).append(this.age, other.age).isEquals();
    }
}
