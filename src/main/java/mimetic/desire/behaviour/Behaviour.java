package mimetic.desire.behaviour;

import mimetic.desire.Agent;
import mimetic.desire.MimeticDesire;

public interface Behaviour {
	void update();

	void setup(Agent agent, MimeticDesire model);
}
