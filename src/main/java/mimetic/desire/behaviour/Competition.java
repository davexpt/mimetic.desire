package mimetic.desire.behaviour;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.management.RuntimeErrorException;

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
import mimetic.desire.Agent;
import mimetic.desire.MimeticDesire;
import mimetic.desire.behaviour.ecj.problems.MimeticEvaluation;

//TODO this is a rough copy of fitenss behaviour
//the fitness is how well we are imitation a neighbour rather than the fitness landscape
public class Competition extends AbstractBehaviour {

	private final double[] constants = new double[] { 0.5, -0.5 };

	private EvolutionState fEvoState;

	// how many steps do we need to maturate a controller (evaluate properly)
	public int maturationSteps = 10;

	// same as maturation
	private static final int INITIAL_UTILITY = 10;
	private double utility = 30;
	private double utilityUpdateSpeed = 1.0;

	private Agent mediator = null;

	// the current controller program that is used to update the agent
	private CGPIndividual pController;
	private CGPSteppableInterpreter interpreter;

	private Map<Individual, ArrayList<Double>> imitationErrors;
	private Map<Individual, ArrayList<Double>> fitnessProgressionRecords;

	public Competition() {
		super();

	}

	// last fitness
	private double previousFitness = -1;
	private int numEvaluated = 0;
	private int currentController = 0;
	private double firstFitness = 0;

	private int steps;

	private double competitionThreshold = 0.1;

	@Override
	public void update() {
		if (fEvoState == null)
			throw new RuntimeException(
					"you called update on the behaviour without initialising its evolutionary state");
		else {
			// select random mediator
			if (mediator == null) {
				mediator = model.getBestNeighbour(agent.index);
			}
			int numControllers = fEvoState.population.subpops[0].individuals.length;
			/*************************************************
			 * continue executing the controllers
			 *************************************************/
			// a controller has maturated, update utility
			if (steps > 0 && steps % (maturationSteps) == 0) {
				double competitionResult = currentCompetitionResult(pController);

				
				// update utility
				utility += competitionResult > 0 - competitionThreshold ? -0.01 * utilityUpdateSpeed
						: -1 * utilityUpdateSpeed;

				// current utility reached 0, switch controller
				if (utility <= 0) {
					numEvaluated++;
					currentController = (currentController + 1)
							% numControllers;
					utility = INITIAL_UTILITY;

					// switch mediator, need a better mediator, or himself
					mediator = model.getBestNeighbour(agent.index);
				}
			}
			/*************************************************
			 * all controllers maturated, evolve
			 *************************************************/

			if (numEvaluated == numControllers) {
				MimeticEvaluation problem = (MimeticEvaluation) fEvoState.evaluator.p_problem;
				// usually it is not, the first time
				if (!problem.isSetup()) {
					problem.setup(agent, model, this);
				}
				// generate the next batch of controllers
				fEvoState.evolve();

				// lets erase the fitness records and record new ones for the
				// new
				// generation of controllers
				resetImitationErrors();

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
			}

			Double2D bestLocalFitness = agent.getBestFitnessCoordinates();

			double scaledX = scale(position.x, model.space.width * -0.5,
					model.space.width * 0.5);
			double scaledY = scale(position.y, model.space.height * -0.5,
					model.space.height * 0.5);

			double bestLocalX = scale(bestLocalFitness.x, model.space.width
					* -0.5, model.space.width * 0.5);
			double bestLocalY = scale(bestLocalFitness.y, model.space.height
					* -0.5, model.space.height * 0.5);

			// Object[] inputs = new Object[] { scaledX, velocity.x, scaledY,
			// velocity.y, scaleFitness(currentFitness),
			// scaleFitness(previousFitness), constants[0], constants[1] };

			Object[] inputs = new Object[] { scaledX, velocity.x, scaledY,
					velocity.y, bestLocalX, bestLocalY, constants[0],
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

			recordCompetitionStatus();
			steps++;

		}

	}

	private Agent getRandomNeighbour() {
		Agent[] neighbours = model.getNeighobours(agent.index);
		return neighbours[model.random.nextInt(neighbours.length)];
	}

	public double currentCompetitionResult(CGPIndividual controller) {
		ArrayList<Double> competitionStats = imitationErrors.get(controller);

		double imitationError = 0.0;

		for (Double fError : competitionStats) {
			imitationError += fError;
		}
		// observation average
		return imitationError /= competitionStats.size();
	}

	/**
	 * This should be called before updating an agent velocity (which updates
	 * its position) so that we record its current fitness and relate it with
	 * the individual controller program behing evaluated
	 */
	private void recordCompetitionStatus() {
		if (imitationErrors == null) {
			throw new RuntimeException(
					"Fitness Records were not initialised for this behaviour, we cant record");
		} else {

			if (!imitationErrors.containsKey(pController)) {
				imitationErrors.put(pController, new ArrayList<Double>());
			}
			ArrayList<Double> errorRecord = imitationErrors.get(pController);

			double fitness = agent.getFitness();
			double fitnessMediator = mediator.getBestFitness();

			double fitnessDiff = (scaleFitness(fitness) - scaleFitness(fitnessMediator));

			errorRecord.add(fitnessDiff);
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
				.getContextClassLoader().getResource("competition.params")
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
		this.steps = 0;

		Output out = Evolve.buildOutput();

		fEvoState = Evolve.initialize(dbase, 0, out);
		interpreter = new CGPSteppableInterpreter();

		resetImitationErrors();
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

	private void resetImitationErrors() {
		imitationErrors = new HashMap<Individual, ArrayList<Double>>();
		fitnessProgressionRecords = new HashMap<Individual, ArrayList<Double>>();

	}

	public ArrayList<Double> getImitationErrors(CGPIndividual ind) {
		return imitationErrors.get(ind);
	}

	// min / max fitenss
	private static final double MIN_ERROR = -4.0;
	private static final double MAX_ERROR = 4.0;

	public double scaleError(double error) {

		return scale(error, MIN_ERROR, MAX_ERROR);

	}

	// this should be located in an Util class or something
	private double scale(double value, double min, double max) {
		if (min == max)
			return 0;

		double result = 2 * ((value - min) / (max - min)) - 1;
		return result;
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

	public ArrayList<Double> getFitnessProgressionRecords(CGPIndividual ind) {
		return fitnessProgressionRecords.get(ind);
	}

	@Override
	public void finish() {
		Evolve.cleanup(fEvoState);
	}

}
