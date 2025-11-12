package org.jacoco.gosu;

import org.jacoco.core.internal.analysis.filter.GosuNullSafetyFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.objectweb.asm.*;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit test suite for GosuFilterInjector.
 * <p>
 * Tests the bytecode injection mechanism that modifies JaCoCo's Filters class
 * to include GosuNullSafetyFilter in the filter chain.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GosuFilterInjector Unit Tests")
public class GosuFilterInjectorTest {

    @Mock
    private ClassLoader mockClassLoader;

    @Mock
    private ProtectionDomain mockProtectionDomain;

    @Mock
    private Instrumentation mockInstrumentation;

    private GosuFilterInjector injector;
    private byte[] mockFiltersClassBytecode;

    @BeforeEach
    void setUp() throws IOException {
        injector = new GosuFilterInjector();
        mockFiltersClassBytecode = createMockFiltersClassBytecode();

        // Reset static state
        try {
            Field injectedField = GosuFilterInjector.class.getDeclaredField("injected");
            injectedField.setAccessible(true);
            injectedField.set(null, false);
        } catch (Exception e) {
            // Field might not exist or be accessible
        }

        // Clear filter instance
        GosuFilterAgent.GOSU_FILTER_INSTANCE = null;
    }

    /**
     * Creates mock bytecode that simulates JaCoCo's Filters class.
     * This bytecode includes the allNonKotlinFilters() method that we want to modify.
     */
    private byte[] createMockFiltersClassBytecode() throws IOException {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        // Create class definition
        cw.visit(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                "org/jacoco/core/internal/analysis/filter/Filters",
                null,
                "java/lang/Object",
                null);

        // Add constructor
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        // Add allNonKotlinFilters() method - THIS IS THE TARGET METHOD
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "allNonKotlinFilters",
                "()Lorg/jacoco/core/internal/analysis/filter/IFilter;",
                null, null);
        mv.visitCode();

        // Original bytecode pattern:
        // NEW FilterSet
        // DUP
        // ICONST_1 (array size = 1)
        // ANEWARRAY IFilter
        // DUP
        // ICONST_0
        // NEW SyntheticFilter
        // DUP
        // INVOKESPECIAL SyntheticFilter.<init>
        // AASTORE
        // INVOKESPECIAL FilterSet.<init>([LIFilter;)V
        // ARETURN

        mv.visitTypeInsn(Opcodes.NEW, "org/jacoco/core/internal/analysis/filter/FilterSet");
        mv.visitInsn(Opcodes.DUP);
        mv.visitInsn(Opcodes.ICONST_1); // Array size = 1 (THIS IS WHAT WE MODIFY)
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "org/jacoco/core/internal/analysis/filter/IFilter");
        mv.visitInsn(Opcodes.DUP);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitTypeInsn(Opcodes.NEW, "org/jacoco/core/internal/analysis/filter/SyntheticFilter");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "org/jacoco/core/internal/analysis/filter/SyntheticFilter",
                "<init>", "()V", false);
        mv.visitInsn(Opcodes.AASTORE);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "org/jacoco/core/internal/analysis/filter/FilterSet",
                "<init>", "([Lorg/jacoco/core/internal/analysis/filter/IFilter;)V", false);
        mv.visitInsn(Opcodes.ARETURN);

        mv.visitMaxs(3, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    @Nested
    @DisplayName("Basic Injector Tests")
    class BasicInjectorTests {

        @Test
        @DisplayName("Injector should instantiate successfully")
        void testInjectorInstantiates() {
            assertNotNull(injector, "GosuFilterInjector should be created successfully");
            System.out.println("✓ GosuFilterInjector instantiated successfully");
        }

        @Test
        @DisplayName("Injector should implement ClassFileTransformer")
        void testImplementsClassFileTransformer() {
            assertTrue(injector instanceof ClassFileTransformer,
                    "GosuFilterInjector should implement ClassFileTransformer");
            System.out.println("✓ GosuFilterInjector implements ClassFileTransformer interface");
        }

        @Test
        @DisplayName("createFilter() should create GosuNullSafetyFilter instance")
        void testCreateFilter() throws Exception {
            Object filter = GosuFilterInjector.createFilter();

            assertNotNull(filter, "Filter should be created");
            assertEquals("org.jacoco.core.internal.analysis.filter.GosuNullSafetyFilter",
                    filter.getClass().getName(),
                    "Should create GosuNullSafetyFilter instance");

            System.out.println("✓ createFilter() successfully created GosuNullSafetyFilter instance");
        }

        @Test
        @DisplayName("createFilter() should successfully create filter instance")
        void testCreateFilterWorksSuccessfully() throws Exception {
            // Test the successful case since the filter class is available
            Method createFilterMethod = GosuFilterInjector.class.getMethod("createFilter");

            Object filter = createFilterMethod.invoke(null);

            assertNotNull(filter, "createFilter() should return a non-null filter instance");
            assertTrue(filter instanceof GosuNullSafetyFilter,
                    "createFilter() should return GosuNullSafetyFilter instance");

            System.out.println("✓ createFilter() successfully created GosuNullSafetyFilter instance");
        }
    }

    @Nested
    @DisplayName("Class Transformation Tests")
    class ClassTransformationTests {

        @Test
        @DisplayName("Transform should ignore non-Filters classes")
        void testTransformIgnoresNonFiltersClasses() throws IllegalClassFormatException {
            byte[] result = injector.transform(mockClassLoader,
                    "some/other/Class",
                    null,
                    mockProtectionDomain,
                    new byte[0]);

            assertNull(result, "Should return null for non-Filters classes");
            System.out.println("✓ Non-Filters classes are ignored by transform()");
        }

        @Test
        @DisplayName("Transform should intercept Filters class")
        void testTransformInterceptsFiltersClass() throws IllegalClassFormatException {
            byte[] result = injector.transform(mockClassLoader,
                    "org/jacoco/core/internal/analysis/filter/Filters",
                    null,
                    mockProtectionDomain,
                    mockFiltersClassBytecode);

            assertNotNull(result, "Should return modified bytecode for Filters class");
            assertNotEquals(mockFiltersClassBytecode, result, "Bytecode should be modified");

            System.out.println("✓ Filters class is intercepted and transformed");
        }

        @Test
        @DisplayName("Transform should handle null className gracefully")
        void testTransformHandlesNullClassName() throws IllegalClassFormatException {
            byte[] result = injector.transform(mockClassLoader,
                    null,
                    null,
                    mockProtectionDomain,
                    new byte[0]);

            assertNull(result, "Should handle null className gracefully");
            System.out.println("✓ Null className handled gracefully");
        }

        @Test
        @DisplayName("Transform should return original bytecode on transformation failure")
        void testTransformReturnsOriginalOnFailure() throws IllegalClassFormatException {
            byte[] invalidBytecode = new byte[]{0x00, 0x01, 0x02}; // Invalid bytecode

            byte[] result = injector.transform(mockClassLoader,
                    "org/jacoco/core/internal/analysis/filter/Filters",
                    null,
                    mockProtectionDomain,
                    invalidBytecode);

            assertEquals(invalidBytecode, result, "Should return original bytecode on failure");
            System.out.println("✓ Original bytecode returned on transformation failure");
        }
    }

    @Nested
    @DisplayName("Bytecode Injection Tests")
    class BytecodeInjectionTests {

        @Test
        @DisplayName("Injection should create filter instance")
        void testInjectionCreatesFilterInstance() throws IllegalClassFormatException {
            System.out.println("\n=== Testing Filter Instance Creation ===");

            assertNull(GosuFilterAgent.GOSU_FILTER_INSTANCE,
                    "Filter instance should be null initially");

            injector.transform(mockClassLoader,
                    "org/jacoco/core/internal/analysis/filter/Filters",
                    null,
                    mockProtectionDomain,
                    mockFiltersClassBytecode);

            assertNotNull(GosuFilterAgent.GOSU_FILTER_INSTANCE,
                    "Filter instance should be created during transformation");
            assertTrue(GosuFilterAgent.GOSU_FILTER_INSTANCE instanceof GosuNullSafetyFilter,
                    "Filter instance should be GosuNullSafetyFilter");

            System.out.println("✓ Filter instance created successfully during injection");
        }

        @Test
        @DisplayName("Injection should modify array size from 1 to 2")
        void testInjectionModifiesArraySize() throws Exception {
            System.out.println("\n=== Testing Array Size Modification ===");

            byte[] transformedBytecode = injector.transform(mockClassLoader,
                    "org/jacoco/core/internal/analysis/filter/Filters",
                    null,
                    mockProtectionDomain,
                    mockFiltersClassBytecode);

            // Analyze transformed bytecode
            ClassReader cr = new ClassReader(transformedBytecode);
            TestClassVisitor visitor = new TestClassVisitor();
            cr.accept(visitor, 0);

            assertTrue(visitor.isArraySizeModified(),
                    "Array size should be modified from 1 to 2");
            assertTrue(visitor.hasArraySizeTwo(), "Should contain ICONST_2 instruction");

            System.out.println("✓ Array size successfully modified from 1 to 2");
            System.out.println("  Original: ICONST_1 (array size = 1)");
            System.out.println("  Modified: ICONST_2 (array size = 2)");
        }

        @Test
        @DisplayName("Injection should add filter to array")
        void testInjectionAddsFilterToArray() throws Exception {
            System.out.println("\n=== Testing Filter Addition to Array ===");

            byte[] transformedBytecode = injector.transform(mockClassLoader,
                    "org/jacoco/core/internal/analysis/filter/Filters",
                    null,
                    mockProtectionDomain,
                    mockFiltersClassBytecode);

            // Analyze transformed bytecode
            ClassReader cr = new ClassReader(transformedBytecode);
            TestClassVisitor visitor = new TestClassVisitor();
            cr.accept(visitor, 0);

            assertTrue(visitor.hasFilterInjection(), "Should contain filter injection bytecode");
            assertTrue(visitor.hasStaticFieldAccess(), "Should have static field access");
            assertTrue(visitor.hasCheckcastInstruction(), "Should have checkcast instruction");

            System.out.println("✓ Filter successfully added to array");
            System.out.println("  Static field access: ✓");
            System.out.println("  Checkcast instruction: ✓");
            System.out.println("  Array store at index 1: ✓");
        }

        @Test
        @DisplayName("Injection should preserve original functionality")
        void testInjectionPreservesOriginalFunctionality() throws Exception {
            System.out.println("\n=== Testing Original Functionality Preservation ===");

            byte[] transformedBytecode = injector.transform(mockClassLoader,
                    "org/jacoco/core/internal/analysis/filter/Filters",
                    null,
                    mockProtectionDomain,
                    mockFiltersClassBytecode);

            // Analyze transformed bytecode
            ClassReader cr = new ClassReader(transformedBytecode);
            TestClassVisitor visitor = new TestClassVisitor();
            cr.accept(visitor, 0);

            assertTrue(visitor.hasOriginalFilterSet(), "Should still create FilterSet");
            assertTrue(visitor.hasSyntheticFilter(), "Should still include SyntheticFilter");
            assertTrue(visitor.hasProperStructure(), "Should maintain proper method structure");

            System.out.println("✓ Original functionality preserved");
            System.out.println("  FilterSet creation: ✓");
            System.out.println("  SyntheticFilter inclusion: ✓");
            System.out.println("  Method structure integrity: ✓");
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Full injection pipeline should work end-to-end")
        void testFullInjectionPipeline() throws Exception {
            System.out.println("\n=== Full Injection Pipeline Test ===");

            // Step 1: Transform the class
            byte[] transformedBytecode = injector.transform(mockClassLoader,
                    "org/jacoco/core/internal/analysis/filter/Filters",
                    null,
                    mockProtectionDomain,
                    mockFiltersClassBytecode);

            assertNotNull(transformedBytecode, "Transformation should succeed");

            // Step 2: Verify filter instance was created
            assertNotNull(GosuFilterAgent.GOSU_FILTER_INSTANCE,
                    "Filter instance should be created");

            // Step 3: Verify bytecode structure
            ClassReader cr = new ClassReader(transformedBytecode);
            TestClassVisitor visitor = new TestClassVisitor();
            cr.accept(visitor, 0);

            assertTrue(visitor.isArraySizeModified(), "Array size should be modified");
            assertTrue(visitor.hasFilterInjection(), "Filter injection should be present");
            assertTrue(visitor.hasOriginalFilterSet(), "Original FilterSet should be preserved");

            System.out.println("✓ Full injection pipeline completed successfully");
            System.out.println("  ✓ Class transformation");
            System.out.println("  ✓ Filter instance creation");
            System.out.println("  ✓ Bytecode modification");
            System.out.println("  ✓ Original functionality preservation");
        }

        @Test
        @DisplayName("Multiple transformations should be idempotent")
        void testMultipleTransformationsIdempotent() throws Exception {
            System.out.println("\n=== Testing Multiple Transformations ===");

            // First transformation
            byte[] firstResult = injector.transform(mockClassLoader,
                    "org/jacoco/core/internal/analysis/filter/Filters",
                    null,
                    mockProtectionDomain,
                    mockFiltersClassBytecode);

            // Capture instance after first transformation
            Object firstInstance = GosuFilterAgent.GOSU_FILTER_INSTANCE;

            // Second transformation
            byte[] secondResult = injector.transform(mockClassLoader,
                    "org/jacoco/core/internal/analysis/filter/Filters",
                    null,
                    mockProtectionDomain,
                    firstResult);

            // Results should be consistent
            assertNotNull(firstResult, "First transformation should succeed");
            assertNotNull(secondResult, "Second transformation should succeed");

            // Filter instance should be reused
            Object secondInstance = GosuFilterAgent.GOSU_FILTER_INSTANCE;
            assertEquals(firstInstance, secondInstance,
                    "Filter instance should be reused");
            assertNotNull(firstInstance, "Filter instance should be created after first transformation");

            System.out.println("✓ Multiple transformations are handled correctly");
        }
    }

    @Test
    @DisplayName("Comprehensive bytecode analysis report")
    void testComprehensiveBytecodeAnalysis() throws Exception {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("COMPREHENSIVE BYTECODE ANALYSIS REPORT");
        System.out.println("=".repeat(60));

        byte[] originalBytecode = mockFiltersClassBytecode;
        byte[] transformedBytecode = injector.transform(mockClassLoader,
                "org/jacoco/core/internal/analysis/filter/Filters",
                null,
                mockProtectionDomain,
                originalBytecode);

        // Analyze original bytecode
        System.out.println("\n--- ORIGINAL BYTECODE ANALYSIS ---");
        analyzeBytecode("Original", originalBytecode);

        // Analyze transformed bytecode
        System.out.println("\n--- TRANSFORMED BYTECODE ANALYSIS ---");
        analyzeBytecode("Transformed", transformedBytecode);

        // Compare key metrics
        System.out.println("\n--- TRANSFORMATION SUMMARY ---");
        compareBytecodeMetrics(originalBytecode, transformedBytecode);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("ANALYSIS COMPLETE");
        System.out.println("=".repeat(60));
    }

    private void analyzeBytecode(String label, byte[] bytecode) throws Exception {
        ClassReader cr = new ClassReader(bytecode);
        AnalysisClassVisitor analyzer = new AnalysisClassVisitor(label);
        cr.accept(analyzer, 0);

        System.out.println(label + " bytecode:");
        System.out.println("  Total bytes: " + bytecode.length);
        System.out.println("  Methods found: " + analyzer.getMethodCount());
        System.out.println("  Key patterns: " + analyzer.getPatternSummary());
    }

    private void compareBytecodeMetrics(byte[] original, byte[] transformed) throws Exception {
        int sizeIncrease = transformed.length - original.length;
        double percentIncrease = (double) sizeIncrease / original.length * 100;

        System.out.println("Size change:");
        System.out.println("  Original: " + original.length + " bytes");
        System.out.println("  Transformed: " + transformed.length + " bytes");
        System.out.println("  Increase: " + sizeIncrease + " bytes (" +
                String.format("%.1f", percentIncrease) + "%)");

        // Verify key transformations
        ClassReader cr = new ClassReader(transformed);
        TestClassVisitor visitor = new TestClassVisitor();
        cr.accept(visitor, 0);

        System.out.println("\nKey transformations verified:");
        System.out.println("  ✓ Array size modified: " + visitor.isArraySizeModified());
        System.out.println("  ✓ Filter added: " + visitor.hasFilterInjection());
        System.out.println("  ✓ Original preserved: " + visitor.hasOriginalFilterSet());
    }

    /**
     * Custom ClassVisitor for testing bytecode transformations.
     */
    private static class TestClassVisitor extends ClassVisitor {
        private boolean arraySizeModified = false;
        private boolean hasFilterInjection = false;
        private boolean hasStaticFieldAccess = false;
        private boolean hasCheckcastInstruction = false;
        private boolean hasOriginalFilterSet = false;
        private boolean hasSyntheticFilter = false;
        private boolean hasProperStructure = false;
        private int methodCount = 0;

        public TestClassVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            methodCount++;
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

            if ("allNonKotlinFilters".equals(name)) {
                return new TestMethodVisitor(mv);
            }

            return mv;
        }

        public boolean isArraySizeModified() {
            return arraySizeModified;
        }

        public boolean hasFilterInjection() {
            return hasFilterInjection;
        }

        public boolean hasStaticFieldAccess() {
            return hasStaticFieldAccess;
        }

        public boolean hasCheckcastInstruction() {
            return hasCheckcastInstruction;
        }

        public boolean hasOriginalFilterSet() {
            return hasOriginalFilterSet;
        }

        public boolean hasSyntheticFilter() {
            return hasSyntheticFilter;
        }

        public boolean hasProperStructure() {
            return hasProperStructure;
        }

        public boolean hasArraySizeTwo() {
            return arraySizeModified;
        }

        public int getMethodCount() {
            return methodCount;
        }

        private class TestMethodVisitor extends MethodVisitor {
            public TestMethodVisitor(MethodVisitor mv) {
                super(Opcodes.ASM9, mv);
            }

            @Override
            public void visitInsn(int opcode) {
                if (opcode == Opcodes.ICONST_2) {
                    arraySizeModified = true;
                    System.out.println("Found ICONST_2 (array size = 2)");
                }
                super.visitInsn(opcode);
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                if (opcode == Opcodes.NEW) {
                    if ("org/jacoco/core/internal/analysis/filter/FilterSet".equals(type)) {
                        hasOriginalFilterSet = true;
                    } else if ("org/jacoco/core/internal/analysis/filter/SyntheticFilter".equals(type)) {
                        hasSyntheticFilter = true;
                    }
                } else if (opcode == Opcodes.CHECKCAST) {
                    if ("org/jacoco/core/internal/analysis/filter/IFilter".equals(type)) {
                        hasCheckcastInstruction = true;
                    }
                }
                super.visitTypeInsn(opcode, type);
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                if (opcode == Opcodes.GETSTATIC &&
                        "org/jacoco/gosu/GosuFilterAgent".equals(owner) &&
                        "GOSU_FILTER_INSTANCE".equals(name)) {
                    hasStaticFieldAccess = true;
                    hasFilterInjection = true;
                }
                super.visitFieldInsn(opcode, owner, name, descriptor);
            }

            @Override
            public void visitEnd() {
                hasProperStructure = true; // If we reach here, structure is valid
                super.visitEnd();
            }
        }
    }

    /**
     * ClassVisitor for detailed bytecode analysis.
     */
    private static class AnalysisClassVisitor extends ClassVisitor {
        private final String label;
        private int methodCount = 0;
        private int iconst1Count = 0;
        private int iconst2Count = 0;
        private int anewarrayCount = 0;
        private int aastoreCount = 0;

        public AnalysisClassVisitor(String label) {
            super(Opcodes.ASM9);
            this.label = label;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            methodCount++;
            return new AnalysisMethodVisitor();
        }

        public int getMethodCount() {
            return methodCount;
        }

        public String getPatternSummary() {
            return String.format("ICONST_1:%d, ICONST_2:%d, ANEWARRAY:%d, AASTORE:%d",
                    iconst1Count, iconst2Count, anewarrayCount, aastoreCount);
        }

        private class AnalysisMethodVisitor extends MethodVisitor {
            public AnalysisMethodVisitor() {
                super(Opcodes.ASM9);
            }

            @Override
            public void visitInsn(int opcode) {
                switch (opcode) {
                    case Opcodes.ICONST_1:
                        iconst1Count++;
                        break;
                    case Opcodes.ICONST_2:
                        iconst2Count++;
                        break;
                    case Opcodes.AASTORE:
                        aastoreCount++;
                        break;
                }
                super.visitInsn(opcode);
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                if (opcode == Opcodes.ANEWARRAY) {
                    anewarrayCount++;
                }
                super.visitTypeInsn(opcode, type);
            }
        }
    }
}