package org.ifit;

import java.text.NumberFormat;

public class Calculator {
    private final Memory engine;

    public Calculator() {
        this(new LastOperationOnly());
    }

    protected Calculator(final Memory engine) {
        this.engine = engine;
    }

    public int add(int a, int b) {
        final int result = a + b;
        this.engine.save("add", a, b, String.valueOf(result));
        return result;
    }

    public int subtract(int a, int b) {
        final int result = a - b;
        this.engine.save("subtract", a, b, String.valueOf(result));
        return result;
    }

    public int multiply(int a, int b) {
        final int result = a * b;
        this.engine.save("multiply", a, b, String.valueOf(result));
        return result;
    }

    public double divide(int a, int b) {
        if (b == 0) {
            this.engine.save("divide", a, b, "N/A");
            throw new IllegalArgumentException("Division by zero is not allowed.");
        }

        final double result = (double) a / b;
        this.engine.save("divide", a, b, NumberFormat.getInstance().format(result));

        return result;
    }
}
