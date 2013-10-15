package mimetic.desire.behaviour;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.management.RuntimeErrorException;

import mimetic.desire.Agent;
import mimetic.desire.MimeticDesire;
import mimetic.desire.behaviour.ecj.problems.FitnessExploitation;

import org.apache.commons.math3.analysis.function.Sigmoid;
import org.apache.commons.math3.util.FastMath;

import sim.util.Double2D;
import ec.EvolutionState;
import ec.Evolve;
import ec.Individual;
import ec.cgp.eval.CGPSteppableInterpreter;
import ec.cgp.genome.CGPIndividual;
import ec.util.Output;
import ec.util.ParameterDatabase;

public class SocialFitnessBehaviour extends AbstractBehaviour {

	private final double[] constants = new double[] { 1.0, -1.0 };

	private EvolutionState fEvoState;

	// how many steps do we need to maturate a controller (evaluate properly)
	public int maturationSteps = 10;

	// same as maturation
	private static final int INITIAL_UTILITY = 10;
	private double utility = 30;
	private double utilityUpdateSpeed = 1.0;
	private double minimalProgress = 1.0;

	// the current controller program that is used to update the agent
	private CGPIndividual pController;
	private CGPSteppableInterpreter interpreter;

	private Map<Individual, ArrayList<Double>> fitnessRecords;
	private Map<Individual, ArrayList<Double>> fitnessProgressionRecords;

	public SocialFitnessBehaviour() {
		super();

	}

	// last fitness
	private double previousFitness = -1;
	private int numEvaluated = 0;
	private int currentController = 0;
	private double firstFitness = 0;
	private int steps = 0;

	Agent mediator = null;

	@Override
	public void update() {
		if (fEvoState == null)
			throw new RuntimeException(
					"you called update on the behaviour without initialising its evolutionary state");
		else {

			int numControllers = fEvoState.population.subpops[0].individuals.length;
			/*************************************************
			 * continue executing the controllers
			 *************************************************/
			// a controller has maturated, update utility
			if (steps > 0 && steps % (maturationSteps) == 0) {
				double progress = fitnessProgression(pController);

				// fEvoState.output
				// .message("controller evaluated, computing progression: "
				// + progress);

				// update utility
				this.utility = utility
						+ (FastMath.abs(progress) >= minimalProgress ? progress
								* utilityUpdateSpeed : -1 * utilityUpdateSpeed);

				// fEvoState.output.message("current utility: " + utility);

				// current utility reached 0, switch controller
				if (utility <= 0) {
					numEvaluated++;
					currentController = (currentController + 1)
							% numControllers;
					utility = INITIAL_UTILITY;

					mediator = getRandomNeighbour();
				}
			}
			/*************************************************
			 * all controllers maturated, evolve
			 *************************************************/

			if (numEvaluated == numControllers) {
				FitnessExploitation problem = (FitnessExploitation) fEvoState.evaluator.p_problem;
				// usually it is not, the first time
				if (!problem.isSetup()) {
					problem.setup(agent, model, this);
				}
				// generate the next batch of controllers
				fEvoState.evolve();

				// lets erase the fitness records and record new ones for the
				// new
				// generation of controllers
				resetFitnessRecords();

				numEvaluated = 0;
			}

			// fEvoState.output.message("evaluating individual: "
			// + currentController);

			pController = (CGPIndividual) fEvoState.population.subpops[0].individuals[currentController];

			// interpret the result and update the velocity of the agent
			interpreter.load(pController);

			// prepare inputs for cgp controller program(x)(y)(dx)(dy)(f) |
			// (f-1) ----num inputs 8 ---- num outputs 2
			Double2D position = agent.getPosition();
			Double2D velocity = agent.getVelocity();

			double currentFitness = agent.getFitness();

			// in the beginning we dont have historic data about fitness
			if (steps == 0) {
				previousFitness = agent.getFitness();
				// we don't have a mediator get one
				mediator = getRandomNeighbour();
			}

			double scaledX = scale(position.x, model.space.width * -0.5,
					model.space.width * 0.5);
			double scaledY = scale(position.y, model.space.height * -0.5,
					model.space.height * 0.5);

			Double2D mediatorP = mediator.getPosition();
			double mediatorFitness = mediator.getFitness();

			double mediatorSX = scale(mediatorP.x, model.space.width * -0.5,
					model.space.width * 0.5);
			double mediatorSY = scale(mediatorP.y, model.space.height * -0.5,
					model.space.height * 0.5);

			// Object[] inputs = new Object[] { scaledX, velocity.x, scaledY,
			// velocity.y, scaleFitness(currentFitness),
			// scaleFitness(previousFitness), constants[0], constants[1] };

			Object[] inputs = new Object[] { scaledX, velocity.x, scaledY,
					velocity.y, scaleFitness(currentFitness), mediatorSX,
					mediatorSY, scaleFitness(mediatorFitness), constants[0],
					constants[1] };

			// update last fitness to current fitness
			previousFitness = currentFitness;

			// run the controller program
			while (!interpreter.finished()) {
				interpreter.step(inputs);
			}
			Object[] outputs = interpreter.getOutput();

			// apply sigmoid to outputs to restrict velocity from -1 to 1
			Sigmoid sig = new Sigmoid(-1.0, 1.0);

			double dx = sig.value((double) outputs[0]);
			double dy = sig.value((double) outputs[1]);

			Double2D newVelocity = new Double2D(dx, dy);
			// set the new position on the model
			agent.setVelocity(newVelocity);
			agent.updatePosition();

			// fEvoState.output.message("executed controller: "
			// + interpreter.getExpression());

			recordFitness();
			steps++;

		}

	}

	private double fitnessProgression(CGPIndividual controller) {
		ArrayList<Double> fitnessR = fitnessRecords.get(controller);
		if (!fitnessProgressionRecords.containsKey(controller)) {
			fitnessProgressionRecords.put(controller, new ArrayList<Double>());
		}
		ArrayList<Double> fitnessProgR = fitnessProgressionRecords
				.get(controller);

		double avgFitness = 0.0;

		for (int i = 0; i < maturationSteps; i++) {
			int index = fitnessR.size() - 1 - i;
			avgFitness += fitnessR.get(index);
		}
		avgFitness /= maturationSteps;

		double avgPrevFitness = 0.0;
		// first time this is called
		if (fitnessR.size() <= maturationSteps) {
			avgPrevFitness = this.firstFitness;
		} else {
			for (int i = 0; i < maturationSteps; i++) {
				int index = fitnessR.size() - 1 - maturationSteps - i;
				avgPrevFitness += fitnessR.get(index);
			}
			avgPrevFitness /= maturationSteps;
		}

		double fitnessProgression = 0;
		if (avgPrevFitness == 0)
			return avgFitness;

		fitnessProgression = (avgFitness - avgPrevFitness)
				/ FastMath.abs(avgPrevFitness);

		fitnessProgR.add(fitnessProgression);
		return fitnessProgression;
	}

	/**
	 * This should be called before updating an agent velocity (which updates
	 * its position) so that we record its current fitness and relate it with
	 * the individual controller program behing evaluated
	 */
	private void recordFitness() {
		if (fitnessRecords == null) {
			throw new RuntimeException(
					"Fitness Records were not initialised for this behaviour, we cant record");
		} else {

			if (!fitnessRecords.containsKey(pController)) {
				fitnessRecords.put(pController, new ArrayList<Double>());
			}
			ArrayList<Double> fitnessRecord = fitnessRecords.get(pController);
			double currentFitness = agent.getFitness();

			fitnessRecord.add(Math.pow((currentFitness - previousFitness), 2));
		}
	}

	// TODO move this initialization to an abstract class for Evolutionary
	// Behaviours
	// apparently any evolutionary behaviour should contain an Evolutionary
	// State and the
	// respective problem that reads an agent and a model and evaluates the
	// behaviour
	@Override
	public void setup(Agent agent, MimeticDesire model) {
		super.setup(agent, model);

		File parameterFile = new File(Thread.currentThread()
				.getContextClassLoader().getResource("social_fitness.params")
				.getPath().toString());

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

		fEvoState = Evolve.initialize(dbase, 0, out);
		interpreter = new CGPSteppableInterpreter();

		resetFitnessRecords();
		firstFitness = agent.getFitness();
		fEvoState.startFresh();

		this.maturationSteps = agent.evaluationPeriod;

		// int result = EvolutionState.R_NOTDONE;
		//
		// while (result == EvolutionState.R_NOTDONE) {
		// result = evaluatedState.evolve();
		// }
		//
		// Individual[] inds = ((SimpleStatistics) (evaluatedState.statistics))
		// .getBestSoFar();
		//
		// CGPSteppableInterpreter interpreter = new CGPSteppableInterpreter();
		// interpreter.load((CGPIndividual) inds[0]);
		// evaluatedState.output.message(interpreter.getExpression());
	}

	private void resetFitnessRecords() {
		fitnessRecords = new HashMap<Individual, ArrayList<Double>>();
		fitnessProgressionRecords = new HashMap<Individual, ArrayList<Double>>();

	}

	public ArrayList<Double> getFitnessRecord(CGPIndividual ind) {
		return fitnessRecords.get(ind);
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
			result = scale(fitness, minFitness, maxFitness);
		}
		return result;
	}

	private double scale(double value, double min, double max) {
		if (min == max)
			return 0;

		double result = 2 * ((value - min) / (max - min)) - 1;
		return result;
	}

	public ArrayList<Double> getFitnessProgressionRecords(CGPIndividual ind) {
		return fitnessProgressionRecords.get(ind);
	}

	@Override
	public void finish() {
		Evolve.cleanup(fEvoState);
	}

	private Agent getRandomNeighbour() {
		Agent[] neighbours = model.getNeighobours(agent.index);
		return neighbours[model.random.nextInt(neighbours.length)];
	}

}
