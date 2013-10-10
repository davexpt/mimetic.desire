package mimetic.desire.behaviour;

import mimetic.desire.Agent;
import mimetic.desire.MimeticDesire;

public abstract class AbstractBehaviour implements Behaviour {
	Agent agent;
	MimeticDesire model;

	@Override
	public void setup(Agent agent, MimeticDesire model) {
		if (agent == null || model == null)
			throw new RuntimeException(
					"You have to supply a proper agent and model in order to setup your behaviour");
		this.agent = agent;
		this.model = model;
	}
}
