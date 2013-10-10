package mimetic.desire.behaviour.ecg.problems;

import mimetic.desire.Agent;
import mimetic.desire.MimeticDesire;
import mimetic.desire.behaviour.EvoBehaviour;
import ec.simple.SimpleProblemForm;

public interface EvoBehaviourProblem extends SimpleProblemForm {
	void setup(Agent agent, MimeticDesire model, EvoBehaviour behaviour);
	boolean isSetup();
}
