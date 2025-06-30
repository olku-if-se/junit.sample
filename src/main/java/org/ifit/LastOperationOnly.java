package org.ifit;

import java.time.Instant;
import java.util.List;

public class LastOperationOnly implements Memory {
    private Record lastOperation;

    @Override
    public void save(String operation, int a, int b, String result) {
        final String hashCode = String.valueOf(this.hashCode());
        final Instant now = Instant.now();

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
            public String timestamp() {
                return String.valueOf(now);
            }

            @Override
            public String toString() {
                // return String.format("%s(%d, %d) == %s instance: #%s", operation(), a(), b(), result(), hashCode);
                System.out.println("instance: #" + hashCode);
                return String.format("%s(%d, %d) == %s", operation(), a(), b(), result());
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
