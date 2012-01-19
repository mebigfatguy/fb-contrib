import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class CEBE_EqualsToEqualsSample {
	public final String name;
	public final int age;

	CEBE_EqualsToEqualsSample(String name, int age) {
		this.name = name;
		this.age = age;
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder().append(name).append(age).toHashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CEBE_EqualsToEqualsSample other = (CEBE_EqualsToEqualsSample) obj;
		return new EqualsBuilder().append(this.name, other.name)
				.append(this.age, other.age).equals(obj);
	}

}
