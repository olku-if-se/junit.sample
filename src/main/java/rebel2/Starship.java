package rebel2;

import gw.lang.Throws;

class Starship {

    private double speedInMGLT;

    public double getSpeedInMGLT() {
        return speedInMGLT;
    }

    public void setSpeedInMGLT(double speedInMGLT) {
        this.speedInMGLT = speedInMGLT;
    }

    Starship(double speedInMGLT) {
        this.speedInMGLT = speedInMGLT;
    }
}