package mimetic.desire.behaviour.ecg.functions;

import java.util.HashSet;
import java.util.Set;

import ec.cgp.functions.AbstractFunctionSet;
import ec.cgp.functions.Function;
import ec.cgp.functions.numeric.AddFn;
import ec.cgp.functions.numeric.DivFn;
import ec.cgp.functions.numeric.MulFn;
import ec.cgp.functions.numeric.SinFn;
import ec.cgp.functions.numeric.SubFn;

/**
 * A set of functions to be used in a CGP test experiment. This function set
 * includes the basic arithmetic functions (+, -, *, div) where the div is a
 * protected division that returns 1 in case of division by 0.
 * 
 * @author Davide Nunes
 * 
 * 
 * 
 */
public class Movement extends AbstractFunctionSet<Double> {

	public Movement() {
		super();
	}

	@Override
	public Class<Double> getType() {
		return Double.class;
	}

	@Override
	protected Set<Function<Double>> loadFunctions() {
		Set<Function<Double>> functions = new HashSet<>();

		functions.add(new AddFn());
		functions.add(new SubFn());
		functions.add(new MulFn());
		functions.add(new DivFn());
		functions.add(new SinFn());

		return functions;
	}
}
