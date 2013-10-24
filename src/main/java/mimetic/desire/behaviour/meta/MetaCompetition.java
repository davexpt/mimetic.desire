package mimetic.desire.behaviour.meta;

import java.io.File;
import java.util.Collections;

import javax.management.RuntimeErrorException;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math3.analysis.function.Sigmoid;

import ec.EvolutionState;
import ec.Evolve;
import ec.Individual;
import ec.Problem;
import ec.cgp.eval.CGPSteppableInterpreter;
import ec.cgp.genome.CGPIndividual;
import ec.simple.SimpleStatistics;
import ec.util.Output;
import ec.util.ParameterDatabase;
import mimetic.desire.Agent;
import mimetic.desire.MimeticDesire;
import mimetic.desire.behaviour.AbstractBehaviour;
import mimetic.desire.behaviour.ecj.problems.MimeticEvaluation;
import mimetic.desire.behaviour.ecj.problems.meta.BehaviourEvolution;
import mimetic.desire.behaviour.ecj.problems.meta.ObjectiveEvolution;
import mimetic.desire.util.Utils;

/**
 * TODO move part of this code to an abstract MetaBehaviour class.
 * 
 * Meta behaviors should contain a objective function that drives the evolution
 * of the agent behaviour. This is done by supplying the current objective
 * function as the fitness evaluator for the agent performance. We need an ECJ
 * {@link Problem} that is used to evaluate the agent controllers and another
 * one that evaluates the objective function based on the agent performance.
 * 
 * We are trying to approximate a function that leads the agent behaviour to
 * perform better according to an objective or set of overall objectives.
 * 
 * Given that the objective is to find the region with higher fitness in the
 * fitness landscape and stabilize the agent in that region we need to
 * understand what fitness function leads to that desired state.
 */
public class MetaCompetition extends AbstractBehaviour {
	// Evolutionary States
	private EvolutionState behaviourEvo;
	private EvolutionState objectiveEvo;

	// behaviour records the number times it steps
	private int steps;

	// the cgp interpreter used to execute the programs
	// this works with any CGPIndividual because it relies on the species to
	// interpret the genome
	private CGPSteppableInterpreter cgpInterpreter;

	/************************************************************************
	 ************************** BEHAVIOUR SETUP *****************************
	 ************************************************************************/

	/**
	 * Initializes all the ECJ {@link EvolutionState evolutionary states}. One
	 * corresponding to the agent behaviour, the second corresponding to the
	 * agent objective.
	 */
	public void setup(Agent agent, MimeticDesire model) {
		super.setup(agent, model);

		steps = 0;
		cgpInterpreter = new CGPSteppableInterpreter();

		setupEvoState(behaviourEvo, "behaviour_evo.params");
		setupEvoState(objectiveEvo, "objective_evo.params");
	}

	/**
	 * Sets up an {@link EvolutionState evolutionary state} based on a parameter
	 * file.
	 * 
	 * @param evoState
	 *            the evolutionary state to initialize
	 * @param filename
	 *            the parameter file
	 */
	private void setupEvoState(EvolutionState evoState, String filename) {
		File parameterFile = new File(Thread.currentThread()
				.getContextClassLoader().getResource(filename).getPath()
				.toString());

		ParameterDatabase dbase = null;
		try {
			dbase = new ParameterDatabase(parameterFile, new String[] {
					"-file", parameterFile.getCanonicalPath() });

		} catch (Exception e) {
			throw new RuntimeErrorException(
					new Error(
							"Couldn't load the configuration file for the evolutionary behaviour"));
		}

		Output out = Evolve.buildOutput();

		behaviourEvo = Evolve.initialize(dbase, 0, out);
		behaviourEvo.startFresh();
	}

	/**
	 * Used to clean up the evolutionary state after they are no longer needed,
	 * if this is not done, the parameter files remain open and restarting the
	 * simulator throws an exception.
	 */
	@Override
	public void finish() {
		Evolve.cleanup(behaviourEvo);
		Evolve.cleanup(objectiveEvo);
	}

	/************************************************************************
	 ************************* BEHAVIOUR UPDATE *****************************
	 ************************************************************************/

	private Agent mediator = null;
	private static final int DEFAULT_ENERGY = 100;
	private static final double ENERGY_UPDATE = 1;
	private double energy = DEFAULT_ENERGY;

	private CGPIndividual controller;
	private Object[] lastInputs;
	private Object[] lastOutputs;

	/**
	 * BEHAVIOUR UPDATE ROUTINE In the update an agent runs its current
	 * controller and records the results. On each evaluation period, the agents
	 * evolve the behaviors according to the current objective function. There's
	 * a second (larger) evaluation cycle that serves to evolve the objective
	 * function.
	 * 
	 * In the case of the competitive behaviour, on each behaviour evaluation
	 * cycle, we evolve the behaviour according to the current desire function.
	 * On each desire function evaluation cycle we either swap the current
	 * objective function, according to the agent performance or (when all the
	 * objective functions are evaluated) evolve the population of objectives.
	 */
	@Override
	public void update() {
		if (behaviourEvo == null || objectiveEvo == null) {
			throw new RuntimeException(
					"You can't execute a behaviour without calling setup first");
		} else {
			if (mediator == null)
				updateMediator();
			// int numControllers =
			// behaviourEvo.population.subpops[0].individuals.length;

			// evolves behaviour if all the controllers have been evaluated
			evolveBehaviour();

			// evolves objectives if all controllers have been evaluated
			// evolveObjectives();

			// switches controllers if out of energy
			updateController();

			// run current controller
			//runController();

			// update energy values based on the best objective function
			updateEnergy();

			steps++;
		}
	}

	/**
	 * This is now called by the evolve objectives method, we can alter this
	 * latter, by choosing when to evolve the objective functions. We can even
	 * evolve them concurrently with the behaviour evolution. By comparing the
	 * energy levels with the energy levels that would be expected if the energy
	 * was updated by specific goals such as fitness, competition or imitation.
	 */
	private void evolveObjectives() {
		ObjectiveEvolution problem = (ObjectiveEvolution) behaviourEvo.evaluator.p_problem;
		// usually it is not, the first time
		if (!problem.isSetup()) {
			problem.setup(agent, model, this);
		}
		// generate the next batch of controllers
		objectiveEvo.evolve();
	}

	/**
	 * Evolves the controller population if all the controllers have been
	 * evaluated, this is, if the controllers' energy level have reached <= 0.
	 * 
	 * Also our evolutionary problems have a setup method so that they receive
	 * both the model, the agent, and the current behaviour, they use this so
	 * that they can access the performance measures recorded during behaviour
	 * evolution.
	 * 
	 * resets the behaviourEvaluator count to 0.
	 */
	private void evolveBehaviour() {
		int numControllers = behaviourEvo.population.subpops[0].individuals.length;
		// we evaluated all the controllers so its time to evolve
		if (behavioursEvaluated == numControllers) {
			BehaviourEvolution problem = (BehaviourEvolution) behaviourEvo.evaluator.p_problem;
			// usually it is not, the first time
			if (!problem.isSetup()) {
				problem.setup(agent, model, this);
			}
			// generate the next batch of controllers
			behaviourEvo.evolve();
			behavioursEvaluated = 0;

			evolveObjectives();
		}
	}

	private int behavioursEvaluated = 0;
	private int currentController = 0;

	/**
	 * Increments number of controllers evaluated. This should be restored to 0
	 * when controllers are evolved.
	 */
	private void updateController() {
		if (steps == 0) {
			controller = (CGPIndividual) behaviourEvo.population.subpops[0].individuals[0];
		} else

		// controller is performing baddly, evaluate the next controller
		if (energy <= 0) {

			behavioursEvaluated++;
			int numControllers = behaviourEvo.population.subpops[0].individuals.length;
			currentController = (currentController + 1) % numControllers;

			// get next controller
			controller = (CGPIndividual) behaviourEvo.population.subpops[0].individuals[currentController];

			// restore energy
			energy = DEFAULT_ENERGY;
		}
	}

	/**
	 * Updates the energy value according to controller performance. This
	 * performance is not given by a fitness function like what happens in
	 * direct evolution but rather by objective function that is co-evolved with
	 * the controller.
	 */
	private void updateEnergy() {
		energy = ENERGY_UPDATE * evaluateController(controller);
	}

	/**
	 * Must return an evaluation of the current individual controlling the agent
	 * behaviour.
	 * 
	 * @param controller
	 *            the {@link CGPIndividual CGP program} being evaluated
	 * @return an evaluation value between 0 and 1.
	 */
	private double evaluateController(CGPIndividual controller) {
		Object[] inputs = ArrayUtils.addAll(lastInputs, lastOutputs);

		Individual[] inds = ((SimpleStatistics) (objectiveEvo.statistics))
				.getBestSoFar();
		CGPIndividual objectiveFn = (CGPIndividual) inds[0];

		cgpInterpreter.load(objectiveFn);
		while (!cgpInterpreter.finished()) {
			cgpInterpreter.step(inputs);
		}

		double output = (double) cgpInterpreter.getOutput()[0];

		return Utils.squash(output);
	}

	private void updateMediator() {
		Agent[] neighbours = model.getNeighobours(agent.index);
		mediator = neighbours[model.random.nextInt(neighbours.length)];
	}
}
