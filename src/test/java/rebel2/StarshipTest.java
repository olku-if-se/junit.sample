package rebel2;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

class StarshipTest {

    Random random = new Random(100);

    @Test
    @DisplayName("Zero-distance travel should return zero for any ship")
    public void shouldReturnZeroForZeroDistanceForAnyShip() {
        //GIVEN: a starship and a distance we want to travel
        Starship starship = new Starship(100);
        double distance = 0;
        
        //WHEN: plan the trip
        double result = Calculator.calculate(distance, starship);
        
        //THEN: expect calculated travel time
        Assertions.assertEquals(0, result, "Expected 0");
    }

    @Test
    @DisplayName("Standard route calculation for a starship with MGLT value 75 and distance 750")
    public void shouldCalculateStandardRoute() {
        //GIVEN: a starship has MGLT value 75, and distance is 750
        Starship starship = new Starship(75);
        double distance = 750;

        //WHEN: plan the trip
        double result = Calculator.calculate(distance, starship);

        //THEN: expect calculated travel time to be 10 hours
        Assertions.assertEquals(10, result, "Expected 10 hours for 750 distance at 75 MGLT speed");
    }

    @Test
    @DisplayName("Ship with unknown MGLT")
    public void shouldHandleUnknownMGLT() {
        //GIVEN: a starship with unknown MGLT
        Starship starship = new Starship(Double.NaN); // or use a sentinel value like -1
        double distance = random.nextDouble(); // arbitrary distance

        //WHEN: plan the trip

        //THEN: expect an exception or a specific behavior (e.g., return -1 or throw an exception)
        Throwable ex = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            double result = Calculator.calculate(distance, starship);
        }, "Expected IllegalArgumentException for unknown MGLT");
        Assertions.assertEquals("Invalid Speed  NaN or Distance value " + distance, ex.getMessage(), "Expected specific error message for unknown MGLT");

    }
    @Test
    @DisplayName("A ship has MGLT:unknown → you must decide how to handle this throw, return null, etc.")
    public void shouldHandleUnknownMGLTGracefully() {
        //GIVEN: a starship with unknown MGLT


        //WHEN: plan the trip with a distance of 1000


        //THEN: expect an exception or a specific behavior (e.g., return -1 or throw an exception)
    }

    @Test
    @DisplayName("Invalid input from UI should reject with clear error handling")
    public void shouldRejectInvalidInputFromUI() {
        //GIVEN: a starship and invalid distance input
        Starship starship = new Starship(100);

        //WHEN: trying to calculate travel time with invalid distance
        try {
            double result = Calculator.calculate(Double.NaN, starship);
            Assertions.fail("Expected an exception for invalid distance input");
        } catch (Exception e) {
            //THEN: expect a NumberFormatException to be thrown
            Assertions.assertEquals("Invalid Speed  100.0 or Distance value " + Double.NaN, e.getMessage(),
                    "Expected specific error message for invalid distance");
        }
    }

    @Test
    @DisplayName("Interstellar long-haul distance calculation")
    public void shouldCalculateInterstellarLongHaul() {
        //GIVEN: a starship with MGLT value 1000 and a long distance of 1,000,000
        Starship starship = new Starship(1000);
        double distance = 1000000;

        //WHEN: plan the trip
        double result = Calculator.calculate(distance, starship);

        //THEN: expect calculated travel time to be 1000 hours
        Assertions.assertEquals(1000, result, "Expected 1000 hours for 1000000 distance at 1000 MGLT speed");
    }

    @Test
    @DisplayName("Compare ships to find the fastest starship for a given distance")
    public void shouldCompareShipsForFastestStarship() {
        //GIVEN: a list of starships with different MGLT values and a distance


        //WHEN: comparing ships to find the fastest one


        //THEN: expect the fastest ship to be the one with MGLT 200

    }

}