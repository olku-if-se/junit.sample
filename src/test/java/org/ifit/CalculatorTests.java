package org.ifit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.internal.verification.Times;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class CalculatorTests {
    private static Memory _mockedLastOperationOnly;

    @BeforeAll
    public static void setup() {
        _mockedLastOperationOnly = mock(LastOperationOnly.class);
    }

    @BeforeEach
    public void resetMocks() {
        Mockito.reset(_mockedLastOperationOnly);
    }

    @Test
    @BadExample("This test is not well-structured and does not follow best practices.")
    public void firstCalculatorTest() {
        final Calculator calculator = new Calculator();
        assertNotNull(calculator, "Calculator instance should not be null");

        int result = calculator.add(2, 3);

        assert result == 5 : "Expected 2 + 3 to equal 5";
        assertTrue("Calculator should return correct addition result", result == 5);
    }

    @Test
    @GoodExample("This test is well-structured and follows best practices.")
    public void shouldUseMockingOfTheInterfaceInsteadOfImplementation() {
        // Given: Calculator with a mocked memory engine
        final Memory mockedMemory = mock(Memory.class);
        final Calculator calculator = new Calculator(mockedMemory);

        // When: Performing an addition operation
        calculator.add(2, 3);

        // Then: expected saved operation in memory/history
        verify(mockedMemory).save("add", 2, 3, "5");
    }

    @Test
    public void shouldHighlightThatMockIsHeavyOperationForFirstTestOnly() {
        // Given: Calculator with a mocked memory engine
        final Calculator calculator = new Calculator(_mockedLastOperationOnly);

        // When: Performing an addition operation
        calculator.add(2, 3);

        // Then: expected saved operation in memory/history
        verify(_mockedLastOperationOnly).save("add", 2, 3, "5");
    }

    @Test
    public void shouldDemonstrateLocaleHiddenDependencyInNumberFormatting() {
        // Given: Calculator with a mocked memory engine
        final Calculator calculator = new Calculator(_mockedLastOperationOnly);

        // When: Performing an addition operation
        calculator.divide(2, 3);

        // Then: expected saved operation in memory but no history
        verify(_mockedLastOperationOnly).save("divide", 2, 3, "0,667");

        // And: verify that history is not called and not saved due to mocks usage
        verify(_mockedLastOperationOnly, new Times(0)).history();
        Assertions.assertNotNull(_mockedLastOperationOnly.history(), "History should be null for LastOperationOnly memory implementation");
        Assertions.assertTrue(_mockedLastOperationOnly.history().isEmpty(), "History should be empty for LastOperationOnly memory implementation");

        // Important! Mocks not always repeat the behavior of the original implementation
        // history() call in implementation return null, but in mock it returns empty list
    }

    @Test
    public void shouldHighlightSpyMisconceptionDuringUsing() {
        // Given: Calculator with LastOperationOnly memory engine covered by spy
        final Memory memory = new LastOperationOnly();
        final Memory spyMemory = Mockito.spy(memory);
        final Calculator calculator = new Calculator(spyMemory);

        // When: Performing an addition operation
        calculator.add(2, 3);

        // Then: expected saved operation in memory/history
        verify(spyMemory).save("add", 2, 3, "5");
        Assertions.assertEquals(1, spyMemory.history().size(), "History should contain one operation after addition");

        // And: highlight the misconception of using spy
        Assertions.assertNull(memory.history(), "Spy is a Proxy that replace memory state with its own. So original state of the memory is untouched!");
    }
}
