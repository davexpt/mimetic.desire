package mimetic.desire.behaviour.ecg.problems;

import mimetic.desire.Agent;
import mimetic.desire.MimeticDesire;
import mimetic.desire.behaviour.FitnessBehaviour;
import ec.EvolutionState;
import ec.cgp.eval.CGPProblem;

/**
 * Stores an agent and its model with setup (which should be called before
 * {@link EvolutionState#startFresh()}) so that the agent behaviour can be
 * evaluated and evolved accordingly.
 * 
 * @author Davide Nunes
 * 
 */
public abstract class AbstractEvoBehaviourProblem extends CGPProblem implements
		EvoBehaviourProblem {
	private static final long serialVersionUID = 1L;

	// the agent to be updated
	protected Agent agent;
	protected MimeticDesire model;
	// was this problem setUp?
	protected boolean setUp;

	protected FitnessBehaviour behaviour;

	public AbstractEvoBehaviourProblem() {
		// nothing to do yet
		setUp = false;

	}

	@Override
	public void setup(Agent agent, MimeticDesire model,
			FitnessBehaviour evoBehaviour) {
		this.agent = agent;
		this.model = model;
		this.behaviour = evoBehaviour;
		setUp = true;
	}

	public boolean isSetup() {
		return setUp;
	}
}
