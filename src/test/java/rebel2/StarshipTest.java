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

}