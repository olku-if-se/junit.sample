package org.ifit;

import java.util.List;

public class LastOperationOnly implements Memory {
    private Record lastOperation;

    @Override
    public void save(String operation, int a, int b, String result) {
        final String hashCode = String.valueOf(this.hashCode());
        final Record record = new Record() {
            @Override
            public String operation() {
                return operation;
            }

            @Override
            public int a() {
                return a;
            }

            @Override
            public int b() {
                return b;
            }

            @Override
            public String result() {
                return result;
            }

            @Override
            public String toString() {
                return String.format("%s(%d, %d) == %s instance: #%s", operation(), a(), b(), result(), hashCode);
            }
        };

        this.lastOperation = record;
    }

    @Override
    public List<Record> history() {
        if (this.lastOperation == null) {
            return null;
        }

        return List.of(this.lastOperation);
    }
}
