package rebel2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StarshipTest {

    Random random = new Random(new Date().getTime());

    @Test
    @DisplayName("Standard route calculation for a starship with MGLT value 75 and distance 750")
    public void shouldCalculateStandardRoute() {
        // Given:a starship has MGLT value 75, and distance is 750
        Starship starship = new Starship(75);
        double distance = 750;

        // When: plan the trip
        double result = Planing.calculate(distance, starship);

        // Then: expect calculated travel time to be 10 hours
        assertEquals(10, result, "Expected 10 hours for 750 distance at 75 MGLT speed");
    }

    @Test
    @DisplayName("Ship with unknown MGLT")
    public void shouldHandleUnknownMGLT() {
        // Given:a starship with unknown MGLT
        Starship starship = new Starship(Double.NaN); // or use a sentinel value like -1
        double distance = random.nextDouble(); // arbitrary distance

        // When: plan the trip
        Throwable ex = assertThrows(IllegalArgumentException.class, () -> {
            double result = Planing.calculate(distance, starship);
        }, "Expected IllegalArgumentException for unknown MGLT");

        // Then: expect an exception or a specific behavior (e.g., return -1 or throw an exception)
        assertEquals("Invalid Speed  NaN or Distance value " + distance, ex.getMessage(), "Expected specific error message for unknown MGLT");
    }

    @Test
    @DisplayName("Zero-distance travel should return zero for any ship")
    public void shouldReturnZeroForZeroDistanceForAnyShip() {
        // Given:a starship and a distance we want to travel
        Starship starship = new Starship(100);
        double distance = 0;

        // When: plan the trip
        double result = Planing.calculate(distance, starship);

        // Then: expect calculated travel time
        assertEquals(0, result, "Expected 0");
    }

    @Test
    @DisplayName("A ship has MGLT:unknown → you must decide how to handle this throw, return null, etc.")
    public void shouldHandleUnknownMGLTGracefully() {
        // Given:a starship with unknown MGLT
        Starship starship = new Starship(Double.NaN); // or use a sentinel value like -1
        double distance = random.nextDouble(); // arbitrary distance


        // When: plan the trip with a distance of 1000
        Throwable ex = assertThrows(IllegalArgumentException.class, () -> {
            double result = Planing.calculate(distance, starship);
        }, "Expected IllegalArgumentException for unknown MGLT");

        // Then: expect an exception or a specific behavior (e.g., return -1 or throw an exception)
        assertEquals("Invalid Speed  NaN or Distance value " + distance, ex.getMessage(),
                "Expected specific error message for unknown MGLT");
    }

    @Test
    @DisplayName("Invalid input from UI should reject with clear error handling")
    public void shouldRejectInvalidInputFromUI() {
        // Given:a starship and invalid distance input
        Starship starship = new Starship(100);

        // When: trying to calculate travel time with invalid distance
        Throwable e = assertThrows(IllegalArgumentException.class, () -> {
            double result = Planing.calculate(Double.NaN, starship);
        }, "Expected IllegalArgumentException for invalid distance input");

        // Then: expect a NumberFormatException to be thrown
        assertEquals("Invalid Speed  100.0 or Distance value " + Double.NaN, e.getMessage(),
                "Expected specific error message for invalid distance");
    }

    @Test
    @DisplayName("Interstellar long-haul distance calculation")
    public void shouldCalculateInterstellarLongHaul() {
        // Given:a starship with MGLT value 1000 and a long distance of 1,000,000
        Starship starship = new Starship(1000);
        double distance = 1000000;

        // When: plan the trip
        double result = Planing.calculate(distance, starship);

        // Then: expect calculated travel time to be 1000 hours
        assertEquals(1000, result, "Expected 1000 hours for 1000000 distance at 1000 MGLT speed");
    }

    @Test
    @DisplayName("Compare ships to find the fastest starship for a given distance")
    public void shouldCompareShipsForFastestStarship() {
        // Given:a list of starships with different MGLT values and a distance
        Starship ship1 = new Starship(100); // 10 hours for 1000 distance
        Starship ship2 = new Starship(200); // 5 hours for 1000 distance

        // When: comparing ships to find the fastest one
        Starship fastestShip = Planing.findFastestShip(List.of(ship1, ship2));

        // Then: expect the fastest ship to be the one with MGLT 200
        assertEquals(ship2, fastestShip, "Expected ship with MGLT 200 to be the fastest ship");
    }

}