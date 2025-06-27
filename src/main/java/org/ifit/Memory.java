package org.ifit;

import java.util.List;

public interface Memory {
    void save(String operation, int a, int b, String result);

    List<Record> history();

    interface Record {
        String operation();

        int a();

        int b();

        String result();

        String timestamp();
    }
}
