package rebel2;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class StarshipTest {

    @Test
    public void shouldReturnZeroForZeroDistanceForAnyShip() {
        //GIVEN: a starship and a distance we want to travel
        Starship starship = new Starship(100);
        double distance = 0;
        
        //WHEN: plan the trip
        double result = starship.calculate(distance);
        
        //THEN: expect calculated travel time
        Assertions.assertEquals(0, result, "Expected 0");
    }

    @Test
    public void shouldCalculateStandardRoute() {
        //GIVEN: a starship has MGLT value 75, and distance is 750
        Starship starship = new Starship(75);
        double distance = 750;

        //WHEN: plan the trip
        double result = starship.calculate(distance);

        //THEN: expect calculated travel time to be 10 hours
        Assertions.assertEquals(10, result, "Expected 10 hours for 750 MGLT at 75 MGLT speed");
    }

}