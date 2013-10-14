package mimetic.desire;

import sim.app.pso.Booth;
import sim.app.pso.Evaluatable;
import sim.app.pso.Griewangk;
import sim.app.pso.Particle;
import sim.app.pso.Rastrigin;
import sim.app.pso.Rosenbrock;
import sim.engine.Schedule;
import sim.engine.SimState;
import sim.field.continuous.Continuous2D;
import sim.util.Double2D;
import sim.util.MutableDouble2D;

/**
 * Model of mimetic desire in which we try to test out the implications of
 * generating behaviour based on an evolutionary mimetic mechanism.
 * 
 * Agents have a set of objective functions they adopt for different
 * motivations. These functions shape their behaviour. The agents try to copy
 * the motivations of neighbours and generate behaviour that resembles their
 * peers. The adaptation to the objective functions of beighbours leads the
 * agents to create different behaviours that ultimate lead to the same purpose.
 * 
 * Consider a problem in which we want to follow a gradient of fitness. The
 * first motivation is supplied by the problem itself. Creating an objective
 * function that leads the behaviour to follow a gitness gradient over time.
 * Each agent should develop different objective functions for this purpose as
 * they evolve in distinct locations in their environment and have a set of
 * unique interactions both with the environment and with their peers.
 * 
 * The imitation mechanism would lead agents to morph their objective functions
 * from their current one to their neighbours. Their function is not evaluated
 * on how well the behaviour follows the fitness landscape this time, but rather
 * how well their objective functions is close to the one of their neighbour.
 * This will eventually lead to the exploration of the differences between
 * multiple gradiet discend functions in a continuous way.
 * 
 * @author Davide Nunes
 * 
 */
public class MimeticDesire extends SimState {
	private static final long serialVersionUID = 1L;

	public Continuous2D space;
	public double width = 20.24;
	public double height = 20.24;

	public Agent[] agents;

	// parameters
	public int numAgents = 100;

	public int getNumAgents() {
		return numAgents;
	}

	public void setNumAgents(int val) {
		if (val >= 0)
			numAgents = val;
	}

	public int neighborhoodSize = 10;

	public int getNeighborhoodSize() {
		return neighborhoodSize;
	}

	public void setNeighborhoodSize(int val) {
		if ((val >= 0) && (val <= numAgents))
			neighborhoodSize = val;
	}

	public double initialVelocityRange = 1.0;

	public double velocityScalar = 0.5;

	public double getVelocityScalar() {
		return velocityScalar;
	}

	public void setVelocityScalar(double val) {
		if (val >= 0.0)
			velocityScalar = val;
	}

	public int fitnessFunction = 0;

	public int getFitnessFunction() {
		return fitnessFunction;
	}

	public void setFitnessFunction(int val) {
		fitnessFunction = val;
	}

	public Object domFitnessFunction() {
		return new String[] { "Booth", "Rastrigin", "Griewangk", "Rosenbrock" };
	}

	private Evaluatable mapFitnessFunction(int val) {
		switch (val) {
		case 0:
			return new Booth();
		case 1:
			return new Rastrigin();
		case 2:
			return new Griewangk();
		case 3:
			return new Rosenbrock();
		}

		return new Booth();
	}

	public double[] fitnessFunctionLowerBound = { 920, 950, 998, 200 };

	public double successThreshold = 1.0e-8;

	public double getSuccessThreshold() {
		return successThreshold;
	}

	public void setSuccessThreshold(double val) {
		if (val >= 0.0)
			successThreshold = val;
	}

	// constructor
	public MimeticDesire(long seed) {
		super(seed);
	}

	/**
	 * Return neighbourhood vector for the current agent, this neighbourhood is
	 * based on the current neighbourhood distance but not on the topological
	 * distance. but rather a regular neighbourhood established when the model
	 * is first set up.
	 */
	public Agent[] getNeighobours(int agentIndex, MutableDouble2D pos) {

		Agent[] neighbours = new Agent[neighborhoodSize];

		int start = (agentIndex - neighborhoodSize / 2);
		if (start < 0)
			start += numAgents;

		for (int i = 0; i < neighborhoodSize; i++) {
			neighbours[i] = agents[(start + i) % numAgents];

		}
		return neighbours;
	}

	int prevSuccessCount = -1;

	public double bestVal;

	public MutableDouble2D bestPosition = new MutableDouble2D();

	public void start() {
		// reset the global best
		// bestVal = 0;

		super.start();

		if (agents != null)
			cleanAgents();
		agents = new Agent[numAgents];
		space = new Continuous2D(height, width, height);

		Evaluatable f = mapFitnessFunction(fitnessFunction);

		for (int i = 0; i < numAgents; i++) {
			double x = (random.nextDouble() * width) - (width * 0.5);
			double y = (random.nextDouble() * height) - (height * 0.5);
			double vx = (random.nextDouble() * initialVelocityRange)
					- (initialVelocityRange * 0.5);
			double vy = (random.nextDouble() * initialVelocityRange)
					- (initialVelocityRange * 0.5);

			final Agent a = new Agent(x, y, vx, vy, this, f, i);

			agents[i] = a;

			schedule.scheduleRepeating(Schedule.EPOCH, 1, a);
		}

		// schedule the current evaluation (stop condition etc)

		schedule.scheduleRepeating(Schedule.EPOCH, 2, new StoppingCondition(
				successThreshold));
	}

	/**
	 * Establishes the behaviour of the agent based on the position it is
	 * requested to be set.
	 * 
	 * We could use this to setup a toroidal world position update or for
	 * instance to simulate the behaviour or bumping against a wall and reducing
	 * the agent current velocity in that direction.
	 * 
	 * @param agent
	 * @param position
	 */
	public Double2D updatePosition(Agent agent, Double2D position,
			Double2D velocity) {

		double desiredx = position.x + velocity.x * velocityScalar;
		double desiredy = position.y + velocity.y * velocityScalar;
		// System.out.println("desired -> (" + desiredx + "," + desiredy + ")");

		// toroidal world!
		double x = space.stx(desiredx + width * 0.5) - (width * 0.5);

		// (((desiredx - 0.5*width) + width) % width) - (width * 0.5);
		double y = space.sty(desiredy + height * 0.5) - (height * 0.5);
		// (((desiredy - 0.5*width) + height)% height) - (height * 0.5);

		// System.out.println("new position -> (" + x + "," + y + ")");

		Double2D newPosition = new Double2D(x, y);
		this.space.setObjectLocation(agent, newPosition);
		// bumping into something could reduce this velocity for instance
		// the velocity could also be reduced in time by some kind of
		// "resistance"
		agent.setVelocity(velocity);
		return newPosition;

	}

	public void updateBest(double currVal, double currX, double currY) {
		if (currVal > bestVal) {
			bestVal = currVal;
			bestPosition.setTo(currX, currY);
		}
	}

	public double getNeighborhoodBest(int agentIndex, MutableDouble2D pos) {
		double bv = Double.NEGATIVE_INFINITY;
		Agent a;

		Agent[] neighbours = getNeighobours(agentIndex, pos);

		for (Agent neighbour : neighbours) {

			if (neighbour.bestFitness > bv) {
				bv = neighbour.bestFitness;
				pos.setTo(neighbour.bestPosition);
			}
		}

		return 1.0;
	}

	@Override
	public void finish() {
		super.finish();

	}

	private void cleanAgents() {
		for (Agent agent : agents) {
			agent.finish();
		}
	}

}
