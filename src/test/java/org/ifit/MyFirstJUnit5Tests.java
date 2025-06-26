package org.ifit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertTrue;

public class MyFirstJUnit5Tests {
    @Test
    public void my_test_that_i_like_to_run_in_junit5() {
        assertTrue("Hello, World!".equals("Hello, World!"));
    }

    @Test
    @Disabled
    public void second_test() {
        // Another test method in JUnit 5 style.
        assertTrue("This test should pass", true);
    }
}
