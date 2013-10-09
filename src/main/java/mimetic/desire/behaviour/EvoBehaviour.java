package mimetic.desire.behaviour;

import java.io.File;

import javax.management.RuntimeErrorException;

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

public class EvoBehaviour implements Behaviour {

	public EvoBehaviour() {
		File parameterFile = new File(Thread.currentThread()
				.getContextClassLoader().getResource("cgp.params").getPath()
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

		// Output out = Evolve.buildOutput();
		//
		// EvolutionState evaluatedState = Evolve.initialize(dbase, 0, out);
		// evaluatedState.startFresh();
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

	@Override
	public void update(Agent agent, MimeticDesire model) {
		Double2D position = agent.getPosition();
		Double2D velocity = agent.getVelocity();
		// update agent position

		Double2D newVelocity = new Double2D(
				(model.random.nextDouble() * 2) - 1,
				(model.random.nextDouble() * 2) - 1);
		// set the new position on the model
		agent.setVelocity(newVelocity);
		agent.updatePosition();
	}
}
