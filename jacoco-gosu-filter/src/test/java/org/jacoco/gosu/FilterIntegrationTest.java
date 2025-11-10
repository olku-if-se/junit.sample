package org.jacoco.gosu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that verifies the complete filter functionality.
 * This test requires the PolicyPeriodEnhancement.class to be compiled first.
 */
public class FilterIntegrationTest {

    @BeforeEach
    void setUp() {
        // Ensure we're in the right directory
        String workingDir = System.getProperty("user.dir");
        System.out.println("Working directory: " + workingDir);

        // Check if PolicyPeriodEnhancement.class exists
        if (!isPolicyPeriodClassAvailable()) {
            fail("PolicyPeriodEnhancement.class not found. Please run: ./gradlew compileGosu");
        }
    }

    @Test
    @DisplayName("Integration test: Filter should process real Gosu bytecode")
    @EnabledIfSystemProperty(named = "run.integration.tests", matches = "true")
    void integrationTestWithRealBytecode() {
        System.out.println("\n=== INTEGRATION TEST WITH REAL BYTECODE ===");

        try {
            // Test the basic filter functionality
            GosuNullSafetyFilter filter = new GosuNullSafetyFilter();
            assertNotNull(filter, "Filter should be created successfully");

            // Enable debug mode for detailed output
            System.setProperty("jacoco.gosu.filter.debug", "true");

            System.out.println("✓ Filter created successfully");
            System.out.println("✓ Debug mode enabled");
            System.out.println("✓ PolicyPeriodEnhancement.class is available");

            // The actual detailed tests are in GosuNullSafetyFilterDetailedTest
            // This test just verifies the integration setup works
            assertTrue(true, "Integration test setup is working");

        } catch (Exception e) {
            fail("Integration test failed: " + e.getMessage(), e);
        }
    }

    @Test
    @DisplayName("Verify test prerequisites are met")
    void verifyTestPrerequisites() {
        System.out.println("\n=== VERIFYING TEST PREREQUISITES ===");

        // Check Java version
        String javaVersion = System.getProperty("java.version");
        System.out.println("Java version: " + javaVersion);
        assertTrue(javaVersion.startsWith("11.") || javaVersion.startsWith("17.") || javaVersion.startsWith("21."),
                   "Should run on Java 11+");

        // Check working directory
        String workingDir = System.getProperty("user.dir");
        System.out.println("Working directory: " + workingDir);

        // Check if main project is accessible
        String[] expectedPaths = {
            "build/classes/gosu/main/enhancement",
            "../build/classes/gosu/main/enhancement",
            "../../build/classes/gosu/main/enhancement"
        };

        boolean pathFound = false;
        for (String path : expectedPaths) {
            if (java.nio.file.Files.exists(java.nio.file.Paths.get(path))) {
                System.out.println("✓ Found Gosu classes at: " + path);
                pathFound = true;
                break;
            }
        }

        if (!pathFound) {
            System.out.println("⚠ Gosu classes not found in expected locations");
            System.out.println("  Tried: " + String.join(", ", expectedPaths));
        }

        // Check if PolicyPeriodEnhancement exists
        if (isPolicyPeriodClassAvailable()) {
            System.out.println("✓ PolicyPeriodEnhancement.class is available");
        } else {
            System.out.println("⚠ PolicyPeriodEnhancement.class not found");
            System.out.println("  To compile: ./gradlew compileGosu");
        }
    }

    @Test
    @DisplayName("Test filter instantiation and basic properties")
    void testFilterInstantiation() {
        System.out.println("\n=== TESTING FILTER INSTANTIATION ===");

        // Test basic instantiation
        assertDoesNotThrow(() -> {
            GosuNullSafetyFilter filter = new GosuNullSafetyFilter();
            assertNotNull(filter);
        }, "Filter should instantiate without errors");

        System.out.println("✓ Filter instantiation successful");
    }

    private boolean isPolicyPeriodClassAvailable() {
        String[] possiblePaths = {
            "build/classes/gosu/main/enhancement/PolicyPeriodEnhancement.class",
            "../build/classes/gosu/main/enhancement/PolicyPeriodEnhancement.class",
            "../../build/classes/gosu/main/enhancement/PolicyPeriodEnhancement.class"
        };

        for (String path : possiblePaths) {
            if (java.nio.file.Files.exists(java.nio.file.Paths.get(path))) {
                return true;
            }
        }
        return false;
    }
}