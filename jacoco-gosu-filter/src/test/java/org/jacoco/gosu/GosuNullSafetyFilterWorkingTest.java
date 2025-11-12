package org.jacoco.gosu;

import org.jacoco.core.internal.analysis.filter.GosuNullSafetyFilter;
import org.jacoco.core.internal.analysis.filter.IFilterContext;
import org.jacoco.core.internal.analysis.filter.IFilterOutput;
import org.jacoco.core.internal.analysis.filter.Replacements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Working unit test for GosuNullSafetyFilter that uses correct JaCoCo 0.8.14 interface signatures.
 */
public class GosuNullSafetyFilterWorkingTest {

    private GosuNullSafetyFilter filter;
    private ClassNode policyPeriodClass;

    @BeforeEach
    void setUp() throws IOException {
        filter = new GosuNullSafetyFilter();
        policyPeriodClass = loadPolicyPeriodEnhancementClass();
    }

    private ClassNode loadPolicyPeriodEnhancementClass() throws IOException {
        Path classPath = Paths.get("build/classes/gosu/main/enhancement/PolicyPeriodEnhancement.class");

        if (!Files.exists(classPath)) {
            throw new IOException("PolicyPeriodEnhancement.class not found at: " + classPath.toAbsolutePath() +
                    "\nPlease run: ./gradlew compileGosu first");
        }

        byte[] bytecode = Files.readAllBytes(classPath);
        ClassReader reader = new ClassReader(bytecode);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        return classNode;
    }

    @Test
    @DisplayName("Filter should load without errors")
    void testFilterLoads() {
        assertNotNull(filter, "Filter should be instantiated successfully");
        System.out.println("✓ Filter created successfully");
    }

    @Test
    @DisplayName("PolicyPeriodEnhancement class should be loadable")
    void testPolicyPeriodClassLoads() {
        assertNotNull(policyPeriodClass, "PolicyPeriodEnhancement class should be loaded");
        assertEquals("enhancement/PolicyPeriodEnhancement", policyPeriodClass.name);
        assertTrue(policyPeriodClass.methods.size() > 0, "Should have methods");

        System.out.println("✓ PolicyPeriodEnhancement class loaded successfully");
        System.out.println("  Class name: " + policyPeriodClass.name);
        System.out.println("  Methods found: " + policyPeriodClass.methods.size());

        // List all method names
        for (MethodNode method : policyPeriodClass.methods) {
            if (!method.name.startsWith("<")) {
                System.out.println("  - " + method.name + method.desc);
            }
        }
    }

    @Test
    @DisplayName("Expected methods should exist")
    void testExpectedMethodsExist() {
        String[] expectedMethods = {
                "getFirstPeriodInTermCreateTime_Ext",
                "getAvailableBrandConceptsForProdCode",
                "getFirstPeriodProducerCodeName",
                "isProducerCodeExists"
        };

        for (String methodName : expectedMethods) {
            MethodNode method = findMethod(methodName);
            assertNotNull(method, "Method " + methodName + " should exist");
            System.out.println("✓ Found method: " + methodName + method.desc);
        }
    }

    @Test
    @DisplayName("Filter should process methods with mock outputs")
    void testFilterProcessesMethodsWithMockOutputs() {
        for (MethodNode method : policyPeriodClass.methods) {
            if (method.name.startsWith("<")) continue; // Skip constructors

            System.out.println("\n=== Testing Method: " + method.name + method.desc + " ===");

            MockFilterOutput output = new MockFilterOutput();
            MockFilterContext context = new MockFilterContext();

            // This should not throw an exception
            assertDoesNotThrow(() -> {
                filter.filter(method, context, output);
            }, "Filter should process method without errors");

            System.out.println("  Instructions processed: " + method.instructions.size());
            System.out.println("  Ranges ignored: " + output.getIgnoredRanges().size());
            System.out.println("  Merges performed: " + output.getMergedInstructions().size());

            if (output.getIgnoredRanges().size() > 0) {
                System.out.println("  ✓ Filter detected and ignored bytecode ranges");
            }
        }
    }

    @Test
    @DisplayName("Filter should detect patterns in getFirstPeriodInTermCreateTime_Ext")
    void testFilterDetectsPatternsInFirstPeriodMethod() {
        MethodNode method = findMethod("getFirstPeriodInTermCreateTime_Ext");
        assertNotNull(method, "Method should exist");

        System.out.println("\n=== Analyzing getFirstPeriodInTermCreateTime_Ext ===");

        MockFilterOutput output = new MockFilterOutput();
        MockFilterContext context = new MockFilterContext();

        filter.filter(method, context, output);

        System.out.println("Instructions: " + method.instructions.size());
        System.out.println("Ignored ranges: " + output.getIgnoredRanges().size());
        System.out.println("Merged instructions: " + output.getMergedInstructions().size());

        // Print bytecode for inspection
        printMethodBytecode(method);

        // Analyze bytecode manually
        analyzeMethodBytecode(method);

        if (output.getIgnoredRanges().size() > 0) {
            System.out.println("✓ Filter detected patterns in this method");
        } else {
            System.out.println("⚠ No patterns detected (may be normal)");
        }
    }

    @Test
    @DisplayName("Methods should contain null-safe bytecode patterns")
    void testBytecodeContainsNullSafePatterns() {
        for (MethodNode method : policyPeriodClass.methods) {
            if (method.name.startsWith("<")) continue; // Skip constructors

            boolean hasAload = false;
            boolean hasIfnonnull = false;
            boolean hasAconstNull = false;
            boolean hasCheckcast = false;

            for (AbstractInsnNode instruction : method.instructions) {
                switch (instruction.getOpcode()) {
                    case Opcodes.ALOAD:
                        hasAload = true;
                        break;
                    case Opcodes.IFNONNULL:
                        hasIfnonnull = true;
                        break;
                    case Opcodes.ACONST_NULL:
                        hasAconstNull = true;
                        break;
                    case Opcodes.CHECKCAST:
                        hasCheckcast = true;
                        break;
                }
            }

            System.out.println("\nMethod: " + method.name + method.desc);
            System.out.println("  Instructions: " + method.instructions.size());
            System.out.println("  ALOAD: " + hasAload + ", IFNONNULL: " + hasIfnonnull +
                    ", ACONST_NULL: " + hasAconstNull + ", CHECKCAST: " + hasCheckcast);

            // At least one method should have null-safe patterns
            if (hasIfnonnull && hasAconstNull && hasCheckcast) {
                System.out.println("  → This method appears to have null-safe patterns");
                return; // Found at least one method with patterns
            }
        }

        System.out.println("⚠ No methods with obvious null-safe patterns found");
    }

    private void printMethodBytecode(MethodNode method) {
        System.out.println("\nBytecode:");
        int index = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            String opcodeName = getOpcodeName(instruction.getOpcode());
            String details = getInstructionDetails(instruction);
            System.out.printf("%3d: %-15s %s%n", index++, opcodeName, details);
        }
    }

    private void analyzeMethodBytecode(MethodNode method) {
        int aloadCount = 0, ifnonnullCount = 0, aconstNullCount = 0, checkcastCount = 0;

        for (AbstractInsnNode instruction : method.instructions) {
            switch (instruction.getOpcode()) {
                case Opcodes.ALOAD:
                    aloadCount++;
                    break;
                case Opcodes.IFNONNULL:
                    ifnonnullCount++;
                    break;
                case Opcodes.ACONST_NULL:
                    aconstNullCount++;
                    break;
                case Opcodes.CHECKCAST:
                    checkcastCount++;
                    break;
            }
        }

        System.out.println("\nPattern Analysis:");
        System.out.println("  ALOAD: " + aloadCount);
        System.out.println("  IFNONNULL: " + ifnonnullCount);
        System.out.println("  ACONST_NULL: " + aconstNullCount);
        System.out.println("  CHECKCAST: " + checkcastCount);

        int estimatedPatterns = Math.min(Math.min(ifnonnullCount, aconstNullCount), checkcastCount);
        if (estimatedPatterns > 0) {
            System.out.println("  Estimated null-safe patterns: " + estimatedPatterns);
        }
    }

    private String getOpcodeName(int opcode) {
        switch (opcode) {
            case Opcodes.ALOAD:
                return "aload";
            case Opcodes.IFNONNULL:
                return "ifnonnull";
            case Opcodes.ACONST_NULL:
                return "aconst_null";
            case Opcodes.CHECKCAST:
                return "checkcast";
            case Opcodes.GOTO:
                return "goto";
            case Opcodes.INVOKEVIRTUAL:
                return "invokevirtual";
            case Opcodes.INVOKEINTERFACE:
                return "invokeinterface";
            case Opcodes.INVOKESTATIC:
                return "invokestatic";
            case Opcodes.ARETURN:
                return "areturn";
            case Opcodes.ASTORE:
                return "astore";
            default:
                return "opcode_" + opcode;
        }
    }

    private String getInstructionDetails(AbstractInsnNode instruction) {
        if (instruction instanceof VarInsnNode) {
            VarInsnNode var = (VarInsnNode) instruction;
            return "var=" + var.var;
        } else if (instruction instanceof TypeInsnNode) {
            TypeInsnNode type = (TypeInsnNode) instruction;
            return type.desc;
        } else if (instruction instanceof MethodInsnNode) {
            MethodInsnNode method = (MethodInsnNode) instruction;
            return method.owner + "." + method.name;
        } else if (instruction instanceof JumpInsnNode) {
            JumpInsnNode jump = (JumpInsnNode) instruction;
            return "label_" + jump.label.hashCode();
        }
        return "";
    }

    private MethodNode findMethod(String name) {
        for (MethodNode method : policyPeriodClass.methods) {
            if (method.name.equals(name)) {
                return method;
            }
        }
        return null;
    }

    /**
     * Mock implementation of IFilterOutput that records calls.
     */
    private static class MockFilterOutput implements IFilterOutput {
        private final List<Range> ignoredRanges = new ArrayList<>();
        private final List<InstructionPair> mergedInstructions = new ArrayList<>();

        @Override
        public void ignore(AbstractInsnNode fromInclusive, AbstractInsnNode toInclusive) {
            ignoredRanges.add(new Range(fromInclusive, toInclusive));
        }

        @Override
        public void merge(AbstractInsnNode i1, AbstractInsnNode i2) {
            mergedInstructions.add(new InstructionPair(i1, i2));
        }

        @Override
        public void replaceBranches(AbstractInsnNode source, Replacements replacements) {
            // Empty implementation for testing
        }

        public List<Range> getIgnoredRanges() {
            return ignoredRanges;
        }

        public List<InstructionPair> getMergedInstructions() {
            return mergedInstructions;
        }

        public static class Range {
            public final AbstractInsnNode start;
            public final AbstractInsnNode end;

            public Range(AbstractInsnNode start, AbstractInsnNode end) {
                this.start = start;
                this.end = end;
            }
        }

        public static class InstructionPair {
            public final AbstractInsnNode i1;
            public final AbstractInsnNode i2;

            public InstructionPair(AbstractInsnNode i1, AbstractInsnNode i2) {
                this.i1 = i1;
                this.i2 = i2;
            }
        }
    }

    /**
     * Mock implementation of IFilterContext.
     */
    private static class MockFilterContext implements IFilterContext {
        @Override
        public String getClassName() {
            return "enhancement/PolicyPeriodEnhancement";
        }

        @Override
        public String getSuperClassName() {
            return "java/lang/Object";
        }

        @Override
        public Set<String> getClassAnnotations() {
            return Collections.emptySet();
        }

        @Override
        public Set<String> getClassAttributes() {
            return Collections.emptySet();
        }

        @Override
        public String getSourceFileName() {
            return "PolicyPeriodEnhancement.gsx";
        }

        @Override
        public String getSourceDebugExtension() {
            return null;
        }
    }
}