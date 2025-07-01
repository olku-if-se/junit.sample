package org.api;

import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.junit.jupiter.api.RepeatedTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CatFactNinjaContractTests {

    //@Test
    @RepeatedTest(10)
    public void shouldValidateContractBetweenAppAndCatFactNinjaApi() throws Exception {
        // Given: Rest Api client for CatFactNinja;
        final CatFactNinja apiClient = ApiClientFactory.createCatFactNinjaClient();

        // When: Getting a catchy cat fact
        final String result = apiClient.getRandomFact();

        // Then: expected result is not null
        assertNotNull(result, "Last operation should not be null");

        // And: the simplest contract testing (only the response body)
        final Schema schema = SchemaLoader.load(
                new JSONObject(String.join("\n",
                        "{",
                        "  \"type\": \"object\",",
                        "  \"properties\": {",
                        "    \"fact\": {",
                        "      \"type\": \"string\"",
                        "    },",
                        "    \"length\": {",
                        "      \"type\": \"integer\"",
                        "    }",
                        "  },",
                        "  \"required\": [\"fact\", \"length\"]",
                        "}"
                ))
        );
        schema.validate(new JSONObject(result)); // Validate that the result is a valid JSON object
        System.err.println(result);
    }
}
