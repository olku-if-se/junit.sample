package org.jacoco.gosu;


import java.lang.instrument.Instrumentation;

/**
 * Java Agent that registers Gosu filter into JaCoCo at runtime.
 * <p>
 * Usage: java -javaagent:gosu-filter-agent.jar -javaagent:jacocoagent.jar ...
 * Note: This agent MUST be loaded BEFORE jacocoagent.jar
 */
public class GosuFilterAgent {

    private static final String LOG_PREFIX = "[GosuFilterAgent]";

    /**
     * Pre-created filter instance that can be accessed by injected bytecode.
     * This is initialized lazily by GosuFilterInjector when it transforms the Filters class.
     */
    public static volatile Object GOSU_FILTER_INSTANCE = null;

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("\n" + LOG_PREFIX + " ========================================");
        System.out.println(LOG_PREFIX + " STARTING GOSU FILTER AGENT");
        System.out.println(LOG_PREFIX + " ========================================");
        System.out.println(LOG_PREFIX + " Agent Args: " + (agentArgs != null ? agentArgs : "(none)"));
        System.out.println(LOG_PREFIX + " JVM has " + inst.getAllLoadedClasses().length + " classes already loaded");

        // Check if any JaCoCo classes are already loaded
        Class<?>[] loadedClasses = inst.getAllLoadedClasses();
        int jacocoClassCount = 0;
        System.out.println(LOG_PREFIX + " Checking for already loaded JaCoCo classes:");
        for (Class<?> clazz : loadedClasses) {
            if (clazz.getName() != null && clazz.getName().toLowerCase().contains("jacoco")) {
                System.out.println(LOG_PREFIX + "   [PRE-LOADED] " + clazz.getName());
                jacocoClassCount++;
            }
        }
        System.out.println(LOG_PREFIX + " Found " + jacocoClassCount + " JaCoCo classes already loaded");
        System.out.println(LOG_PREFIX + " Gosu filter instance ready: " + (GOSU_FILTER_INSTANCE != null));

        System.out.println(LOG_PREFIX + " Registering Gosu null-safety filter transformer...");

        // Hook into JaCoCo class loading
        GosuFilterInjector injector = new GosuFilterInjector();
        inst.addTransformer(injector, true);

        System.out.println(LOG_PREFIX + " ✓ Transformer registered successfully");
        System.out.println(LOG_PREFIX + " Agent installed - waiting for JaCoCo to load...");
        System.out.println(LOG_PREFIX + " Patterns to detect:");
        System.out.println(LOG_PREFIX + "   1. Null-safe navigation: aload → ifnonnull → aconst_null → checkcast → goto");
        System.out.println(LOG_PREFIX + "   2. Defensive null check: aload → ifnonnull → new NPE → dup → invokespecial → athrow");
        System.out.println(LOG_PREFIX + "   3. Simplified null-safe: aload → ifnonnull → aconst_null → goto/areturn");
        System.out.println(LOG_PREFIX + "   4. Boolean null-safe: aload → ifnonnull → iconst_0/iconst_1 → goto");
        System.out.println(LOG_PREFIX + "   5. Array null-safe: aload → ifnonnull → iconst_0/anewarray → checkcast → goto");
        System.out.println(LOG_PREFIX + " ========================================\n");
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        System.out.println(LOG_PREFIX + " Running in attach mode");
        premain(agentArgs, inst);
    }
}

