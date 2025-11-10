package org.jacoco.gosu;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple unit test for GosuFilterInjector without Mockito to avoid Java 21 compatibility issues.
 */
@DisplayName("GosuFilterInjector Simple Tests")
public class GosuFilterInjectorSimpleTest {

    private GosuFilterInjector injector;

    @BeforeEach
    void setUp() {
        injector = new GosuFilterInjector();
        // Reset static state
        GosuFilterAgent.GOSU_FILTER_INSTANCE = null;
    }

    @Test
    @DisplayName("Injector should instantiate successfully")
    void testInjectorInstantiates() {
        assertNotNull(injector, "GosuFilterInjector should be created successfully");
        System.out.println("✓ GosuFilterInjector instantiated successfully");
    }

    @Test
    @DisplayName("createFilter() should create GosuNullSafetyFilter instance")
    void testCreateFilter() throws Exception {
        Object filter = GosuFilterInjector.createFilter();

        assertNotNull(filter, "Filter should be created");
        assertEquals("org.jacoco.gosu.GosuNullSafetyFilter",
                    filter.getClass().getName(),
                    "Should create GosuNullSafetyFilter instance");

        System.out.println("✓ createFilter() successfully created GosuNullSafetyFilter instance");
        System.out.println("  Filter type: " + filter.getClass().getSimpleName());
    }

    @Test
    @DisplayName("Transform should ignore non-Filters classes")
    void testTransformIgnoresNonFiltersClasses() {
        byte[] result = injector.transform(getClass().getClassLoader(),
                                         "some/other/Class",
                                         null,
                                         null,
                                         new byte[0]);

        assertNull(result, "Should return null for non-Filters classes");
        System.out.println("✓ Non-Filters classes are ignored by transform()");
    }

    @Test
    @DisplayName("Transform should handle null className gracefully")
    void testTransformHandlesNullClassName() {
        byte[] result = injector.transform(getClass().getClassLoader(),
                                         null,
                                         null,
                                         null,
                                         new byte[0]);

        assertNull(result, "Should handle null className gracefully");
        System.out.println("✓ Null className handled gracefully");
    }

    @Test
    @DisplayName("Transform should return original bytecode on transformation failure")
    void testTransformReturnsOriginalOnFailure() {
        byte[] invalidBytecode = new byte[]{0x00, 0x01, 0x02}; // Invalid bytecode

        byte[] result = injector.transform(getClass().getClassLoader(),
                                         "org/jacoco/core/internal/analysis/filter/Filters",
                                         null,
                                         null,
                                         invalidBytecode);

        assertEquals(invalidBytecode, result, "Should return original bytecode on failure");
        System.out.println("✓ Original bytecode returned on transformation failure");
    }

    @Test
    @DisplayName("Transform should intercept Filters class with valid bytecode")
    void testTransformInterceptsFiltersClass() throws Exception {
        byte[] mockBytecode = createMockFiltersBytecode();

        System.out.println("Testing transformation with mock bytecode: " + mockBytecode.length + " bytes");

        byte[] result = injector.transform(getClass().getClassLoader(),
                                         "org/jacoco/core/internal/analysis/filter/Filters",
                                         null,
                                         null,
                                         mockBytecode);

        assertNotNull(result, "Should return modified bytecode for Filters class");
        assertNotEquals(mockBytecode, result, "Bytecode should be modified");

        // Check if filter instance was created
        assertNotNull(GosuFilterAgent.GOSU_FILTER_INSTANCE,
                      "Filter instance should be created during transformation");
        assertTrue(GosuFilterAgent.GOSU_FILTER_INSTANCE instanceof GosuNullSafetyFilter,
                  "Filter instance should be GosuNullSafetyFilter");

        System.out.println("✓ Filters class is intercepted and transformed");
        System.out.println("  Original size: " + mockBytecode.length + " bytes");
        System.out.println("  Modified size: " + result.length + " bytes");
        System.out.println("  Filter instance created: " + GosuFilterAgent.GOSU_FILTER_INSTANCE.getClass().getSimpleName());
    }

    @Test
    @DisplayName("Should verify filter instance creation and storage")
    void testFilterInstanceCreation() throws Exception {
        assertNull(GosuFilterAgent.GOSU_FILTER_INSTANCE,
                  "Filter instance should be null initially");

        // Create mock bytecode and transform
        byte[] mockBytecode = createMockFiltersBytecode();
        injector.transform(getClass().getClassLoader(),
                         "org/jacoco/core/internal/analysis/filter/Filters",
                         null,
                         null,
                         mockBytecode);

        assertNotNull(GosuFilterAgent.GOSU_FILTER_INSTANCE,
                      "Filter instance should be created during transformation");
        assertTrue(GosuFilterAgent.GOSU_FILTER_INSTANCE instanceof GosuNullSafetyFilter,
                  "Filter instance should be GosuNullSafetyFilter");

        System.out.println("✓ Filter instance creation and storage verified");
        System.out.println("  Instance type: " + GosuFilterAgent.GOSU_FILTER_INSTANCE.getClass().getName());
    }

    /**
     * Creates a minimal mock bytecode that simulates a Filters class.
     * This is a very basic implementation that creates valid class bytecode.
     */
    private byte[] createMockFiltersBytecode() {
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

        // Original bytecode pattern that should be modified:
        // NEW FilterSet
        // DUP
        // ICONST_1 (array size = 1) - THIS SHOULD BE CHANGED TO ICONST_2
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

    @Test
    @DisplayName("Should analyze mock bytecode structure")
    void testAnalyzeMockBytecodeStructure() throws Exception {
        byte[] mockBytecode = createMockFiltersBytecode();

        // Analyze the created bytecode
        ClassReader cr = new ClassReader(mockBytecode);
        ClassNode classNode = new ClassNode();
        cr.accept(classNode, 0);

        System.out.println("Mock bytecode analysis:");
        System.out.println("  Class name: " + classNode.name);
        System.out.println("  Methods: " + classNode.methods.size());

        boolean foundTargetMethod = false;
        for (org.objectweb.asm.tree.MethodNode method : classNode.methods) {
            System.out.println("    Method: " + method.name + method.desc);
            if ("allNonKotlinFilters".equals(method.name)) {
                foundTargetMethod = true;
                System.out.println("      Instructions: " + method.instructions.size());
                System.out.println("      ✓ Target method found");
            }
        }

        assertTrue(foundTargetMethod, "Should contain allNonKotlinFilters method");
        System.out.println("✓ Mock bytecode structure analysis completed");
    }
}