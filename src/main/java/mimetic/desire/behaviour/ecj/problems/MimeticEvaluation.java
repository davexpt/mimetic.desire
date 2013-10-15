package mimetic.desire.behaviour.ecj.problems;

import java.util.ArrayList;

import org.apache.commons.math3.util.FastMath;

import mimetic.desire.behaviour.Competition;
import mimetic.desire.behaviour.MimeticBehaviour;
import sim.util.Double2D;
import ec.EvolutionState;
import ec.Individual;
import ec.cgp.FitnessCGP;
import ec.cgp.genome.CGPIndividual;
import ec.simple.SimpleStatistics;

/**
 * This problem is used to evaluate a set of controlling programs for an agent.
 * 
 * 
 * 
 * @author Davide Nunes
 * 
 */
public class MimeticEvaluation extends AbstractEvoBehaviourProblem {
	private static final long serialVersionUID = 1L;

	@Override
	public void evaluate(EvolutionState state, Individual ind,
			int subpopulation, int threadnum) {

		Competition behave = (Competition) super.behaviour;

		ArrayList<Double> mimeticError = behave
				.getImitationErrors((CGPIndividual) ind);

		if (mimeticError == null) {
			throw new RuntimeException(
					"FitnessExploitation Problem is trying to retrieve a fitness record for an individual that was not evaluated, probably, evolve was called before all the controllers could be evaluated");
		} else {
			float fitness = 0;
			fitness = (float) behave
					.currentCompetitionResult((CGPIndividual) ind);

			// fitness += behave.currentCompetitionResult((CGPIndividual) ind);

			// fitness = fitness);

			// instead of mean try the last value ending up in a good fitness
			// fitness = (float) (1.0 * fitnessRecord
			// .get(fitnessRecord.size() - 1));

			// change false to the condition of being ideal
			((FitnessCGP) ind.fitness).setFitness(state, fitness, false);
			ind.evaluated = true;
		}

	}
}
