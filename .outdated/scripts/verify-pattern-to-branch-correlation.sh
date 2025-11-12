#!/bin/bash

# E2E Pattern Detection to Branch Reduction Correlation
# This script verifies that detected patterns actually result in branch reduction

set -e

echo "=== PATTERN-TO-BRANCH REDUCTION CORRELATION VERIFICATION ==="

# Test 1: Baseline measurement without filter
echo "1. Creating baseline measurement (without filter)..."

# Temporarily disable filter agent
./gradlew clean test jacocoTestReport -x :jacoco-gosu-filter:copyAgentJar 2>&1 | tee baseline-test.log

if [ -f "build/reports/jacoco/test/jacocoTestReport.csv" ]; then
    grep "PolicyPeriodEnhancement" build/reports/jacoco/test/jacocoTestReport.csv > baseline-coverage.csv
    echo "✓ Baseline coverage generated"
else
    echo "✗ Baseline coverage failed"
    exit 1
fi

# Test 2: Pattern detection with filter enabled
echo "2. Running with pattern detection enabled..."

export JAVA_OPTS="-Djacoco.gosu.filter.debug=true -Djacoco.gosu.filter.correlation=true"

./gradlew clean test jacocoTestReport 2>&1 | tee filtered-test.log

if [ -f "build/reports/jacoco/test/jacocoTestReport.csv" ]; then
    grep "PolicyPeriodEnhancement" build/reports/jacoco/test/jacocoTestReport.csv > filtered-coverage.csv
    echo "✓ Filtered coverage generated"
else
    echo "✗ Filtered coverage failed"
    exit 1
fi

# Test 3: Extract pattern detection information
echo "3. Analyzing pattern detection logs..."

# Extract pattern detection by method
echo "Pattern Detection Analysis:" > pattern-analysis.log
echo "===========================" >> pattern-analysis.log

# Count patterns per method
for method in "getFirstPeriodInTermCreateTime_Ext" "getAvailableBrandConceptsForProdCode" "getFirstPeriodProducerCodeName"; do
    pattern_count=$(grep -c "PATTERN.*$method" filtered-test.log 2>/dev/null || echo "0")
    echo "$method: $pattern_count patterns detected" >> pattern-analysis.log

    # Show sample patterns for this method
    if [ "$pattern_count" -gt 0 ]; then
        echo "  Sample patterns:" >> pattern-analysis.log
        grep "PATTERN.*$method" filtered-test.log | head -3 | sed 's/^/    /' >> pattern-analysis.log
    fi
    echo "" >> pattern-analysis.log
done

# Test 4: Compare coverage metrics
echo "4. Comparing coverage metrics..."

echo "Coverage Comparison:" > coverage-comparison.log
echo "====================" >> coverage-comparison.log

if [ -f "baseline-coverage.csv" ] && [ -f "filtered-coverage.csv" ]; then
    baseline_data=$(cat baseline-coverage.csv)
    filtered_data=$(cat filtered-coverage.csv)

    echo "Baseline: $baseline_data" >> coverage-comparison.log
    echo "Filtered: $filtered_data" >> coverage-comparison.log

    # Extract branch counts
    baseline_branches=$(echo "$baseline_data" | cut -d',' -f9)
    baseline_covered=$(echo "$baseline_data" | cut -d',' -f10)
    filtered_branches=$(echo "$filtered_data" | cut -d',' -f9)
    filtered_covered=$(echo "$filtered_data" | cut -d',' -f10)

    branch_reduction=$((baseline_branches - filtered_branches))
    reduction_percentage=$(echo "scale=2; $branch_reduction * 100 / $baseline_branches" | bc -l 2>/dev/null || echo "0")

    echo "Branch Analysis:" >> coverage-comparison.log
    echo "  Total branches (baseline): $baseline_branches" >> coverage-comparison.log
    echo "  Total branches (filtered): $filtered_branches" >> coverage-comparison.log
    echo "  Branches reduced: $branch_reduction" >> coverage-comparison.log
    echo "  Reduction percentage: ${reduction_percentage}%" >> coverage-comparison.log

    echo "✓ Coverage comparison complete"
else
    echo "✗ Coverage data missing for comparison"
    exit 1
fi

# Test 5: Method-by-method correlation analysis
echo "5. Creating method-by-method correlation..."

# This would require method-level coverage analysis from JaCoCo
# For now, we'll create a framework for this analysis

cat > CorrelationAnalysis.java << 'EOF'
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class CorrelationAnalysis {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java CorrelationAnalysis <PolicyPeriodEnhancement.class>");
            System.exit(1);
        }

        byte[] bytecode = Files.readAllBytes(Paths.get(args[0]));
        ClassReader reader = new ClassReader(bytecode);
        MethodAnalyzer analyzer = new MethodAnalyzer();
        reader.accept(analyzer, 0);

        System.out.println("METHOD ANALYSIS FOR NULL-SAFETY PATTERNS:");
        System.out.println("==========================================");

        for (Map.Entry<String, MethodInfo> entry : analyzer.methods.entrySet()) {
            MethodInfo info = entry.getValue();
            System.out.println("Method: " + entry.getKey());
            System.out.println("  IFNONNULL instructions: " + info.nullCheckCount);
            System.out.println("  ACONST_NULL instructions: " + info.nullReturnCount);
            System.out.println("  Potential null-safe patterns: " + info.potentialPatterns);
            System.out.println();
        }
    }

    static class MethodAnalyzer extends ClassVisitor {
        Map<String, MethodInfo> methods = new HashMap<>();

        public MethodAnalyzer() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                       String signature, String[] exceptions) {
            MethodInfo info = new MethodInfo(name);
            methods.put(name, info);

            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitInsn(int opcode) {
                    if (opcode == Opcodes.IFNONNULL || opcode == Opcodes.IFNULL) {
                        info.nullCheckCount++;
                    } else if (opcode == Opcodes.ACONST_NULL) {
                        info.nullReturnCount++;
                    }
                    super.visitInsn(opcode);
                }

                @Override
                public void visitEnd() {
                    // Estimate potential null-safe patterns
                    info.potentialPatterns = Math.min(info.nullCheckCount, info.nullReturnCount);
                    super.visitEnd();
                }
            };
        }
    }

    static class MethodInfo {
        String name;
        int nullCheckCount = 0;
        int nullReturnCount = 0;
        int potentialPatterns = 0;

        MethodInfo(String name) {
            this.name = name;
        }
    }
}
EOF

# Run correlation analysis if PolicyPeriodEnhancement.class exists
if [ -f "build/classes/gosu/main/enhancement/PolicyPeriodEnhancement.class" ]; then
    javac -cp "$(find ~/.gradle -name 'asm*.jar' | tr '\n' ':')" CorrelationAnalysis.java
    java -cp ".:$(find ~/.gradle -name 'asm*.jar' | tr '\n' ':')" \
         CorrelationAnalysis build/classes/gosu/main/enhancement/PolicyPeriodEnhancement.class > method-analysis.log
    echo "✓ Method analysis complete"
else
    echo "⚠ PolicyPeriodEnhancement.class not found for method analysis"
fi

# Test 6: Generate correlation report
echo "6. Generating correlation report..."

cat > e2e-correlation-report.md << EOF
# E2E Pattern Detection to Branch Reduction Correlation Report

## Test Execution Summary

This report correlates pattern detection with actual branch reduction in JaCoCo coverage reports.

### 1. Pattern Detection Results

$(cat pattern-analysis.log)

### 2. Coverage Comparison Results

$(cat coverage-comparison.log)

### 3. Method Analysis Results

$(cat method-analysis.log 2>/dev/null || echo "Method analysis not available")

### 4. Correlation Verification

#### Success Criteria:
- [ ] Pattern detection logs show null-safety patterns
- [ ] Branch count reduced in filtered vs baseline coverage
- [ ] Method analysis shows potential patterns match detection
- [ ] Reduction percentage is significant (>15%)

#### Results:
- **Pattern Detection**: $(grep -c "PATTERN" filtered-test.log 2>/dev/null || echo "0") patterns detected
- **Branch Reduction**: ${branch_reduction:-0} branches reduced (${reduction_percentage:-0}%)
- **Filter Injection**: $(grep -c "INJECTION SUCCESSFUL" filtered-test.log 2>/dev/null || echo "0") successful injections

### 5. Conclusion

The correlation analysis shows whether detected patterns actually result in branch reduction.

**Status**: $([ "${branch_reduction:-0}" -gt 0 ] && echo "SUCCESS - Branch reduction confirmed" || echo "FAILED - No branch reduction detected")

EOF

echo "✓ Correlation analysis complete"
echo "Report generated: e2e-correlation-report.md"

unset JAVA_OPTS