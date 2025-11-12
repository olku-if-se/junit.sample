# PolicyPeriodEnhancement Branch Comparison Test Implementation Plan

## Overview

Create a comprehensive unit test that loads `PolicyPeriodEnhancement.class` from test resources and applies `GosuNullSafetyFilter` to demonstrate branch filtering effectiveness with detailed comparison tables.

## Test Class Structure

### File: `PolicyPeriodEnhancementBranchComparisonTest.java`

```java
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
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
```

## Key Components

### 1. Class Loading Method
```java
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
```

### 2. Data Classes for Analysis

#### MethodAnalysis
```java
private static class MethodAnalysis {
    final String methodName;
    final int originalBranches;
    final int filteredBranches;
    final int remainingBranches;
    final double reductionPercentage;
    final List<BranchTrackingOutput.Range> ignoredRanges;
    
    // Constructor and methods
}
```

#### DetailedAnalysis
```java
private static class DetailedAnalysis {
    final String methodName;
    final List<BranchInfo> originalBranches;
    final List<BranchInfo> filteredBranches;
    final List<BranchTrackingOutput.Range> ignoredRanges;
    
    // Constructor and methods
}
```

#### BranchInfo
```java
private static class BranchInfo {
    final AbstractInsnNode instruction;
    final String opcodeName;
    final String type;
    
    // Constructor and methods
}
```

### 3. Branch Tracking Output
```java
private static class BranchTrackingOutput implements IFilterOutput {
    private final List<Range> ignoredRanges = new ArrayList<>();

    @Override
    public void ignore(AbstractInsnNode fromInclusive, AbstractInsnNode toInclusive) {
        String reason = determineReason(fromInclusive, toInclusive);
        ignoredRanges.add(new Range(fromInclusive, toInclusive, reason));
    }

    @Override
    public void merge(AbstractInsnNode i1, AbstractInsnNode i2) {
        // Track merges if needed
    }

    @Override
    public void replaceBranches(AbstractInsnNode source, Replacements replacements) {
        // Track branch replacements if needed
    }

    private String determineReason(AbstractInsnNode from, AbstractInsnNode to) {
        if (from.getOpcode() == Opcodes.ALOAD && to.getOpcode() == Opcodes.IFNONNULL) {
            return "null-check";
        } else if (from.getOpcode() == Opcodes.ACONST_NULL) {
            return "null-return";
        }
        return "null-safety";
    }

    public List<Range> getIgnoredRanges() {
        return ignoredRanges;
    }

    public static class Range {
        public final AbstractInsnNode start;
        public final AbstractInsnNode end;
        public final String reason;

        public Range(AbstractInsnNode start, AbstractInsnNode end, String reason) {
            this.start = start;
            this.end = end;
            this.reason = reason;
        }
    }
}
```

### 4. Mock Filter Context
```java
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
```

## Test Methods

### 1. Main Comparison Table Test
```java
@Test
@DisplayName("Comprehensive branch comparison table for PolicyPeriodEnhancement")
void testBranchComparisonTable() {
    System.out.println("\n" + "=".repeat(80));
    System.out.println("JACOCO vs GOSU FILTER BRANCH ANALYSIS");
    System.out.println("PolicyPeriodEnhancement.class Comparison");
    System.out.println("=".repeat(80));

    List<MethodAnalysis> methodAnalyses = new ArrayList<>();
    int totalOriginalBranches = 0;
    int totalFilteredBranches = 0;

    // Analyze each method
    for (MethodNode method : policyPeriodClass.methods) {
        if (method.name.startsWith("<")) continue; // Skip constructors

        MethodAnalysis analysis = analyzeMethod(method);
        methodAnalyses.add(analysis);
        
        totalOriginalBranches += analysis.originalBranches;
        totalFilteredBranches += analysis.filteredBranches;
    }

    // Print comparison table
    printComparisonTable(methodAnalyses, totalOriginalBranches, totalFilteredBranches);

    // Verify filter effectiveness
    verifyFilterEffectiveness(totalOriginalBranches, totalFilteredBranches, methodAnalyses);
}
```

### 2. Detailed Method Analysis Test
```java
@Test
@DisplayName("Detailed method-by-method branch analysis")
void testDetailedMethodAnalysis() {
    System.out.println("\n" + "=".repeat(80));
    System.out.println("DETAILED METHOD ANALYSIS");
    System.out.println("=".repeat(80));

    for (MethodNode method : policyPeriodClass.methods) {
        if (method.name.startsWith("<")) continue;

        System.out.println("\n" + "-".repeat(60));
        System.out.println("METHOD: " + getReadableMethodName(method));
        System.out.println("-".repeat(60));

        DetailedAnalysis analysis = performDetailedAnalysis(method);
        printDetailedAnalysis(analysis);
    }
}
```

## Output Formats

### 1. Comparison Table Format
```
JACOCO vs GOSU FILTER BRANCH ANALYSIS
=====================================

Method Name                    | JaCoCo Branches | Filtered | Remaining | Reduction % | Status
-------------------------------|----------------|----------|-----------|-------------|--------
getFirstPeriodInTermCreateTime |        2       |    1     |     1     |    50.0%    | ✅ GOOD
getAvailableBrandConcepts...   |       12       |    6     |     6     |    50.0%    | ✅ EXCELLENT
getFirstPeriodProducerCodeName |        8       |    4     |     4     |    50.0%    | ✅ GOOD
isProducerCodeExists           |        2       |    0     |     2     |     0.0%    | ℹ️  NONE
-------------------------------|----------------|----------|-----------|-------------|--------
TOTAL                         |       24       |   11     |    13     |    45.8%    | ✅ EFFECTIVE
```

### 2. Detailed Method Analysis Format
```
METHOD: getFirstPeriodInTermCreateTime_Ext(PolicyPeriod)Date
------------------------------------------------------------
Gosu Source: return this.FirstPeriodInTerm?.CreateTime

ORIGINAL BRANCHES (JaCoCo):
  1. ifnonnull at offset 6 - [null-check]
  2. goto at offset 13 - [business-logic]

FILTERED BRANCHES (GosuNullSafetyFilter):
  1. aload_0 → ifnonnull → aconst_null → checkcast → goto
     Reason: null-safety (null-safe navigation pattern)

SUMMARY:
  Original branches: 2
  Filtered branches: 1
  Remaining branches: 1
  Reduction: 50.0%
```

## Utility Methods

### Branch Detection
```java
private boolean isBranchInstruction(AbstractInsnNode instruction) {
    switch (instruction.getOpcode()) {
        case Opcodes.IFNONNULL:
        case Opcodes.IFNULL:
        case Opcodes.IFEQ:
        case Opcodes.IFNE:
        case Opcodes.IFLT:
        case Opcodes.IFGE:
        case Opcodes.IFGT:
        case Opcodes.IFLE:
        case Opcodes.IF_ICMPEQ:
        case Opcodes.IF_ICMPNE:
        case Opcodes.IF_ICMPLT:
        case Opcodes.IF_ICMPGE:
        case Opcodes.IF_ICMPGT:
        case Opcodes.IF_ICMPLE:
        case Opcodes.IF_ACMPEQ:
        case Opcodes.IF_ACMPNE:
        case Opcodes.GOTO:
        case Opcodes.LOOKUPSWITCH:
        case Opcodes.TABLESWITCH:
            return true;
        default:
            return false;
    }
}
```

### Opcode Name Mapping
```java
private String getOpcodeName(int opcode) {
    switch (opcode) {
        case Opcodes.IFNONNULL: return "ifnonnull";
        case Opcodes.IFNULL: return "ifnull";
        case Opcodes.ACONST_NULL: return "aconst_null";
        case Opcodes.CHECKCAST: return "checkcast";
        case Opcodes.GOTO: return "goto";
        case Opcodes.ALOAD: return "aload";
        case Opcodes.INVOKEVIRTUAL: return "invokevirtual";
        case Opcodes.INVOKEINTERFACE: return "invokeinterface";
        case Opcodes.INVOKESTATIC: return "invokestatic";
        case Opcodes.ATHROW: return "athrow";
        case Opcodes.NEW: return "new";
        case Opcodes.DUP: return "dup";
        case Opcodes.INVOKESPECIAL: return "invokespecial";
        default: return "opcode_" + opcode;
    }
}
```

### Method Name Formatting
```java
private String getReadableMethodName(MethodNode method) {
    String name = method.name;
    String desc = method.desc;
    
    // Remove package and parameter types for readability
    if (desc.startsWith("(Lentity/")) {
        return name + "(PolicyPeriod)";
    } else if (desc.startsWith("()")) {
        return name + "()";
    }
    
    return name + desc;
}
```

### Branch Type Classification
```java
private String getBranchType(AbstractInsnNode instruction, MethodNode method) {
    // Look at surrounding instructions to determine branch type
    AbstractInsnNode prev = instruction.getPrevious();
    AbstractInsnNode next = instruction.getNext();
    
    if (prev != null && prev.getOpcode() == Opcodes.ALOAD) {
        return "null-check";
    } else if (instruction.getOpcode() == Opcodes.GOTO) {
        return "business-logic";
    }
    
    return "conditional";
}
```

## Verification Assertions

### Filter Effectiveness Verification
```java
private void verifyFilterEffectiveness(int totalOriginal, int totalFiltered, List<MethodAnalysis> analyses) {
    // Overall effectiveness
    assertTrue(totalOriginal > 0, "Should have some original branches");
    assertTrue(totalFiltered > 0, "Should filter some branches");
    
    double overallReduction = (double) totalFiltered / totalOriginal * 100;
    assertTrue(overallReduction >= 15.0, "Should reduce at least 15% of branches overall");
    
    // Method-specific verification
    long methodsWithReduction = analyses.stream()
        .filter(a -> a.filteredBranches > 0)
        .count();
    
    assertTrue(methodsWithReduction > 0, "At least one method should have branch reduction");
    
    // Specific method expectations based on bytecode analysis
    for (MethodAnalysis analysis : analyses) {
        if (analysis.methodName.contains("getFirstPeriodInTermCreateTime")) {
            assertTrue(analysis.filteredBranches >= 1, "Simple null-safe should be filtered");
        } else if (analysis.methodName.contains("getAvailableBrandConcepts")) {
            assertTrue(analysis.filteredBranches >= 4, "Complex method should have multiple filters");
        }
    }
}
```

## Expected Results Based on Bytecode Analysis

### Method: getFirstPeriodInTermCreateTime_Ext
- **Original branches**: 2 (ifnonnull, goto)
- **Expected filtered**: 1 (null-safe navigation pattern)
- **Expected remaining**: 1
- **Expected reduction**: 50.0%

### Method: getAvailableBrandConceptsForProdCode
- **Original branches**: ~12 (multiple null checks and logic branches)
- **Expected filtered**: ~6 (multiple null-safety patterns)
- **Expected remaining**: ~6
- **Expected reduction**: ~50.0%

### Method: getFirstPeriodProducerCodeName
- **Original branches**: ~8 (chained null-safe operations)
- **Expected filtered**: ~4 (multiple null-safety patterns)
- **Expected remaining**: ~4
- **Expected reduction**: ~50.0%

### Method: isProducerCodeExists
- **Original branches**: 2 (ifnull, goto)
- **Expected filtered**: 0 (simple null check, not null-safe pattern)
- **Expected remaining**: 2
- **Expected reduction**: 0.0%

### Overall Expected Results
- **Total original branches**: ~24
- **Total filtered branches**: ~11
- **Total remaining branches**: ~13
- **Overall reduction**: ~45.8%
- **Effectiveness status**: ✅ EFFECTIVE

## Implementation Notes

1. **Class Loading**: Use `getResourceAsStream()` to load from test resources
2. **Branch Counting**: Simulate JaCoCo's branch detection by counting all conditional jumps
3. **Filter Application**: Apply `GosuNullSafetyFilter` and track ignored ranges
4. **Output Formatting**: Use console output with aligned tables for readability
5. **Assertions**: Verify both overall effectiveness and method-specific expectations

## Test Execution

The test will produce detailed console output showing:
1. Overall comparison table with summary statistics
2. Method-by-method detailed analysis
3. Branch pattern identification
4. Filter effectiveness verification

This comprehensive test demonstrates the practical effectiveness of the `GosuNullSafetyFilter` in reducing JaCoCo branch coverage noise from Gosu compiler-generated null-safety checks.