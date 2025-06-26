package org.ifit;

import junit.framework.TestCase;

public class MyFirstJUnit4StyleTests extends TestCase {

    public void testOldStyleDeclarationOfTheTest() {
        assertEquals("Hello, World!", "Hello, World!");
    }

    public void testSecondTest() {
        assertTrue("This test should pass", true);
    }
}
