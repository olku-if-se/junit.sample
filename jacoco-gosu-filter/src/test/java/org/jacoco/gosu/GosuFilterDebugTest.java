package org.jacoco.gosu;

import org.jacoco.core.internal.analysis.filter.GosuNullSafetyFilter;
import org.jacoco.core.internal.analysis.filter.IFilterContext;
import org.jacoco.core.internal.analysis.filter.IFilterOutput;
import org.jacoco.core.internal.analysis.filter.Replacements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Debug test to understand why GosuNullSafetyFilter is not working.
 */
public class GosuFilterDebugTest {

    private ClassNode policyPeriodClass;

    @BeforeEach
    void setUp() throws IOException {
        policyPeriodClass = loadPolicyPeriodEnhancementClass();
    }

    private ClassNode loadPolicyPeriodEnhancementClass() throws IOException {
        String classResource = "/bytecode/PolicyPeriodEnhancement.class";
        
        try (InputStream inputStream = getClass().getResourceAsStream(classResource)) {
            if (inputStream == null) {
                throw new IOException("PolicyPeriodEnhancement.class not found in test resources: " + classResource);
            }
            
            byte[] bytecode = inputStream.readAllBytes();
            ClassReader reader = new ClassReader(bytecode);
            ClassNode classNode = new ClassNode();
            reader.accept(classNode, 0);
            return classNode;
        }
    }

    @Test
    void debugSimpleMethod() {
        System.out.println("DEBUG: Analyzing getFirstPeriodInTermCreateTime_Ext method");
        
        MethodNode method = findMethod("getFirstPeriodInTermCreateTime_Ext");
        if (method == null) {
            System.out.println("Method not found!");
            return;
        }
        
        System.out.println("Method instructions:");
        printInstructions(method);
        
        System.out.println("\nLooking for ALOAD instructions:");
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() == Opcodes.ALOAD) {
                System.out.println("Found ALOAD at position: " + method.instructions.indexOf(instruction));
                System.out.println("Var index: " + ((org.objectweb.asm.tree.VarInsnNode) instruction).var);
                
                // Try to match pattern manually
                if (manualMatchPattern(instruction, method)) {
                    System.out.println("MANUAL MATCH: Pattern found!");
                } else {
                    System.out.println("MANUAL MATCH: Pattern NOT found");
                }
            }
        }
        
        // Apply filter
        System.out.println("\nApplying GosuNullSafetyFilter:");
        GosuNullSafetyFilter filter = new GosuNullSafetyFilter();
        DebugFilterOutput output = new DebugFilterOutput();
        MockFilterContext context = new MockFilterContext();
        filter.filter(method, context, output);
        
        System.out.println("Filter ignored ranges: " + output.getIgnoredRanges().size());
    }
    
    private boolean manualMatchPattern(AbstractInsnNode aload, MethodNode method) {
        System.out.println("  Checking pattern starting from ALOAD...");
        
        AbstractInsnNode current = aload;
        System.out.println("  1. ALOAD - ✓");
        
        // Skip non-opcode instructions
        current = getNextRealInstruction(current);
        if (current == null || current.getOpcode() != Opcodes.IFNONNULL) {
            System.out.println("  2. Expected IFNONNULL, got: " + (current != null ? getOpcodeName(current.getOpcode()) : "null"));
            return false;
        }
        System.out.println("  2. IFNONNULL - ✓");
        
        current = getNextRealInstruction(current);
        if (current == null || current.getOpcode() != Opcodes.ACONST_NULL) {
            System.out.println("  3. Expected ACONST_NULL, got: " + (current != null ? getOpcodeName(current.getOpcode()) : "null"));
            return false;
        }
        System.out.println("  3. ACONST_NULL - ✓");
        
        current = getNextRealInstruction(current);
        if (current == null || current.getOpcode() != Opcodes.CHECKCAST) {
            System.out.println("  4. Expected CHECKCAST, got: " + (current != null ? getOpcodeName(current.getOpcode()) : "null"));
            return false;
        }
        System.out.println("  4. CHECKCAST - ✓");
        
        current = getNextRealInstruction(current);
        if (current == null || current.getOpcode() != Opcodes.GOTO) {
            System.out.println("  5. Expected GOTO, got: " + (current != null ? getOpcodeName(current.getOpcode()) : "null"));
            return false;
        }
        System.out.println("  5. GOTO - ✓");
        
        return true;
    }
    
    private AbstractInsnNode getNextRealInstruction(AbstractInsnNode instruction) {
        AbstractInsnNode current = instruction.getNext();
        while (current != null && (current.getType() == AbstractInsnNode.FRAME ||
                                  current.getType() == AbstractInsnNode.LABEL ||
                                  current.getType() == AbstractInsnNode.LINE)) {
            current = current.getNext();
        }
        return current;
    }
    
    private void printInstructions(MethodNode method) {
        int offset = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getType() == AbstractInsnNode.LABEL || 
                instruction.getType() == AbstractInsnNode.FRAME ||
                instruction.getType() == AbstractInsnNode.LINE) {
                continue;
            }
            
            String opcodeName = getOpcodeName(instruction.getOpcode());
            System.out.printf("%3d: %-15s", offset, opcodeName);
            
            if (instruction.getOpcode() == Opcodes.ALOAD) {
                org.objectweb.asm.tree.VarInsnNode varNode = (org.objectweb.asm.tree.VarInsnNode) instruction;
                System.out.printf(" (var=%d)", varNode.var);
            } else if (instruction.getOpcode() == Opcodes.IFNONNULL || 
                      instruction.getOpcode() == Opcodes.IFNULL) {
                org.objectweb.asm.tree.JumpInsnNode jumpNode = (org.objectweb.asm.tree.JumpInsnNode) instruction;
                System.out.printf(" (-> label %d)", method.instructions.indexOf(jumpNode.label));
            }
            
            System.out.println();
            offset++;
        }
    }
    
    private String getOpcodeName(int opcode) {
        switch (opcode) {
            case Opcodes.IFNONNULL: return "ifnonnull";
            case Opcodes.IFNULL: return "ifnull";
            case Opcodes.ACONST_NULL: return "aconst_null";
            case Opcodes.CHECKCAST: return "checkcast";
            case Opcodes.GOTO: return "goto";
            case Opcodes.ALOAD: return "aload";
            case Opcodes.ARETURN: return "areturn";
            case Opcodes.INVOKEVIRTUAL: return "invokevirtual";
            default: return "opcode_" + opcode;
        }
    }

    private MethodNode findMethod(String name) {
        for (MethodNode method : policyPeriodClass.methods) {
            if (method.name.equals(name)) {
                return method;
            }
        }
        return null;
    }

    private static class DebugFilterOutput implements IFilterOutput {
        private final List<String> ignoredRanges = new ArrayList<>();

        @Override
        public void ignore(AbstractInsnNode fromInclusive, AbstractInsnNode toInclusive) {
            ignoredRanges.add("IGNORE: " + getOpcodeName(fromInclusive.getOpcode()) + " -> " + getOpcodeName(toInclusive.getOpcode()));
        }

        @Override
        public void merge(AbstractInsnNode i1, AbstractInsnNode i2) {
        }

        @Override
        public void replaceBranches(AbstractInsnNode source, Replacements replacements) {
        }

        private static String getOpcodeName(int opcode) {
            switch (opcode) {
                case Opcodes.IFNONNULL: return "ifnonnull";
                case Opcodes.IFNULL: return "ifnull";
                case Opcodes.ACONST_NULL: return "aconst_null";
                case Opcodes.CHECKCAST: return "checkcast";
                case Opcodes.GOTO: return "goto";
                case Opcodes.ALOAD: return "aload";
                default: return "opcode_" + opcode;
            }
        }

        public List<String> getIgnoredRanges() {
            return ignoredRanges;
        }
    }

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