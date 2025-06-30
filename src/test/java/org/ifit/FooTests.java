package org.ifit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class FooTests {
    @Test
    @Disabled
    public void shouldBeAbleToAccessGosuFooClass() {
        // Given: A Foo class in Gosu
        final Foo foo = new Foo();

        // When: Calling the method
        String result = foo.bar();

        // Then: The result should be as expected
        assert "Hello, World!".equals(result) : "Expected greeting to be 'Hello, World!'";
    }
}
