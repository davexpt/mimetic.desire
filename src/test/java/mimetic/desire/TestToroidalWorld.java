package mimetic.desire;

import static org.junit.Assert.*;

import org.junit.Test;

import sim.util.Double2D;

public class TestToroidalWorld {

	@Test
	public void test() {
		double width = 10;
		double height = 10;

		Double2D position = new Double2D(5, 5);
		Double2D velocity = new Double2D(1, 0);

		double x = ((position.x + velocity.x + width * 0.5) % width)
				- (width * 0.5);
		double y = ((position.y + velocity.y + height * 0.5) % height)
				- (height * 0.5);

		assertTrue(x < position.x);
		assertTrue(y < position.y);
		
		System.out.println(position.x +" + "+velocity.x+" = "+x);
		System.out.println(position.y +" + "+velocity.y+" = "+y);
	}

}
