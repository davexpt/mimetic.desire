package mimetic.desire.behaviour.ecj.problems.meta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.MathArrays;

import mimetic.desire.behaviour.ecj.problems.AbstractEvoBehaviourProblem;
import mimetic.desire.behaviour.meta.MetaCompetition;
import ec.EvolutionState;
import ec.Individual;
import ec.cgp.FitnessCGP;
import ec.cgp.genome.CGPIndividual;

public class ObjectiveEvolution extends AbstractEvoBehaviourProblem {

	@Override
	public void evaluate(EvolutionState state, Individual ind,
			int subpopulation, int threadnum) {
		MetaCompetition b = (MetaCompetition) behaviour;

		CGPIndividual currentObjective = (CGPIndividual) ind;
		CGPIndividual bestObjective = b.getObjectiveFunction();

		CGPIndividual trainer = getTrainer();
		//
		// ArrayList<Double> compEnergySamples =
		// b.getCompetitionEnergySamples();
		// ArrayList<Double> energySamples =
		// b.getEnergySamples(currentController, bestObjective)

		float fitnessValue = 0.0f;

		// FitnessCGP fitness = (FitnessCGP) currentController.fitness;
		// fitness.setFitness(state, fitnessValue, false);
		// currentController.evaluated = true;

	}

	/**
	 * Returns the controller with more variance in relation to the objective
	 * function population
	 * 
	 * @return
	 */
	private CGPIndividual getTrainer() {
		MetaCompetition b = (MetaCompetition) behaviour;

		Map<Individual, Double> stats = new HashMap<>();

		// compute the objective fitness for all the predictors
		for (Individual controller : b.getControllers()) {
			DescriptiveStatistics predictedFitness = new DescriptiveStatistics();
			for (Individual objective : b.getObjectives()) {
				DescriptiveStatistics energyStats = b.getEnergyStats(
						(CGPIndividual) controller, (CGPIndividual) objective);
				predictedFitness.addValue(energyStats.getMean());
			}
			stats.put(controller, predictedFitness.getMean());

		}
		for (Individual objective : b.getObjectives()) {
			for (Individual controller : b.getControllers()) {
				DescriptiveStatistics energyStats = b.getEnergyStats(
						(CGPIndividual) controller, (CGPIndividual) objective);
				energyStats.getV
			}
		}

		return null;
	}

}
