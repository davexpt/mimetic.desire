package mimetic.desire.behaviour.ecj.problems;

import mimetic.desire.Agent;
import mimetic.desire.MimeticDesire;
import mimetic.desire.behaviour.Behaviour;
import mimetic.desire.behaviour.FitnessBehaviour;
import ec.simple.SimpleProblemForm;

public interface EvoBehaviourProblem extends SimpleProblemForm {
	void setup(Agent agent, MimeticDesire model, Behaviour behaviour);
	boolean isSetup();
}
