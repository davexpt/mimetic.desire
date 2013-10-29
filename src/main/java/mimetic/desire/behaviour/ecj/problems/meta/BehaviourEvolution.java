package mimetic.desire.behaviour.ecj.problems.meta;

import mimetic.desire.behaviour.ecj.problems.AbstractEvoBehaviourProblem;
import mimetic.desire.behaviour.meta.MetaCompetition;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

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

		CGPIndividual controller = (CGPIndividual) ind;
		CGPIndividual objective = b.getObjectiveFunction();

		DescriptiveStatistics energyStats = b.getEnergyStats(controller,
				objective);

		float fitnessValue = (float) energyStats.getMean();

		// set fitness value based on the energy samples from the best objective
		// function

		FitnessCGP fitness = (FitnessCGP) controller.fitness;
		fitness.setFitness(state, fitnessValue, false);
		controller.evaluated = true;
	}
}
