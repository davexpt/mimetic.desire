package mimetic.desire.behaviour.meta;

import java.io.File;

import javax.management.RuntimeErrorException;

import ec.EvolutionState;
import ec.Evolve;
import ec.Problem;
import ec.cgp.eval.CGPSteppableInterpreter;
import ec.util.Output;
import ec.util.ParameterDatabase;
import mimetic.desire.Agent;
import mimetic.desire.MimeticDesire;
import mimetic.desire.behaviour.AbstractBehaviour;

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
			

			steps++;
		}
	}

	private void updateMediator() {
		Agent[] neighbours = model.getNeighobours(agent.index);
		mediator = neighbours[model.random.nextInt(neighbours.length)];
	}
}
