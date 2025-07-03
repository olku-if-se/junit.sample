package rebel2;

class Starship {

    private double speedInMGLT;

    Starship(double speedInMGLT) {
        this.speedInMGLT = speedInMGLT;
    }

    public double calculate(double distance) {
        return distance / speedInMGLT;
    }
}