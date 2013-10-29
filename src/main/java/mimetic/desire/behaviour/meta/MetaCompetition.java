package mimetic.desire.behaviour.meta;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.management.RuntimeErrorException;

import mimetic.desire.Agent;
import mimetic.desire.MimeticDesire;
import mimetic.desire.behaviour.AbstractBehaviour;
import mimetic.desire.behaviour.ecj.problems.meta.BehaviourEvolution;
import mimetic.desire.behaviour.ecj.problems.meta.ObjectiveEvolution;
import mimetic.desire.util.Utils;

import org.apache.commons.collections.keyvalue.MultiKey;
import org.apache.commons.collections.map.MultiKeyMap;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import sim.util.Double2D;
import ec.EvolutionState;
import ec.Evolve;
import ec.Individual;
import ec.Problem;
import ec.cgp.eval.CGPSteppableInterpreter;
import ec.cgp.genome.CGPIndividual;
import ec.simple.SimpleStatistics;
import ec.util.Output;
import ec.util.ParameterDatabase;

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

		behaviourEvo = setupEvoState("behaviour_evo.params");
		objectiveEvo = setupEvoState("objective_evo.params");

		// energy samples is a multi map that stores the samples for the various
		// predictors
		resetEnergySamples();

		// first run uses random predictor
		currentObjectiveFn = (CGPIndividual) objectiveEvo.population.subpops[0].individuals[model.random
				.nextInt(objectiveEvo.population.subpops[0].individuals.length)];

		// we need to keep track of the energy values for all the objective
		// functions
		resetEnergy();

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
	private EvolutionState setupEvoState(String filename) {
		EvolutionState evoState;

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

		evoState = Evolve.initialize(dbase, 0, out);
		evoState.startFresh();

		return evoState;
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
	// private double energy = DEFAULT_ENERGY;

	private Map<CGPIndividual, Double> energy = null;

	// energy value that would be considered if the objective was to compete for
	// actual fitness
	private double competitionEnergy;
	private Map<CGPIndividual, ArrayList<Double>> competitionEnergySamples;
	private Map<CGPIndividual, DescriptiveStatistics> compEnergyStats;

	private CGPIndividual controller;
	private Object[] lastInputs;
	// private Object[] lastOutputs;

	// behaviour progress controll
	private int behavioursEvaluated = 0;
	private int currentController = 0;
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
			int numControllers = behaviourEvo.population.subpops[0].individuals.length;
			// we evaluated all the controllers so its time to evolve
			if (behavioursEvaluated == numControllers) {
				evolveBehaviour();

				// evolves objectives if all controllers have been evaluated
				evolveObjectives();

				resetEnergySamples();
			}

			// switches controllers if out of energy
			updateController();

			// run current controller
			runController();

			// update energy values based on the best objective function
			updateEnergy();

			// samples the energy values based on the sampling interval
			sampleEnergy();

			steps++;

		}
	}

	private void resetEnergySamples() {
		energySamples = new MultiKeyMap();
		coEvoEnergyStats = new MultiKeyMap();

		competitionEnergySamples = new HashMap<>();
		compEnergyStats = new HashMap<>();
	}

	private void resetEnergy() {
		energy = new HashMap<CGPIndividual, Double>();
		Individual[] objectives = objectiveEvo.population.subpops[0].individuals;
		for (Individual ind : objectives) {
			CGPIndividual objective = (CGPIndividual) ind;
			energy.put(objective, new Double(DEFAULT_ENERGY));
		}

		competitionEnergy = DEFAULT_ENERGY;

	}

	private static final double constants[] = new double[] { 0.5, -0.5 };

	private void runController() {
		// interpret the result and update the velocity of the agent
		cgpInterpreter.load(controller);

		// prepare inputs for cgp controller program(x)(y)(dx)(dy)(f) |
		// (f-1) ----num inputs 8 ---- num outputs 2
		Double2D position = agent.getPosition();
		Double2D velocity = agent.getVelocity();

		double currentFitness = agent.getFitness();

		// in the beginning we dont have historic data about fitness
		// if (steps == 0) {
		// //previousFitness = agent.getFitness();
		// }

		Double2D bestLocalFitness = agent.getBestFitnessCoordinates();

		double scaledX = Utils.scale(position.x, model.space.width * -0.5,
				model.space.width * 0.5);
		double scaledY = Utils.scale(position.y, model.space.height * -0.5,
				model.space.height * 0.5);

		// double bestLocalX = Utils.scale(bestLocalFitness.x, model.space.width
		// * -0.5, model.space.width * 0.5);
		// double bestLocalY = Utils.scale(bestLocalFitness.y,
		// model.space.height
		// * -0.5, model.space.height * 0.5);

		// Object[] inputs = new Object[] { scaledX, velocity.x, scaledY,
		// velocity.y, scaleFitness(currentFitness),
		// scaleFitness(previousFitness), constants[0], constants[1] };

		Object[] inputs = new Object[] { scaledX, velocity.x, scaledY,
				velocity.y, constants[0], constants[1] };

		// this is used as the inputs for the objective function
		lastInputs = ArrayUtils.clone(inputs);

		// update last fitness to current fitness
		// previousFitness = currentFitness;

		// run the controller program
		while (!cgpInterpreter.finished()) {
			cgpInterpreter.step(inputs);
		}
		Object[] outputs = cgpInterpreter.getOutput();

		lastOutputs = ArrayUtils.clone(outputs);
		// apply sigmoid to outputs to restrict velocity from -1 to 1

		double dx = Utils.squash((double) outputs[0]);
		double dy = Utils.squash((double) outputs[1]);

		Double2D newVelocity = new Double2D(dx, dy);
		// set the new position on the model
		agent.setVelocity(newVelocity);
		agent.updatePosition();

	}

	// energy sampling for the multiple objective functions and the competition
	// function
	public static final int ENERGY_SAMPLING_INTERVAL = 3;
	private MultiKeyMap energySamples;
	private MultiKeyMap coEvoEnergyStats;

	private void sampleEnergy() {

		if (steps % ENERGY_SAMPLING_INTERVAL == 0) {
			Individual[] objectives = objectiveEvo.population.subpops[0].individuals;

			for (Individual objective : objectives) {
				MultiKey key = new MultiKey(controller, objective);
				if (!energySamples.containsKey(key)) {
					energySamples.put(key, new ArrayList<Double>());

					coEvoEnergyStats.put(key, new DescriptiveStatistics());
				}
				@SuppressWarnings("unchecked")
				ArrayList<Double> controllerSamples = (ArrayList<Double>) energySamples
						.get(key);

				DescriptiveStatistics energyStats = (DescriptiveStatistics) coEvoEnergyStats
						.get(key);

				controllerSamples.add(energy.get(objective));
				energyStats.addValue(energy.get(objective));
			}

			// sample energy based on competition
			if (!competitionEnergySamples.containsKey(controller)) {
				competitionEnergySamples.put(controller,
						new ArrayList<Double>());

				compEnergyStats.put(controller, new DescriptiveStatistics());
			}
			ArrayList<Double> competitionSamples = (ArrayList<Double>) competitionEnergySamples
					.get(controller);

			DescriptiveStatistics copmStats = compEnergyStats.get(controller);

			competitionSamples.add(competitionEnergy);
			copmStats.addValue(competitionEnergy);

			// TODO samples for actual fitness
			// TODO samples for imitation
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
		ObjectiveEvolution problem = (ObjectiveEvolution) objectiveEvo.evaluator.p_problem;
		// usually it is not, the first time
		if (!problem.isSetup()) {
			problem.setup(agent, model, this);
		}
		// TODO do not evolve yet
		// generate the next batch of controllers
		// objectiveEvo.evolve();
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

	/**
	 * Increments number of controllers evaluated. This should be restored to 0
	 * when controllers are evolved.
	 */
	private void updateController() {
		if (steps == 0) {
			controller = (CGPIndividual) behaviourEvo.population.subpops[0].individuals[0];
		} else {
			// the first value for the energy associated with a objective
			// function is the default

			// controller is performing baddly, evaluate the next controller
			if (energy.get(getObjectiveFunction()) <= 0) {

				behavioursEvaluated++;
				int numControllers = behaviourEvo.population.subpops[0].individuals.length;
				currentController = (currentController + 1) % numControllers;

				// get next controller
				controller = (CGPIndividual) behaviourEvo.population.subpops[0].individuals[currentController];

				// restore energy
				resetEnergy();
			}
		}
	}

	/**
	 * Updates the energy value according to controller performance. This
	 * performance is not given by a fitness function like what happens in
	 * direct evolution but rather by objective function that is co-evolved with
	 * the controller.
	 */
	private void updateEnergy() {

		Map<CGPIndividual, Double> evaluations = evaluateController(controller);

		Individual[] objectives = objectiveEvo.population.subpops[0].individuals;
		// update the energy values for all the predictors
		// the only energy used is the one for the best predictor but we need
		// the other values to evolve the predictors
		for (Individual objective : objectives) {
			CGPIndividual obj = (CGPIndividual) objective;

			double energyValue = energy.get(obj);

			energy.put(obj, energyValue + ENERGY_UPDATE * evaluations.get(obj));
		}

		// evaluate competition
		competitionEnergy = ENERGY_UPDATE * evaluateCompetition();

	}

	// evaluate competition based on scaled fitness difference
	private double evaluateCompetition() {

		double fitness = agent.getFitness();
		double fitnessMediator = mediator.getBestFitness();

		double fitnessDiff = (scaleFitness(fitness) - scaleFitness(fitnessMediator));

		return fitnessDiff;
	}

	// min / max fitenss
	private double minFitness = 0.0;
	private double maxFitness = 0.0;

	private double scaleFitness(double fitness) {
		double result = 0;
		if (steps == 0) {
			minFitness = fitness;
			maxFitness = fitness;
		} else {

			// found a new max
			if (fitness > maxFitness) {
				maxFitness = fitness;
			} else if (fitness < minFitness) {
				minFitness = fitness;
			}

			// rescale
			result = Utils.scale(fitness, minFitness, maxFitness);
		}
		return result;
	}

	/**
	 * Must return an evaluation of the current individual controlling the agent
	 * behaviour.
	 * 
	 * @param controller
	 *            the {@link CGPIndividual CGP program} being evaluated
	 * @return an evaluation value between 0 and 1.
	 */
	private Map<CGPIndividual, Double> evaluateController(
			CGPIndividual controller) {
		Map<CGPIndividual, Double> evaluations = new HashMap<CGPIndividual, Double>();

		Object[] inputs = ArrayUtils.addAll(lastInputs, lastOutputs);

		Individual[] objectives = objectiveEvo.population.subpops[0].individuals;
		for (Individual objective : objectives) {
			CGPIndividual obj = (CGPIndividual) objective;

			cgpInterpreter.load(obj);
			while (!cgpInterpreter.finished()) {
				cgpInterpreter.step(inputs);
			}
			double output = (double) cgpInterpreter.getOutput()[0];
			evaluations.put(obj, Utils.squash(output));

		}
		return evaluations;
	}

	private void updateMediator() {
		Agent[] neighbours = model.getNeighobours(agent.index);
		mediator = neighbours[model.random.nextInt(neighbours.length)];
	}

	CGPIndividual currentObjectiveFn;

	/**
	 * Returns the current objective function which is the best of the current
	 * predictors
	 * 
	 * @return
	 */
	public CGPIndividual getObjectiveFunction() {
		// the first run uses a random predictor
		if (objectiveEvo.generation > 0) {
			// the next runs have statistics to pick best objective function
			Individual[] inds = ((SimpleStatistics) (objectiveEvo.statistics))
					.getBestSoFar();
			currentObjectiveFn = (CGPIndividual) inds[0];
		}
		return currentObjectiveFn;
	}

	public double getCompetitionEnergy() {
		return competitionEnergy;
	}

	@SuppressWarnings("unchecked")
	public ArrayList<Double> getEnergySamples(CGPIndividual controller,
			CGPIndividual objective) {
		return (ArrayList<Double>) energySamples.get(new MultiKey(controller,
				objective));
	}

	public ArrayList<Double> getCompetitionEnergySamples(
			CGPIndividual controller) {
		return competitionEnergySamples.get(controller);
	}

	public Individual[] getControllers() {
		return behaviourEvo.population.subpops[0].individuals;
	}

	public Individual[] getObjectives() {
		return objectiveEvo.population.subpops[0].individuals;
	}

	//return descriptive stats objects
	
	public DescriptiveStatistics getEnergyStats(CGPIndividual controller,
			CGPIndividual objective) {
		return (DescriptiveStatistics) coEvoEnergyStats.get(new MultiKey(
				controller, objective));
	}

	public DescriptiveStatistics getCompetitionEnergyStats(
			CGPIndividual controller) {
		return compEnergyStats.get(controller);
	}
}
