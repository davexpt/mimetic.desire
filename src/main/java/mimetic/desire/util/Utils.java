package mimetic.desire.util;

import org.apache.commons.math3.analysis.function.Sigmoid;

/**
 * Any reusable utilities will be placed in this class
 * 
 * @author Davide Nunes
 * 
 */
public class Utils {
	/**
	 * Used to scale values between a min and a max value (min-max
	 * normalization)
	 * 
	 * @param value
	 *            the value to be scalled
	 * @param min
	 *            the lower bound of the range
	 * @param max
	 *            the upper bound of the range
	 * @return
	 */
	public static double scale(double value, double min, double max) {
		if (min == max)
			return 0;

		double result = 2 * ((value - min) / (max - min)) - 1;
		return result;
	}

	/**
	 * Apply a sigmoid function between -1 and 1
	 * 
	 * @param value
	 *            the value to be "squashed"
	 * 
	 * @return the resulting value
	 */
	public static double squash(double value) {
		Sigmoid sig = new Sigmoid(-1.0, 1.0);
		double result = sig.value((double) value);
		return result;
	}

}
