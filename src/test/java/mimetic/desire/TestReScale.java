package mimetic.desire;

import static org.junit.Assert.*;

import org.junit.Test;

public class TestReScale {

	@Test
	public void test() {
		for (int i = 0; i < 10; i++) {
			System.out.println("input:" + i);
			double r = scaleFitness(i);
			System.out.println("scaled:" + r);
			assertTrue(i == 0 || r == 1);
		}

		for (int i = 0; i < 10; i++) {
			System.out.println("input:" + i);
			double r = scaleFitness(i);
			System.out.println("scaled:" + r);
			assertTrue(i == 9 || r != 1);
		}
	}

	// min / max fitenss
	private double minFitness = 0.0;
	private double maxFitness = 0.1;

	private double scaleFitness(double fitness) {

		// found a new max
		if (fitness > maxFitness) {
			maxFitness = fitness;
		}

		// rescale
		double result = 2 * ((fitness - minFitness) / (maxFitness - minFitness)) - 1;

		return result;
	}

}
