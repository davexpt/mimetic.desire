package mimetic.desire.behaviour;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.management.RuntimeErrorException;

import com.sun.xml.internal.ws.policy.spi.PolicyAssertionValidator.Fitness;

import sim.util.Double2D;
import ec.EvolutionState;
import ec.Evolve;
import ec.Individual;
import ec.cgp.eval.CGPSteppableInterpreter;
import ec.cgp.genome.CGPIndividual;
import ec.simple.SimpleStatistics;
import ec.util.Output;
import ec.util.ParameterDatabase;
import mimetic.desire.Agent;
import mimetic.desire.MimeticDesire;
import mimetic.desire.behaviour.ecg.problems.EvoBehaviourProblem;
import mimetic.desire.behaviour.ecg.problems.FitnessExploitation;

public class EvoBehaviour extends AbstractBehaviour {

	private final double[] constants = new double[] { 1.0, -1.0 };

	private EvolutionState fEvoState;

	// how many steps do we need to maturate a controller
	private int maturationSteps = 1;

	// the current controller program that is used to update the agent
	private CGPIndividual pController;
	private CGPSteppableInterpreter interpreter;

	private Map<Individual, ArrayList<Double>> fitnessRecords;

	public EvoBehaviour() {
		super();

	}

	// last fitness
	private double previousFitness = 0;
	private int numMaturated = 0;
	private int currentController = 0;

	@Override
	public void update() {
		if (fEvoState == null)
			throw new RuntimeException(
					"you called update on the behaviour without initialising its evolutionary state");
		else {

			previousFitness = agent.getFitness();
			int numControllers = fEvoState.population.subpops[0].individuals.length;
			/*************************************************
			 * continue executing the controllers
			 *************************************************/
			// a controller has maturated, switch to the next one
			if (agent.steps > 0 && agent.steps % (maturationSteps) == 0) {
				numMaturated++;
				currentController = (currentController + 1) % numControllers;
			}
			/*************************************************
			 * all controllers maturated, evolve
			 *************************************************/

			if (numMaturated == numControllers) {
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

				numMaturated = 0;
			}

			fEvoState.output.message("evaluating individual: "
					+ currentController);

			pController = (CGPIndividual) fEvoState.population.subpops[0].individuals[currentController];

			// interpret the result and update the velocity of the agent
			interpreter.load(pController);

			// prepare inputs for cgp controller program(x)(y)(dx)(dy)(f) |
			// (f-1) ----num inputs 8 ---- num outputs 2
			Double2D position = agent.getPosition();
			Double2D velocity = agent.getVelocity();
			double currentFitness = previousFitness;

			Object[] inputs = new Object[] { position.x, velocity.x,
					position.y, velocity.y, currentFitness, previousFitness,
					constants[0], constants[1] };

			// update last fitness to current fitness
			previousFitness = currentFitness;

			// run the controller program
			while (!interpreter.finished()) {
				interpreter.step(inputs);
			}
			Object[] outputs = interpreter.getOutput();
			double dx = (double) outputs[0];
			double dy = (double) outputs[1];
			Double2D newVelocity = new Double2D(dx, dy);
			// set the new position on the model
			agent.setVelocity(newVelocity);
			agent.updatePosition();

			fEvoState.output.message("executed controller: "
					+ interpreter.getExpression());

			recordFitness();

		}

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
				.getContextClassLoader()
				.getResource("fitness_exploitation.params").getPath()
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

		fEvoState = Evolve.initialize(dbase, 0, out);
		interpreter = new CGPSteppableInterpreter();

		resetFitnessRecords();

		fEvoState.startFresh();

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

	}

	public ArrayList<Double> getFitnessRecord(CGPIndividual ind) {
		return fitnessRecords.get(ind);
	}
}
