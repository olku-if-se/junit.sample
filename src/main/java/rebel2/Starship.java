package rebel2;

import gw.lang.Throws;

class Starship {

    private double speedInMGLT;

    Starship(double speedInMGLT) {
        this.speedInMGLT = speedInMGLT;
    }

    public double calculate(double distance) {
        if (Double.isNaN(distance) || Double.isNaN(speedInMGLT) || distance < 0 || speedInMGLT < 0) {
            throw new IllegalArgumentException("Invalid Speed  " + speedInMGLT + " or Distance value " + distance);
        }
        return distance / speedInMGLT;
    }
}