package mimetic.desire.behaviour.ecj.problems.meta;

import java.util.ArrayList;

import mimetic.desire.behaviour.ecj.problems.AbstractEvoBehaviourProblem;
import mimetic.desire.behaviour.meta.MetaCompetition;
import ec.EvolutionState;
import ec.Individual;
import ec.cgp.FitnessCGP;
import ec.cgp.genome.CGPIndividual;

public class BehaviourEvolution extends AbstractEvoBehaviourProblem {
	private static final long serialVersionUID = 1L;

	@Override
	public void evaluate(EvolutionState state, Individual ind,
			int subpopulation, int threadnum) {

		// evaluation behaviour based on energy samples from best objective
		// function
		MetaCompetition b = (MetaCompetition) behaviour;

		
		CGPIndividual currentController = (CGPIndividual) ind;
		CGPIndividual bestObjective = b.getObjectiveFunction();

		ArrayList<Double> energySamples = b.getEnergySamples(currentController,
				bestObjective);

		float fitnessValue = 0.0f;

		// average of energy samples
		for (Double value : energySamples) {
			fitnessValue += value;
		}
		fitnessValue /= energySamples.size();

		// set fitness value based on the energy samples from the best objective
		// function

		FitnessCGP fitness = (FitnessCGP) currentController.fitness;
		fitness.setFitness(state, fitnessValue, false);
		currentController.evaluated = true;
	}
}
