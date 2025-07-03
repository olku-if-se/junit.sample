package rebel2

class Calculator {

  static function calculate(distance : double, starship : Starship) : double {
    if (Double.isNaN(distance) || Double.isNaN(starship.getSpeedInMGLT()) || distance < 0 || starship.getSpeedInMGLT() < 0) {
      throw new IllegalArgumentException("Invalid Speed  " + starship.getSpeedInMGLT() + " or Distance value " + distance);
    }
    return distance / starship.getSpeedInMGLT();
  }
}