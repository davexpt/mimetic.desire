package mimetic.desire.behaviour;

import sim.util.Double2D;
import sim.util.MutableDouble2D;

public class PSOBehaviour extends AbstractBehaviour {

	@Override
	public void update() {
		Double2D position = agent.getPosition();
		Double2D velocity = agent.getVelocity();

		double x = position.x;
		double y = position.y;

		MutableDouble2D nBestPos = new MutableDouble2D();
		model.getNeighborhoodBest(agent.index, nBestPos); // updates the
															// location of
		// nBestPos

		// calc new velocity
		// calc x component

		double inertia = velocity.x;
		double pDelta = agent.bestFitnessP.x - x;
		double nDelta = nBestPos.x - x;
		double gDelta = model.bestPosition.x - x;
		double pWeight = model.random.nextDouble() + 0.4;
		double nWeight = model.random.nextDouble() + 0.4;
		double gWeight = model.random.nextDouble() + 0.4;
		double vx = (0.9 * inertia + pWeight * pDelta + nWeight * nDelta + gWeight
				* gDelta)
				/ (1 + pWeight + nWeight + gWeight);

		// calc y component
		inertia = velocity.y;
		pDelta = agent.bestFitnessP.y - y;
		nDelta = nBestPos.y - y;
		gDelta = model.bestPosition.y - y;
		pWeight = Math.random() + 0.4;
		nWeight = Math.random() + 0.4;
		gWeight = Math.random() + 0.4;
		double vy = (0.8 * inertia + pWeight * pDelta + nWeight * nDelta + gWeight
				* gDelta)
				/ (1 + pWeight + nWeight + gWeight);

		vx *= model.velocityScalar;
		vy *= model.velocityScalar;

		// update velocity
		agent.setVelocity(new Double2D(vx, vy));
		agent.updatePosition();

	}

	@Override
	public void finish() {
		// NOTHING TO CLEAN

	}

}
