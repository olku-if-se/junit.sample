package org.ifit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.api.ApiClientFactory;
import org.api.CatFactNinja;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CalculatorIntegrationTests {
    // bad sample of mocking, when exactly we create the mock? Before all?
    private static final CatFactNinja _mockedCatFactNinja = mock(CatFactNinja.class);
    // Highly configurable web server that allows us to mock REST API calls
    private MockWebServer _server = null;

    @BeforeEach
    public void startWebServer() throws Exception {
        _server = new MockWebServer();
        _server.start();
    }

    @AfterEach
    public void shutdownWebServer() throws Exception {
        if (_server != null) {
            _server.shutdown();
            _server = null;
        }
    }

    @BeforeEach
    public void resetMocks() {
        Mockito.reset(_mockedCatFactNinja);
    }

    @Test
    public void shouldReturnCatFactOnLastOperationWithCatchyCatFactAndMockedApiClient() {
        // Given: Calculator with a mocked API client
        final Calculator calculator = new Calculator(new LastOperationOnly(), _mockedCatFactNinja);

        // Mocking the API response to return a catchy cat fact
        when(_mockedCatFactNinja.getRandomFact())
                .thenReturn("test fact");

        // When: Getting the last operation with a catchy cat fact
        calculator.add(1, 2);
        final String result = calculator.getLastOperationWitCatchyCatFact();

        // Then: expected result is not null and contains a cat fact
        assertEquals("[0]: add(1, 2) == 3 fact: test fact", result, "Last operation should have test fact");
    }

    @Test
    public void shouldCallTheCatFactNinjaApiWithMockedApiServer() throws Exception {
        // Given: Mocked REST API server
        _server.enqueue(new MockResponse().setBody("{\"fact\": \"Cats are great!\"}"));

        // And: Calculator with test api server in use
        final CatFactNinja apiClient = ApiClientFactory.createCatFactNinjaClient(_server.url("/").toString());
        final Calculator calculator = new Calculator(apiClient);

        // When: Getting the last operation with a catchy cat fact
        calculator.add(1, 2);
        String result = calculator.getLastOperationWitCatchyCatFact();

        // Then: expected API was called
        assertNotNull(result, "Last operation should not be null");
        assertEquals("[0]: add(1, 2) == 3 fact: {\"fact\": \"Cats are great!\"}", result,
                "Last operation should have the fact from mocked API");
    }

    @Test
    public void shouldDoRealApiCallAndPrintRandomFact() throws Exception {
        // Given: Calculator with real API client
        final Calculator calculator = new Calculator();

        // When: Getting the last operation with a catchy cat fact
        calculator.add(1, 2);
        String result = calculator.getLastOperationWitCatchyCatFact();

        // Then: expected result is not null and contains a cat fact
        assertNotNull(result, "Last operation should not be null");
        System.out.println(result); // Print the result to console
    }
}
