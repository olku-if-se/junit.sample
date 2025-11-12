package org.jacoco.gosu;

import org.jacoco.core.internal.analysis.filter.GosuNullSafetyFilter;
import org.objectweb.asm.*;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * Bytecode transformer that injects GosuNullSafetyFilter into JaCoCo's Filters class.
 *
 * <p>Strategy: Modify the {@code allNonKotlinFilters()} method in the Filters class
 * to call {@code GosuFilterInjector.createFilter()} which returns a filter instance
 * loaded from the agent's classloader.
 *
 * <p>This approach works with standard JaCoCo 0.8.14 without requiring a custom build.
 */
public class GosuFilterInjector implements ClassFileTransformer {

    private static final String LOG_PREFIX = "[GosuFilterInjector]";
    private static final String FILTERS_CLASS = "org/jacoco/core/internal/analysis/filter/Filters";
    private static final String FILTER_SET_CLASS = "org/jacoco/core/internal/analysis/filter/FilterSet";
    private static final String IFILTER_CLASS = "org/jacoco/core/internal/analysis/filter/IFilter";
    private static boolean injected = false;

    /**
     * Factory method that creates a GosuNullSafetyFilter instance.
     * This method is called by the injected bytecode and can access
     * classes from the agent JAR via the agent's classloader.
     */
    public static Object createFilter() {
        try {
            // Load and instantiate the filter using this class's classloader (the agent classloader)
            Class<?> filterClass = GosuFilterInjector.class.getClassLoader()
                    .loadClass("org.jacoco.core.internal.analysis.filter.GosuNullSafetyFilter");
            return filterClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            System.err.println(LOG_PREFIX + " Failed to create GosuNullSafetyFilter: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to create GosuNullSafetyFilter", e);
        }
    }

    @Override
    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer) {

        // Debug: Log ALL classes with "jacoco" in the name to understand the loading pattern
        if (className != null && className.toLowerCase().contains("jacoco")) {
            System.out.println(LOG_PREFIX + " [JACOCO-CLASS] " + className);
        }

        // Debug: Log JaCoCo filter classes being loaded
        if (className != null && className.startsWith("org/jacoco/core/internal/analysis/filter/")) {
            System.out.println(LOG_PREFIX + " [FILTER-CLASS] " + className);
        }

        // Target the Filters class
        if (FILTERS_CLASS.equals(className)) {
            System.out.println(LOG_PREFIX + " ========================================");
            System.out.println(LOG_PREFIX + " INTERCEPTING FILTERS CLASS");
            System.out.println(LOG_PREFIX + " ========================================");

            try {
                // Create filter instance and store in GosuFilterAgent for access by injected bytecode
                if (GosuFilterAgent.GOSU_FILTER_INSTANCE == null) {
                    System.out.println(LOG_PREFIX + " Creating GosuNullSafetyFilter instance...");
                    GosuFilterAgent.GOSU_FILTER_INSTANCE = new GosuNullSafetyFilter();
                    System.out.println(LOG_PREFIX + " ✓ Filter instance created and stored");
                }

                byte[] modifiedClass = injectGosuFilter(classfileBuffer);
                System.out.println(LOG_PREFIX + " ✓ BYTECODE INJECTION SUCCESSFUL!");
                System.out.println(LOG_PREFIX + " ========================================");
                injected = true;
                return modifiedClass;
            } catch (Exception e) {
                System.err.println(LOG_PREFIX + " ✗ BYTECODE INJECTION FAILED");
                System.err.println(LOG_PREFIX + " Exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                e.printStackTrace();
                return classfileBuffer; // Return original if transformation fails
            }
        }

        return null; // No transformation for other classes
    }

    /**
     * Modifies the Filters class bytecode to inject GosuNullSafetyFilter.
     */
    private byte[] injectGosuFilter(byte[] originalBytecode) {
        ClassReader cr = new ClassReader(originalBytecode);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        ClassVisitor cv = new FiltersClassVisitor(cw);

        cr.accept(cv, 0);
        return cw.toByteArray();
    }

    /**
     * Custom ClassVisitor that intercepts the allNonKotlinFilters() method.
     */
    private static class FiltersClassVisitor extends ClassVisitor {

        public FiltersClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

            // Target the allNonKotlinFilters() method
            if ("allNonKotlinFilters".equals(name) && "()Lorg/jacoco/core/internal/analysis/filter/IFilter;".equals(descriptor)) {
                System.out.println(LOG_PREFIX + " Found allNonKotlinFilters() method - injecting GosuNullSafetyFilter");
                return new NonKotlinFiltersMethodVisitor(mv);
            }

            return mv;
        }
    }

    /**
     * Method visitor that modifies allNonKotlinFilters() to add GosuNullSafetyFilter.
     *
     * <p>Original bytecode pattern:
     * <pre>
     * NEW FilterSet
     * DUP
     * ICONST_1 (array size)
     * ANEWARRAY IFilter
     * DUP
     * ICONST_0
     * NEW SyntheticFilter
     * DUP
     * INVOKESPECIAL SyntheticFilter.&lt;init&gt;
     * AASTORE
     * INVOKESPECIAL FilterSet.&lt;init&gt;([LIFilter;)V
     * ARETURN
     * </pre>
     *
     * <p>Modified bytecode:
     * <pre>
     * NEW FilterSet
     * DUP
     * ICONST_2 (array size = 2 instead of 1)
     * ANEWARRAY IFilter
     * DUP
     * ICONST_0
     * NEW SyntheticFilter
     * DUP
     * INVOKESPECIAL SyntheticFilter.&lt;init&gt;
     * AASTORE
     * DUP
     * ICONST_1 (second element)
     * NEW GosuNullSafetyFilter
     * DUP
     * INVOKESPECIAL GosuNullSafetyFilter.&lt;init&gt;
     * AASTORE
     * INVOKESPECIAL FilterSet.&lt;init&gt;([LIFilter;)V
     * ARETURN
     * </pre>
     */
    private static class NonKotlinFiltersMethodVisitor extends MethodVisitor {
        private boolean arrayCreated = false;
        private boolean syntheticFilterAdded = false;

        public NonKotlinFiltersMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitInsn(int opcode) {
            // Intercept ICONST_1 (array size) and change it to ICONST_2
            if (opcode == Opcodes.ICONST_1 && !arrayCreated) {
                System.out.println(LOG_PREFIX + "   → Changing array size from 1 to 2");
                super.visitInsn(Opcodes.ICONST_2);
                arrayCreated = true;
                return;
            }

            // After the first AASTORE (storing SyntheticFilter), add GosuNullSafetyFilter
            if (opcode == Opcodes.AASTORE && !syntheticFilterAdded) {
                super.visitInsn(opcode); // Store SyntheticFilter first

                System.out.println(LOG_PREFIX + "   → Adding GosuNullSafetyFilter via reflection");

                // DUP (duplicate array reference)
                super.visitInsn(Opcodes.DUP);

                // ICONST_1 (index for second element)
                super.visitInsn(Opcodes.ICONST_1);

                // Get pre-created filter instance from static field:
                // GosuFilterAgent.GOSU_FILTER_INSTANCE

                System.out.println(LOG_PREFIX + "   → Injecting static field access to pre-created filter");

                // GETSTATIC org/jacoco/gosu/GosuFilterAgent.GOSU_FILTER_INSTANCE : Ljava/lang/Object;
                super.visitFieldInsn(Opcodes.GETSTATIC,
                        "org/jacoco/gosu/GosuFilterAgent",
                        "GOSU_FILTER_INSTANCE",
                        "Ljava/lang/Object;");

                // CHECKCAST IFilter (cast Object to IFilter)
                super.visitTypeInsn(Opcodes.CHECKCAST, "org/jacoco/core/internal/analysis/filter/IFilter");

                // AASTORE (store in array at index 1)
                super.visitInsn(Opcodes.AASTORE);

                syntheticFilterAdded = true;
                return;
            }

            super.visitInsn(opcode);
        }

        @Override
        public void visitEnd() {
            if (syntheticFilterAdded) {
                System.out.println(LOG_PREFIX + "   ✓ GosuNullSafetyFilter successfully added to filter chain");
            } else {
                System.err.println(LOG_PREFIX + "   ✗ WARNING: Could not add GosuNullSafetyFilter");
            }
            super.visitEnd();
        }
    }
}
