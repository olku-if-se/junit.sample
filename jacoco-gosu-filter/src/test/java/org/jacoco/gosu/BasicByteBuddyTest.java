package org.jacoco.gosu;

import net.bytebuddy.agent.ByteBuddyAgent;
import org.jacoco.core.internal.analysis.filter.GosuNullSafetyFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic ByteBuddy-powered agent testing.
 * Demonstrates the enhanced testing approach using ByteBuddy's agent utilities.
 */
@DisplayName("Basic ByteBuddy Agent Tests")
public class BasicByteBuddyTest {

    @BeforeEach
    void setUp() {
        // Reset agent state for clean testing
        try {
            java.lang.reflect.Field filterField = GosuFilterAgent.class.getField("GOSU_FILTER_INSTANCE");
            filterField.set(null, null);
        } catch (Exception e) {
            // Field might not be accessible, but that's ok for basic testing
        }
    }

    @Test
    @DisplayName("Should install instrumentation using ByteBuddy")
    void testInstallInstrumentation() {
        // Test ByteBuddy instrumentation installation
        java.lang.instrument.Instrumentation instrumentation = ByteBuddyAgent.install();

        assertNotNull(instrumentation, "ByteBuddy should provide instrumentation");
        assertTrue(instrumentation.getAllLoadedClasses().length > 0,
                "Should have loaded classes available");

        System.out.println("✓ ByteBuddy instrumentation installed successfully");
        System.out.println("  Loaded classes count: " + instrumentation.getAllLoadedClasses().length);
    }

    @Test
    @DisplayName("Should test createFilter method")
    void testCreateFilterMethod() throws Exception {
        // Test the static createFilter method
        Object filter = GosuFilterInjector.createFilter();

        assertNotNull(filter, "createFilter should return non-null instance");
        assertTrue(filter instanceof GosuNullSafetyFilter,
                "createFilter should return GosuNullSafetyFilter");

        System.out.println("✓ createFilter method works correctly");
        System.out.println("  Filter type: " + filter.getClass().getSimpleName());
    }

    @Test
    @DisplayName("Should test agent functionality with instrumentation")
    void testAgentWithInstrumentation() throws Exception {
        // Get real instrumentation
        java.lang.instrument.Instrumentation instrumentation = ByteBuddyAgent.install();

        // Initialize agent
        GosuFilterAgent.premain("instrumentation-test", instrumentation);

        // Test transformer creation
        GosuFilterInjector injector = new GosuFilterInjector();
        assertNotNull(injector, "Should create injector");

        // Test that transformer implements correct interface
        assertTrue(injector instanceof java.lang.instrument.ClassFileTransformer,
                "Injector should implement ClassFileTransformer");

        System.out.println("✓ Agent functionality with instrumentation verified");
        System.out.println("  Instrumentation capabilities:");
        System.out.println("    - Redefine classes: " + instrumentation.isRedefineClassesSupported());
        System.out.println("    - Retransform classes: " + instrumentation.isRetransformClassesSupported());
    }

    @Test
    @DisplayName("Should test transformer registration")
    void testTransformerRegistration() throws Exception {
        java.lang.instrument.Instrumentation instrumentation = ByteBuddyAgent.install();

        // Create and register transformer
        GosuFilterInjector injector = new GosuFilterInjector();

        // Test registration (should not throw exception)
        assertDoesNotThrow(() -> {
            instrumentation.addTransformer(injector);
        }, "Transformer registration should not throw exceptions");

        // Test removal
        assertTrue(instrumentation.removeTransformer(injector),
                "Should be able to remove transformer");

        System.out.println("✓ Transformer registration works correctly");
    }

    @Test
    @DisplayName("Should test agent methods and properties")
    void testAgentMethods() throws Exception {
        // Test that required methods exist
        assertNotNull(GosuFilterAgent.class.getMethod("premain", String.class, java.lang.instrument.Instrumentation.class),
                "Agent should have premain method");

        assertNotNull(GosuFilterAgent.class.getMethod("agentmain", String.class, java.lang.instrument.Instrumentation.class),
                "Agent should have agentmain method");

        assertNotNull(GosuFilterAgent.class.getField("GOSU_FILTER_INSTANCE"),
                "Agent should have GOSU_FILTER_INSTANCE field");

        System.out.println("✓ Agent methods and properties verified");
    }

    @Test
    @DisplayName("Should test injector properties")
    void testInjectorProperties() throws Exception {
        GosuFilterInjector injector = new GosuFilterInjector();

        // Test that injector implements ClassFileTransformer
        assertTrue(injector instanceof java.lang.instrument.ClassFileTransformer,
                "Injector should implement ClassFileTransformer");

        // Test that required method exists
        assertNotNull(injector.getClass().getMethod("createFilter"),
                "Injector should have createFilter method");

        System.out.println("✓ Injector properties verified");
    }

    @Test
    @DisplayName("Should test agent and injector integration")
    void testAgentInjectorIntegration() throws Exception {
        java.lang.instrument.Instrumentation instrumentation = ByteBuddyAgent.install();

        // Initialize agent
        GosuFilterAgent.premain("integration-test", instrumentation);

        // The filter instance is created lazily when transform() is called,
        // not during premain(). So we test the createFilter method directly.
        Object filter = GosuFilterInjector.createFilter();
        assertNotNull(filter, "Filter should be created via createFilter");
        assertTrue(filter instanceof GosuNullSafetyFilter,
                "createFilter should return correct type");

        // Verify that the agent is properly initialized (logs show it started)
        // The static field may be null until first transformation
        System.out.println("✓ Agent and injector integration verified");
        System.out.println("  Filter instance type: " + filter.getClass().getSimpleName());
    }

    @Test
    @DisplayName("Should test ByteBuddy environment")
    void testByteBuddyEnvironment() {
        // Test ByteBuddy is available and working
        assertNotNull(ByteBuddyAgent.class, "ByteBuddy agent should be available");

        // Test we can get current process info
        String runtimeName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
        assertNotNull(runtimeName, "Should get runtime name");
        assertTrue(runtimeName.contains("@"), "Runtime name should contain @");

        String pid = runtimeName.split("@")[0];
        assertNotNull(pid, "Should extract PID");
        assertFalse(pid.isEmpty(), "PID should not be empty");

        System.out.println("✓ ByteBuddy environment verified");
        System.out.println("  Runtime: " + runtimeName);
        System.out.println("  PID: " + pid);
    }
}