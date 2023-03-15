package ex;

import org.apache.commons.lang3.builder.ToStringBuilder;

public class CSBTS_StringToStringSample {
	static class Person {
		String name;
		int age;

		Person(String name, int age) {
			this.name = name;
			this.age = age;
		}

		@Override
		public String toString() {
			// INCORRECT USAGE : The same as invoking Object.toString()
			// return new ToStringBuilder(this).toString();
			// Consider using for non final classes to support a
			// string representation for derived types
			// return ToStringBuilder.reflectionToString(this);
			// Use for final classes most efficient solution
			return new ToStringBuilder(this).append("name", name).append("age", age).toString();
		}
	}

	private enum SEX {
		Male, Female;
	}

	public final static class GenderPerson extends Person {
		private SEX sex;

		GenderPerson(String name, int age, SEX sex) {
			super(name, age);
			this.sex = sex;
		}
	}

	public static void main(String[] args) {
		Person p = new Person("John Doe", 2);
		ToStringBuilder x = new ToStringBuilder(p);
		ToStringBuilder y = new ToStringBuilder(p);
		// INCORRECT USAGE : The same as invoking Object.toString
		System.out.println("P " + new ToStringBuilder(p).toString());
		// Consider using for non final classes to support a string
		// representation for derived types
		System.out.println("P " + ToStringBuilder.reflectionToString(p));
		GenderPerson p2 = new GenderPerson("Jane Doe", 2, SEX.Female);
		System.out.println("GP " + new ToStringBuilder(p2).append("name", p2.name).append("age", p2.age)
				.append("sex", p2.sex).toString());
		// Y now has an append
		y.append("name", p.name);
		System.out.println("P - Once Again " + y.toString());
		System.out.println("P - Again " + x.toString());
	}

	public class FPAppend extends ToStringBuilder {

		FPAppend(Object object) {
			super(object);
		}

		@Override
		public ToStringBuilder append(Object obj) {
			return super.append(obj);
		}

	}

}
