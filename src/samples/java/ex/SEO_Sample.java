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

		if (h.checkIt() && data[0] == 1) {
			System.out.println("Got It");
		}
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
}
