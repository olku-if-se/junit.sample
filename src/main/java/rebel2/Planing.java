package rebel2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Planing {
    public static double calculate(double distance, Starship starship) {
        if (Double.isNaN(distance) || Double.isNaN(starship.getSpeedInMGLT()) || distance < 0 || starship.getSpeedInMGLT() <= 0) {
            throw new IllegalArgumentException("Invalid Speed  " + starship.getSpeedInMGLT() + " or Distance value " + distance);
        }

        return distance / starship.getSpeedInMGLT();
    }

    public static Starship findFastestShip(List<Starship> ships) {
        if (ships == null || ships.isEmpty()) {
            throw new IllegalArgumentException("Ship list cannot be null or empty");
        }

        List<Starship> sortable = new ArrayList(ships);
        sortable.sort(Comparator.comparingDouble(Starship::getSpeedInMGLT));
        return sortable.get(ships.size() - 1); // the last ship in the sorted list is the fastest
    }
}
