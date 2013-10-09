package mimetic.desire;

import sim.engine.SimState;
import sim.engine.Steppable;

public class StoppingCondition implements Steppable {
	private static final long serialVersionUID = 1L;
	private double successThreshold;

	public StoppingCondition(double successThreshold) {
		this.successThreshold = successThreshold;
	}

	int prevSuccessCount = -1;

	@Override
	public void step(SimState state) {
		MimeticDesire model = (MimeticDesire) state;
		int successCount = 0;
		for (Agent a : model.agents) {
			if (Math.abs(a.getFitness() - 1000) <= successThreshold)
				successCount++;
		}
		if (successCount != prevSuccessCount) {
			prevSuccessCount = successCount;
			// stop the simulation when all the agents found a
			// Successful fitness
			// give the threshold
			if (successCount == model.numAgents)
				state.kill();
		}

	}

}
