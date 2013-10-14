/*
  Copyright 2006 by Ankur Desai, Sean Luke, and George Mason University
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
 */

package mimetic.desire;

import java.awt.Color;
import java.awt.Graphics2D;

import javax.swing.JFrame;

import sim.display.Controller;
import sim.display.Display2D;
import sim.display.GUIState;
import sim.engine.SimState;
import sim.portrayal.DrawInfo2D;
import sim.portrayal.continuous.ContinuousPortrayal2D;
import sim.portrayal.simple.OvalPortrayal2D;
import sim.util.gui.SimpleColorMap;

/**
 * @author Ankur Desai and Joey Harrison
 */
public class MimeticDesireWithUI extends GUIState {
	public Display2D display;
	public JFrame displayFrame;

	public static void main(String[] args) {
		new MimeticDesireWithUI().createController(); // randomizes by
														// currentTimeMillis
	}

	public Object getSimulationInspectedObject() {
		return state;
	} // non-volatile

	public static String getName() {
		return "Evolutionary Mimetic Behaviour";
	}

	ContinuousPortrayal2D swarmPortrayal = new ContinuousPortrayal2D();

	public MimeticDesireWithUI() {
		super(new MimeticDesire(System.currentTimeMillis()));
	}

	public MimeticDesireWithUI(SimState state) {
		super(state);
	}

	public void start() {
		super.start();
		setupPortrayals();
	}

	public void load(SimState state) {
		super.load(state);
		setupPortrayals();
	}

	@SuppressWarnings("serial")
	public void setupPortrayals() {
		MimeticDesire swarm = (MimeticDesire) state;
		final SimpleColorMap map = new SimpleColorMap(
				swarm.fitnessFunctionLowerBound[swarm.fitnessFunction], 1000,
				Color.blue, Color.red);

		swarmPortrayal.setField(swarm.space);
		swarmPortrayal.setPortrayalForAll(new OvalPortrayal2D() {
			public void draw(Object object, Graphics2D graphics, DrawInfo2D info) {
				Agent agent = (Agent) object;
				this.scale = 0.1;
				this.paint = Color.green;

				paint = map.getColor(agent.getFitness());
				super.draw(object, graphics, info);
			}

		});

		// update the size of the display appropriately.
		double w = swarm.space.getWidth();
		double h = swarm.space.getHeight();
		if (w == h) {
			display.insideDisplay.width = display.insideDisplay.height = 750;
		} else if (w > h) {
			display.insideDisplay.width = 750;
			display.insideDisplay.height = 750 * (h / w);
		} else if (w < h) {
			display.insideDisplay.height = 750;
			display.insideDisplay.width = 750 * (w / h);
		}

		// reschedule the displayer
		display.reset();

		// redraw the display
		display.repaint();
	}

	public void init(Controller c) {
		super.init(c);

		// make the displayer
		display = new Display2D(750, 750, this);
		display.setBackdrop(Color.black);

		displayFrame = display.createFrame();
		displayFrame.setTitle("Fitness Landscape");
		c.registerFrame(displayFrame); // register the frame so it appears in
										// the "Display" list
		displayFrame.setVisible(true);
		display.attach(swarmPortrayal, "Behold the Swarm!",
				(display.insideDisplay.width * 0.5),
				(display.insideDisplay.height * 0.5), true);
	}

	public void quit() {
		super.quit();

		if (displayFrame != null)
			displayFrame.dispose();
		displayFrame = null;
		display = null;
	}

}
