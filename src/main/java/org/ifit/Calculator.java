package org.ifit;

import org.api.ApiClientFactory;
import org.api.CatFactNinja;

import java.text.NumberFormat;
import java.util.List;

public class Calculator {
    private final Memory engine;
    private final CatFactNinja api;

    public Calculator() {
        this(new LastOperationOnly());
    }

    protected Calculator(final Memory engine) {
        this(engine, ApiClientFactory.createCatFactNinjaClient());
    }

    protected Calculator(final CatFactNinja api) {
        this(new LastOperationOnly(), api);
    }

    protected Calculator(final Memory engine, final CatFactNinja api) {
        this.engine = engine;
        this.api = api;
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

    public String getLastOperationWitCatchyCatFact() {
        final List<Memory.Record> history = this.engine.history();
        if (history == null || history.isEmpty()) {
            return "No operations performed yet.";
        }

        final String fact = this.api.getRandomFact();
        return "[0]: " + history.get(0).toString() + " fact: " + fact;
    }
}
