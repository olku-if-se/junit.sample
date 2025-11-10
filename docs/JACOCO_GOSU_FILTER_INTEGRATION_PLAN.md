# JaCoCo Gosu Filter Integration Plan

**Created**: 2025-11-10
**Objective**: Complete integration of jacoco-gosu-filter using Java agent and bytecode injection
**Status**: Ready for implementation

---

## Executive Summary

The jacoco-gosu-filter uses a sophisticated Java agent approach to inject Gosu-specific null-safety filtering into
JaCoCo at runtime. This plan provides a comprehensive, step-by-step integration strategy with testing at each phase.

### Enhanced Technical Architecture & Timing Flow

```
┌───────────────────────────────────────────────────────────────────────────────┐
│                           JVM PROCESS STARTUP                                 │
│  java -javaagent:gosu-filter-agent.jar -javaagent:jacocoagent.jar ...         │
└─────────────────────┬───────────────────────┬─────────────────────────────────┘
                      │                       │
           JVM processes agents in order      │
                      ▼                       ▼
┌──────────────────────────────┐  ┌──────────────────────────────┐
│    PHASE 1: AGENT LOADING    │  │  PHASE 2: JACOCO LOADING     │
│                              │  │                              │
│ GosuFilterAgent              │  │ JaCoCo Agent                 │
│ - premain() executes         │  │ - Loads after Gosu agent     │
│ - Registers transformer      │  │ - Sets up instrumentation    │
│ - Logs startup messages      │  │ - Prepares for test execution│
│ - Waits for Filters class    │  │                              │
│                              │  │                              │
│ [GosuFilterAgent] STARTING   │  │ [JaCoCo] Agent initialized   │
│ [GosuFilterAgent] ✓ Reg.     │  │                              │
└─────────────────────┬────────┘  └─────────────┬────────────────┘
                      │                         │
                      ▼                         ▼
           ┌──────────────────────────────────────────────────────┐
           │        PHASE 3: CLASS TRANSFORMATION                 │
           │                                                      │
           │ When JaCoCo's Filters.class is loaded:               │
           │                                                      │
           │ GosuFilterInjector (ClassFileTransformer)            │
           │ - Intercepts Filters.class loading                   │
           │ - Uses ASM to modify bytecode                        │
           │ - Injects createFilter() call                        │
           │ - Modifies allNonKotlinFilters() method              │
           │                                                      │
           │ [GosuFilterInjector] INTERCEPTING FILTERS CLASS      │
           │ [GosuFilterInjector] ✓ BYTECODE INJECTION SUCCESS    │
           └─────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
           ┌──────────────────────────────────────────────────────┐
           │         PHASE 4: JUNIT5 TEST EXECUTION               │
           │                                                      │
           │ JUnit5 Framework starts (after all agents loaded)    │
           │                                                      │
           │ Test Process:                                        │
           │ 1. Test classes loaded normally                      │
           │ 2. Tests execute with modified JaCoCo config         │
           │ 3. JaCoCo collects coverage data during execution    │
           │ 4. Coverage data stored for later analysis           │
           │                                                      │
           │ Note: NO filter application during test execution    │
           └─────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
           ┌──────────────────────────────────────────────────────┐
           │        PHASE 5: COVERAGE ANALYSIS                    │
           │                                                      │
           │ After tests complete, JaCoCo generates reports:      │
           │                                                      │
           │ For each method:                                     │
           │ 1. JaCoCo calls allNonKotlinFilters()                │
           │ 2. Gets FilterSet[2]: SyntheticFilter + GosuFilter   │
           │ 3. Calls each filter.filter() method                 │
           │                                                      │
           │ GosuNullSafetyFilter.filter():                       │
           │ - Analyzes bytecode patterns                         │
           │ - Detects 5 null-safety patterns                     │
           │   • Pattern 1: Null-safe navigation                  │
           │   • Pattern 2: Defensive null checks                 │
           │   • Pattern 3: Simplified null-safe patterns         │
           │   • Pattern 4: Boolean null-safe patterns            │
           │   • Pattern 5: Array creation null-safe patterns     │
           │ - Marks bytecode ranges as ignored                   │
           │                                                      │
           │ [GosuNullSafetyFilter] PATTERN 1 detected            │
           │ [GosuNullSafetyFilter] Ignoring range X-Y            │
           └─────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
           ┌──────────────────────────────────────────────────────┐
           │          PHASE 6: REPORT GENERATION                  │
           │                                                      │
           │ Final JaCoCo Reports:                                │
           │ - HTML report with reduced branch counts             │
           │ - CSV report with filtered statistics                │
           │ - Business logic branches preserved                  │
           │                                                      │
           │ Expected Result:                                     │
           │ • Line 13: 2 branches (reduced from 4)               │
           │ • Total: 31/38 branches covered (81.6%)              │
           │ • Null-safety patterns excluded                      │
           └──────────────────────────────────────────────────────┘
```

**Critical Timing Dependencies:**

| Phase | What Happens                               | When                     | Why Critical                             |
|-------|--------------------------------------------|--------------------------|------------------------------------------|
| 1     | Gosu agent loads and registers transformer | JVM startup              | Must be before JaCoCo agent              |
| 2     | JaCoCo agent loads                         | JVM startup (after Gosu) | Must be after transformer registration   |
| 3     | Filter injection                           | When Filters.class loads | Must be before any coverage analysis     |
| 4     | Test execution                             | After all agents loaded  | Normal test process with modified JaCoCo |
| 5     | Pattern detection                          | During report generation | When JaCoCo processes coverage data      |
| 6     | Branch reduction                           | In final reports         | Result of successful filtering           |

**Loading Order Verification Sequence:**

```
1. [GosuFilterAgent] STARTING GOSU FILTER AGENT
2. [GosuFilterAgent] ✓ Transformer registered successfully
3. [JaCoCo] Agent initialized (if logging enabled)
4. [GosuFilterInjector] INTERCEPTING FILTERS CLASS
5. [GosuFilterInjector] ✓ BYTECODE INJECTION SUCCESSFUL
6. JUnit5 test execution starts
7. [GosuNullSafetyFilter] PATTERN detection (during analysis)
```

---

## Agent Loading Control & JUnit5 Integration

### Critical Agent Loading Order Requirements

**Why Loading Order Matters:**
The GosuFilter agent MUST be loaded before JaCoCo agent to ensure the transformer is registered before
JaCoCo's `Filters` class is loaded. This is controlled through JVM command-line argument order.

**JVM Command Line Order Control:**

```bash
# CORRECT ORDER - Gosu agent loads first
java -javaagent:gosu-filter-agent.jar -javaagent:jacocoagent.jar ...

# INCORRECT ORDER - JaCoCo loads first, injection fails
java -javaagent:jacocoagent.jar -javaagent:gosu-filter-agent.jar ...
```

**How JVM Processes Agents:**

1. JVM processes `-javaagent` arguments in the order they appear on the command line
2. Each agent's `premain()` method executes immediately in that order
3. Agents register transformers with the JVM's `Instrumentation` API
4. When classes are loaded later, all registered transformers can intercept them

**Build Configuration Control:**

```gradle
test {
    dependsOn ':jacoco-gosu-filter:copyAgentJar' // Ensure agent JAR exists

    def filterAgentPath = file("${rootProject.projectDir}/agents/gosu-filter-agent.jar").absolutePath

    doFirst {
        jvmArgs "-javaagent:${filterAgentPath}" // Gosu agent added first
    }

    useJUnitPlatform()

    jacoco {
        enabled = true // JaCoCo agent added automatically after our agent
    }
}
```

**Loading Sequence Assurance:**

1. JVM startup
2. `gosu-filter-agent.jar` loads → `GosuFilterAgent.premain()` executes
3. Transformer registered → waits for JaCoCo `Filters` class
4. `jacocoagent.jar` loads (via JaCoCo plugin)
5. JaCoCo `Filters` class loads → intercepted by our transformer
6. Bytecode injection modifies JaCoCo's filter chain
7. JUnit5 framework starts
8. Tests execute with modified JaCoCo configuration

### JUnit5 Integration & Sandbox Clarification

**JUnit5 Does NOT Run in a Complete Sandbox:**

- JUnit5 tests run in the same JVM process as the application code
- Java agents operate at the JVM level, which is lower than JUnit5's framework
- The agent is loaded during JVM initialization, before any test framework code

**JUnit5 Loading Process:**

```bash
# What actually happens during test execution
./gradlew test
→ Gradle launches new JVM process
→ JVM loads agents in command-line order
→ GosuFilterAgent.premain() executes immediately
→ Transformer registered with Instrumentation API
→ JaCoCo agent loads
→ JUnit5 framework initializes
→ Test classes loaded (potentially intercepted)
→ Tests execute with modified JaCoCo filter chain
```

**Gradle Test Configuration Details:**

```gradle
test {
    dependsOn ':jacoco-gosu-filter:copyAgentJar' // Agent JAR built and copied

    doFirst {
        // JVM arguments set BEFORE test process starts
        jvmArgs "-javaagent:${filterAgentPath}"

        println "JVM Args for test:"
        jvmArgs.each { println "  ${it}" }
    }

    useJUnitPlatform() // JUnit5 starts AFTER agent loading

    // JaCoCo automatically adds its agent after our agent
    jacoco {
        enabled = true
        includes = ['**/*']
    }
}
```

**Why This Works Reliably:**

- `doFirst` block executes before the test JVM is created
- Agent JAR is guaranteed to exist (`dependsOn`)
- JVM arguments are set before process creation
- JUnit5 cannot prevent agent loading because agents operate at JVM level
- Test isolation happens at the framework level, not the JVM level

**Verification Steps:**

```bash
# Check agent loading order in test logs
./gradlew test --rerun-tasks 2>&1 | grep -E "\[GosuFilterAgent\].*STARTING"

# Verify transformer registration
grep -E "\[GosuFilterInjector\].*INJECTION SUCCESSFUL" test.log

# Confirm JUnit5 started after agent
grep -E "Test execution started" test.log
```

### Filter Application Timing: Two-Phase Process

**Phase 1: Filter Registration (During JaCoCo Class Loading)**

```java
// When: JaCoCo's Filters.class is first loaded by JVM
// Where: GosuFilterInjector.transform() method
// What: Bytecode modification of JaCoCo's filter chain

@Override
public byte[] transform(ClassLoader loader, String className, ...) {
    if (FILTERS_CLASS.equals(className)) {
        // Intercept JaCoCo's Filters class during loading
        // Modify allNonKotlinFilters() method bytecode
        // Inject call to GosuFilterInjector.createFilter()
    }
}
```

**Phase 2: Filter Application (During Coverage Analysis)**

```java
// When: After tests complete, during JaCoCo report generation
// Where: GosuNullSafetyFilter.filter() method
// What: Pattern detection and branch exclusion

public void filter(FilterContext context, IFilterOutput output) {
    // Analyze bytecode patterns in each method
    // Detect Gosu null-safety constructs
    // Tell JaCoCo to ignore specific bytecode ranges
    // Reduces branch counts in final coverage report
}
```

**Detailed Timeline:**

1. **Test Execution Phase**: Tests run normally, JaCoCo collects coverage data
2. **Report Generation Phase**: JaCoCo analyzes collected data
3. **Filter Application**: For each method, JaCoCo calls all registered filters
4. **Pattern Detection**: Gosu filter examines bytecode for null-safety patterns
5. **Range Exclusion**: Filter tells JaCoCo to ignore specific instruction ranges
6. **Branch Count Reduction**: Final coverage report shows reduced branch counts

**Key Technical Details:**

- Filter is **NOT** applied during test execution
- Filter is **NOT** applied during JaCoCo instrumentation
- Filter **IS** applied during coverage data analysis
- Uses ASM to examine bytecode patterns after collection
- Implements JaCoCo's `IFilter` interface for standard integration

**Evidence in Integration Plan:**

- Phase 3: Pattern detection logs appear during coverage processing
- Phase 4: Branch reduction visible in final HTML/CSV reports
- Filter timing confirmed by log sequence analysis

---

## Phase 1: Build Infrastructure Setup

### Objective

Establish the build configuration and ensure all components compile successfully.

### Step 1.1: Verify Project Structure

```bash
# Expected directory structure
junit.sample/
├── jacoco-gosu-filter/                # Filter subproject
│   ├── build.gradle                   # Filter build config
│   └── src/main/java/org/jacoco/gosu/
│       ├── GosuFilterAgent.java       # Agent entry point
│       ├── GosuFilterInjector.java    # Bytecode injector
│       └── GosuNullSafetyFilter.java  # Filter implementation
├── agents/                            # Generated directory
│   └── gosu-filter-agent.jar          # Agent JAR (built)
├── src/main/gosu/enhancement/         # Gosu source code
│   └── PolicyPeriodEnhancement.gsx    # Test subject
└── build.gradle                       # Main project config
```

**Verification Commands**:

```bash
# 1. Check all Java files exist
find jacoco-gosu-filter -name "*.java" -type f
# Expected: 3 files found

# 2. Check Gosu test subject exists
ls src/main/gosu/enhancement/PolicyPeriodEnhancement.gsx
# Expected: File exists

# 3. Verify build.gradle contains agent configuration
grep -A 10 -B 2 "javaagent.*gosu-filter-agent" build.gradle
# Expected: jvmArgs "-javaagent:${filterAgentPath}"
```

### Step 1.2: Build Filter Agent

```bash
# Clean build to ensure fresh compilation
./gradlew clean jacoco-gosu-filter:build

# Verify agent JAR is created
ls -la agents/gosu-filter-agent.jar
# Expected: File exists with reasonable size (>50KB)

# Verify JAR contains required classes
jar tf agents/gosu-filter-agent.jar | grep "org/jacoco/gosu"
# Expected: 3 .class files for GosuFilter*

# Verify JAR manifest
jar xf agents/gosu-filter-agent.jar META-INF/MANIFEST.MF
cat META-INF/MANIFEST.MF | grep -E "(Premain-Class|Agent-Class|Can-Redefine)"
# Expected: All three attributes present
```

**Success Criteria**:

- ✓ Agent JAR builds without errors
- ✓ All required Java classes included in JAR
- ✓ Manifest contains proper agent attributes
- ✓ JAR includes ASM dependencies (required for bytecode manipulation)

**Troubleshooting**:

- **Build fails**: Check Java version compatibility (requires Java 11+)
- **Missing classes**: Verify ASM dependencies are included in JAR
- **Manifest issues**: Check build.gradle manifest configuration

---

## Phase 2: Agent Loading Verification

### Objective

Confirm the Java agent loads correctly and initializes all components.

### Step 2.1: Enable Agent Logging

```bash
# Run tests with agent to verify loading
./gradlew test --rerun-tasks 2>&1 | tee test-agent-loading.log

# Check for agent startup logs
grep -E "\[GosuFilterAgent\]" test-agent-loading.log
```

**Expected Log Output**:

```
[GosuFilterAgent] ========================================
[GosuFilterAgent] STARTING GOSU FILTER AGENT
[GosuFilterAgent] ========================================
[GosuFilterAgent] Agent Args: (none)
[GosuFilterAgent] JVM has XXXX classes already loaded
[GosuFilterAgent] Registering Gosu null-safety filter...
[GosuFilterAgent] ✓ Transformer registered successfully
[GosuFilterAgent] Agent installed - waiting for JaCoCo to load...
[GosuFilterAgent] Patterns to detect:
[GosuFilterAgent]   1. Null-safe navigation: aload → ifnonnull → aconst_null → checkcast → goto
[GosuFilterAgent]   2. Defensive null check: aload → ifnonnull → new NPE → athrow
[GosuFilterAgent] ========================================
```

### Step 2.2: Verify Transformer Registration

```bash
# Check for injector class detection logs
grep -E "\[GosuFilterInjector\].*\[FILTER-CLASS\]" test-agent-loading.log | head -10
```

**Expected Output**:

```
[GosuFilterInjector] [FILTER-CLASS] org/jacoco/core/internal/analysis/filter/Filters
[GosuFilterInjector] [FILTER-CLASS] org/jacoco/core/internal/analysis/filter/IFilter
[GosuFilterInjector] [FILTER-CLASS] org/jacoco/core/internal/analysis/filter/FilterSet
```

### Step 2.3: Confirm Bytecode Injection

```bash
# Look for successful injection logs
grep -E "\[GosuFilterInjector\].*(INTERCEPTING|INJECTION SUCCESSFUL)" test-agent-loading.log
```

**Expected Output**:

```
[GosuFilterInjector] ========================================
[GosuFilterInjector] INTERCEPTING FILTERS CLASS
[GosuFilterInjector] ========================================
[GosuFilterInjector] Creating GosuNullSafetyFilter instance...
[GosuFilterInjector] ✓ Filter instance created and stored
[GosuFilterInjector]   → Changing array size from 1 to 2
[GosuFilterInjector]   → Adding GosuNullSafetyFilter via reflection
[GosuFilterInjector]   → Injecting static field access to pre-created filter
[GosuFilterInjector]   ✓ GosuNullSafetyFilter successfully added to filter chain
[GosuFilterInjector] ✓ BYTECODE INJECTION SUCCESSFUL!
[GosuFilterInjector] ========================================
```

**Success Criteria**:

- ✓ Agent loads with all expected log messages
- ✓ Transformer registers without errors
- ✓ JaCoCo filter classes are detected
- ✓ Bytecode injection completes successfully
- ✓ Filter instance is created and stored

**Troubleshooting**:

- **No agent logs**: Check if agent JAR path is correct in build.gradle
- **Transformer registration fails**: Verify Can-Redefine-Classes and Can-Retransform-Classes in manifest
- **No JaCoCo classes detected**: Verify JaCoCo agent loads after Gosu agent
- **Injection fails**: Check ASM library compatibility with JaCoCo version

---

## Phase 3: Pattern Detection Testing

### Objective

Verify the filter successfully detects and ignores Gosu null-safety patterns.

### Step 3.1: Enable Debug Logging

```bash
# Enable filter debug mode
export JAVA_OPTS="-Djacoco.gosu.filter.debug=true"

# Run tests with debug logging
./gradlew test --rerun-tasks 2>&1 | tee test-pattern-detection.log

# Alternative: Add to build.gradle test task
# test.jvmArgs += ["-Djacoco.gosu.filter.debug=true"]
```

### Step 3.2: Verify Pattern Detection

```bash
# Look for Pattern 1 (null-safe navigation) detection
grep -E "\[GosuNullSafetyFilter\].*PATTERN 1" test-pattern-detection.log
```

**Expected Pattern 1 Output**:

```
[GosuNullSafetyFilter] PATTERN 1 (Null-safe navigation) | Method: getFirstPeriodInTermCreateTime_Ext(Lentity/PolicyPeriod;)Ljava/util/Date; | Var: 0 | Cast: Ljava/util/Date; | Invoke: INVOKEVIRTUAL
[GosuNullSafetyFilter] PATTERN 1 (Null-safe navigation) | Method: getAvailableBrandConceptsForProdCode(Lentity/PolicyPeriod;Ljava/lang/String;)Ljava/util/List; | Var: 1 | Cast: Ljava/util/List; | Invoke: INVOKEINTERFACE
[GosuNullSafetyFilter] PATTERN 1 (Null-safe navigation) | Method: getFirstPeriodProducerCodeName(Lentity/PolicyPeriod;)Ljava/lang/String; | Var: 0 | Cast: Ljava/lang/String; | Invoke: INVOKEVIRTUAL
```

### Step 3.3: Verify Defensive Null Check Detection

```bash
# Look for Pattern 2 (defensive null check) detection
grep -E "\[GosuNullSafetyFilter\].*PATTERN 2" test-pattern-detection.log
```

**Expected Pattern 2 Output** (may not appear in current codebase):

```
[GosuNullSafetyFilter] PATTERN 2 (Defensive null check - throws NPE) | Method: someMethod()V | Var: 1
```

### Step 3.4: Verify All Pattern Detection

**Complete Pattern Detection Guide:**

**Pattern 1: Null-safe Navigation**

- **Purpose**: Filters `?.` operator navigation that returns null if target is null
- **Bytecode Sequence**: `aload X → ifnonnull → aconst_null → checkcast → goto`
- **Example**: `return this.FirstPeriodInTerm?.CreateTime`
- **Expected Output**:
  ```
  [GosuNullSafetyFilter] PATTERN 1 (Null-safe navigation) | Method: methodName | Var: X | Cast: Type | Invoke: INVOKE*
  ```

**Pattern 2: Defensive Null Check (NPE Throw)**

- **Purpose**: Filters explicit null checks that throw NPE
- **Bytecode Sequence**: `aload X → ifnonnull → new NPE → dup → invokespecial → athrow`
- **Example**: Gosu-generated defensive programming checks
- **Expected Output**:
  ```
  [GosuNullSafetyFilter] PATTERN 2 (Defensive null check - throws NPE) | Method: methodName | Var: X
  ```

**Pattern 3: Simplified Null-Safe Patterns**

- **Purpose**: Filters simplified null-safe patterns without complex casting
- **Bytecode Sequence**: `aload X → ifnonnull → aconst_null → goto/areturn`
- **Example**: Simple null-safe returns or assignments
- **Expected Output**:
  ```
  [GosuNullSafetyFilter] PATTERN 3 (simplified null-safe) | Method: methodName | Var: X
  ```

**Pattern 4: Boolean Null-Safe Patterns**

- **Purpose**: Filters null-safe boolean operations (like `?.HasElements`)
- **Bytecode Sequence**: `aload X → ifnonnull → iconst_0/iconst_1 → goto`
- **Example**: `collection?.HasElements` evaluations
- **Expected Output**:
  ```
  [GosuNullSafetyFilter] PATTERN 4 (Boolean null-safe) | Method: methodName | Var: X | Const: 0/1
  ```

**Pattern 5: Array Creation Null-Safe Patterns**

- **Purpose**: Filters null-safe array creation patterns
- **Bytecode Sequence**: `aload X → ifnonnull → iconst_0/anewarray → checkcast → goto`
- **Example**: Null-safe array initialization or creation
- **Expected Output**:
  ```
  [GosuNullSafetyFilter] PATTERN 5 (Array null-safe) | Method: methodName | Var: X
  ```

**Pattern Detection Verification Commands:**

```bash
# Check all pattern types
echo "=== Pattern Detection Summary ==="
echo "Pattern 1 (Null-safe navigation):"
grep -c "PATTERN 1.*Null-safe navigation" test-pattern-detection.log || echo "0"

echo "Pattern 2 (Defensive null check):"
grep -c "PATTERN 2.*Defensive null check" test-pattern-detection.log || echo "0"

echo "Pattern 3 (Simplified null-safe):"
grep -c "PATTERN 3.*simplified null-safe\|null-return" test-pattern-detection.log || echo "0"

echo "Pattern 4 (Boolean null-safe):"
grep -c "PATTERN 4.*Boolean null-safe" test-pattern-detection.log || echo "0"

echo "Pattern 5 (Array null-safe):"
grep -c "PATTERN 5.*Array null-safe" test-pattern-detection.log || echo "0"

echo "Total patterns detected:"
grep -c "PATTERN [1-5]" test-pattern-detection.log || echo "0"
```

### Step 3.5: Verify Method Summaries

```bash
# Check per-method filtering summaries
grep -E "\[GosuNullSafetyFilter\].*Method.*patterns.*checks" test-pattern-detection.log || echo "No summaries found (normal if DEBUG disabled)"
```

**Expected Method Summaries** (if enabled):

```
[GosuNullSafetyFilter] Method: getFirstPeriodInTermCreateTime_Ext | Null-safe patterns: 2 | Defensive checks: 0 | Simplified: 0 | Boolean: 0 | Array: 0
[GosuNullSafetyFilter] Method: getAvailableBrandConceptsForProdCode | Null-safe patterns: 4 | Defensive checks: 2 | Simplified: 0 | Boolean: 0 | Array: 0
[GosuNullSafetyFilter] Method: getFirstPeriodProducerCodeName | Null-safe patterns: 5 | Defensive checks: 0 | Simplified: 0 | Boolean: 0 | Array: 0
[GosuNullSafetyFilter] Method: isProducerCodeExists | Null-safe patterns: 0 | Defensive checks: 0 | Simplified: 0 | Boolean: 0 | Array: 0
```

**Note**: The current implementation doesn't output detailed method summaries, but individual pattern detection logs
provide comprehensive coverage information.

**Success Criteria**:

- ✓ Pattern 1 detected in null-safe navigation methods
- ✓ Pattern 2 detected in defensive null checks (if present in codebase)
- ✓ Pattern 3 detected in simplified null-safe patterns
- ✓ Pattern 4 detected in boolean null-safe operations (like `?.HasElements`)
- ✓ Pattern 5 detected in array creation null-safe patterns
- ✓ Variable numbers and cast types correctly identified
- ✓ Invoke types (INVOKEVIRTUAL/INVOKEINTERFACE/INVOKESTATIC) properly recognized
- ✓ Bytecode ranges are marked as ignored
- ✓ Total pattern detection covers all Gosu null-safety constructs

**Troubleshooting**:

- **No pattern detection**: Enable debug mode with `-Djacoco.gosu.filter.debug=true`
- **Incomplete detection**: Check bytecode patterns in generated class files
- **Wrong method signatures**: Verify Gosu compilation compatibility
- **Missing ranges**: Check IFilterOutput implementation

---

## Phase 4: Coverage Report Validation

### Objective

Confirm that branch counts are reduced in JaCoCo reports as expected.

### Step 4.1: Generate Coverage Reports

```bash
# Run tests with coverage
./gradlew clean test jacocoTestReport

# Verify reports are generated
ls -la build/reports/jacoco/test/
# Expected: index.html, jacocoTestReport.csv, jacocoTestReport.xml
```

### Step 4.2: Analyze CSV Report

```bash
# Extract PolicyPeriodEnhancement coverage data
grep "PolicyPeriodEnhancement" build/reports/jacoco/test/jacocoTestReport.csv
```

**Expected CSV Format**:

```
junit.sample,enhancement,PolicyPeriodEnhancement,21,168,7,31,0,5,7,17,0,5
```

**Field Mapping**:

- `INSTRUCTION_MISSED`: 21
- `INSTRUCTION_COVERED`: 168
- `BRANCH_MISSED`: 7
- `BRANCH_COVERED`: 31
- `LINE_MISSED`: 0
- `LINE_COVERED`: 5
- `COMPLEXITY_MISSED`: 7
- `COMPLEXITY_COVERED`: 17
- `METHOD_MISSED`: 0
- `METHOD_COVERED`: 5

### Step 4.3: Verify Branch Reduction

```bash
# Calculate total branches and coverage percentage
grep "PolicyPeriodEnhancement" build/reports/jacoco/test/jacocoTestReport.csv | \
awk -F',' '{print "Branches: " $9+$10 " | Covered: " $10 " | Coverage: " ($10/($9+$10)*100) "%"}'
```

**Expected Results**:

```
Branches: 38 | Covered: 31 | Coverage: 81.6%
```

### Step 4.4: Inspect HTML Report

```bash
# Open HTML report for visual inspection
echo "HTML Report: file://$(pwd)/build/reports/jacoco/test/html/index.html"

# Check specific lines in PolicyPeriodEnhancement
open build/reports/jacoco/test/html/enhancement/PolicyPeriodEnhancement.gsx.html
```

**Expected Line-by-Line Analysis**:
| Line | Code Pattern | Expected Branches | Status |
| ---- | ------------------------------------------------------------------ | ----------------- | ----------------- |
| 13 | `return this.FirstPeriodInTerm?.CreateTime`                        | 2 | ✓ Reduced from 4 |
| 21 | `var brandConcepts = this.ProducerCodeOfRecord?.BrandConcepts_Ext` | 2 | ✓ Covered |
| 22 | `return brandConcepts?.HasElements ? ... : null`                   | 20 | ✓ Complex pattern |
| 30 | Multi-step null-safe chain | 12 | ✓ Filtered |
| 38 | `return this.ProducerCodeOfRecord != null`                         | 2 | ✓ Simple check |

**Success Criteria**:

- ✓ Total branches: 38 (31 covered, 7 missed)
- ✓ Branch coverage: 81.6%
- ✓ Line 13 shows reduced branches (2 instead of 4)
- ✓ All null-safe navigation patterns show reduced counts
- ✓ Business logic branches preserved

**Troubleshooting**:

- **High branch counts**: Verify filter injection was successful
- **No reduction seen**: Check if patterns are being detected
- **Coverage too low**: Verify tests cover all null scenarios
- **Report not generated**: Check JaCoCo agent is loaded

---

## Phase 5: Bytecode Verification

### Objective

Manually verify bytecode patterns match what the filter expects.

### Step 5.1: Locate Compiled Class

```bash
# Find the compiled PolicyPeriodEnhancement class
find build -name "PolicyPeriodEnhancement.class" -type f
# Expected: build/classes/gosu/main/enhancement/PolicyPeriodEnhancement.class
```

### Step 5.2: Inspect Bytecode with javap

```bash
# Disassemble bytecode with verbose output
javap -v -p build/classes/gosu/main/enhancement/PolicyPeriodEnhancement.class > bytecode-analysis.txt

# Look for null-safe navigation patterns
grep -B1 -A3 "ifnonnull" bytecode-analysis.txt | head -30
```

**Expected Pattern 1 Bytecode**:

```bytecode
aload_0
ifnonnull L1          // Null check
aconst_null           // Push null for null path
checkcast java/util/Date
goto L2
L1:
aload_0
invokevirtual entity/PolicyPeriod.getFirstPeriodInTerm()Lentity/PolicyPeriod;
L2:
```

### Step 5.3: Count Pattern Instructions

```bash
# Count ALOAD instructions (potential null checks)
grep -c "aload" bytecode-analysis.txt

# Count IFNONNULL instructions (null checks)
grep -c "ifnonnull" bytecode-analysis.txt

# Count ACONST_NULL instructions (null path)
grep -c "aconst_null" bytecode-analysis.txt

# Count CHECKCAST instructions (type casting)
grep -c "checkcast" bytecode-analysis.txt
```

**Expected Instruction Counts**:

- `aload`: Multiple occurrences (variable loads)
- `ifnonnull`: 4-6 occurrences (null checks)
- `aconst_null`: 4-6 occurrences (null returns)
- `checkcast`: 4-6 occurrences (type casts)

### Step 5.4: Method-by-Method Analysis

```bash
# Extract specific method bytecode
sed -n '/getFirstPeriodInTermCreateTime_Ext/,/getAvailableBrandConceptsForProdCode/p' bytecode-analysis.txt | head -40

# Extract complex method bytecode
sed -n '/getFirstPeriodProducerCodeName/,/isProducerCodeExists/p' bytecode-analysis.txt | head -50
```

**Success Criteria**:

- ✓ Bytecode contains expected null-safe patterns
- ✓ Pattern sequences match filter detection logic
- ✓ Instruction counts align with pattern expectations
- ✓ Method signatures match Gosu source

**Troubleshooting**:

- **No patterns found**: Re-compile Gosu source code
- **Different bytecode**: Check Gosu compiler version
- **Unexpected instructions**: Verify filter pattern matching
- **Missing methods**: Clean build and recompile

---

## Phase 6: Production Integration Testing

### Objective

Test the complete integration in a production-like environment.

### Step 6.1: Clean Integration Test

```bash
# Complete clean build and test
./gradlew clean
rm -rf agents/ build/
./gradlew test jacocoTestReport

# Verify all components working
grep -q "\[GosuFilterAgent\].*STARTING" build/test-results/test/*.xml && echo "✓ Agent loaded" || echo "✗ Agent failed"
grep -q "\[GosuFilterInjector\].*INJECTION SUCCESSFUL" build/test-results/test/*.xml && echo "✓ Injection successful" || echo "✗ Injection failed"
```

### Step 6.2: Performance Impact Assessment

```bash
# Run tests multiple times to measure performance impact
time ./gradlew test > performance-test.log 2>&1
time ./gradlew test > performance-test2.log 2>&1
time ./gradlew test > performance-test3.log 2>&1

# Average test execution time
echo "Performance times:"
grep "BUILD SUCCESSFUL" performance-test*.log | wc -l
```

### Step 6.3: CI/CD Integration Simulation

```bash
# Simulate CI environment (clean build every time)
export CI=true
export JAVA_HOME=$(echo $JAVA_HOME)  # Use system Java

for i in {1..3}; do
  echo "=== CI Run $i ==="
  ./gradlew clean test jacocoTestReport

  # Verify coverage stability
  COVERAGE=$(grep "PolicyPeriodEnhancement" build/reports/jacoco/test/jacocoTestReport.csv | awk -F',' '{print $10}')
  echo "Coverage: $COVERAGE branches covered"

  # Verify filter logs present
  LOG_COUNT=$(grep -c "\[Gosu" build/test-results/test/*.txt 2>/dev/null || echo "0")
  echo "Filter log entries: $LOG_COUNT"
done
```

**Success Criteria**:

- ✓ Consistent test results across multiple runs
- ✓ Stable coverage metrics (±5% variance)
- ✓ Filter logs appear in every test run
- ✓ No performance degradation (>10% slower)
- ✓ Clean builds work consistently

**Troubleshooting**:

- **Inconsistent results**: Check for race conditions in agent loading
- **Performance issues**: Profile agent initialization overhead
- **Coverage variance**: Verify test isolation and cleanup
- **Missing logs**: Check agent JAR loading in CI environment

---

## Verification Checklist

### Pre-Integration Prerequisites

- [ ] Java 11+ installed and JAVA_HOME set
- [ ] Gradle 8.0+ configured
- [ ] Gosu compiler plugin integrated
- [ ] JaCoCo plugin configured in build.gradle
- [ ] Source code contains null-safe navigation patterns

### Build Verification

- [ ] Agent JAR builds successfully: `./gradlew jacoco-gosu-filter:build`
- [ ] Agent JAR contains all required classes
- [ ] Manifest includes proper agent attributes
- [ ] ASM dependencies bundled in JAR
- [ ] Agent JAR copied to `agents/` directory

### Agent Loading Verification

- [ ] `[GosuFilterAgent]` startup logs appear
- [ ] Transformer registered successfully
- [ ] JaCoCo filter classes detected
- [ ] `[GosuFilterInjector]` intercepts Filters class
- [ ] Bytecode injection completes without errors

### Pattern Detection Verification

- [ ] Pattern 1 (null-safe navigation) detected
- [ ] Pattern 2 (defensive null checks) detected (if present in codebase)
- [ ] Pattern 3 (simplified null-safe patterns) detected
- [ ] Pattern 4 (boolean null-safe patterns) detected
- [ ] Pattern 5 (array creation null-safe patterns) detected
- [ ] Correct variable numbers identified
- [ ] Cast types properly extracted
- [ ] Invoke types correctly recognized
- [ ] Bytecode ranges marked as ignored

### Coverage Report Verification

- [ ] JaCoCo reports generated successfully
- [ ] Branch counts reduced (line 13: 2 branches)
- [ ] Total coverage: 81.6% (31/38 branches)
- [ ] Business logic branches preserved
- [ ] HTML report visually confirms filtering

### Production Readiness

- [ ] Multiple clean builds succeed
- [ ] Performance impact minimal (<10%)
- [ ] CI/CD integration stable
- [ ] Documentation complete
- [ ] Team training materials prepared

---

## Troubleshooting Guide

### Common Issues and Solutions

#### Issue: Agent Fails to Load

**Symptoms**: No `[GosuFilterAgent]` logs in test output
**Causes**:

- Agent JAR not found
- Incorrect JAR path in build.gradle
- Missing manifest attributes

**Solutions**:

```bash
# 1. Verify JAR exists and is accessible
ls -la agents/gosu-filter-agent.jar

# 2. Check manifest attributes
jar tf agents/gosu-filter-agent.jar META-INF/MANIFEST.MF
cat META-INF/MANIFEST.MF | grep -E "(Premain|Agent|Can-Redefine)"

# 3. Verify build.gradle configuration
grep -A 5 -B 5 "javaagent.*gosu-filter-agent" build.gradle
```

#### Issue: No Pattern Detection

**Symptoms**: No `[GosuNullSafetyFilter]` pattern logs
**Causes**:

- Debug mode disabled
- Injection failed silently
- Bytecode patterns don't match

**Solutions**:

```bash
# 1. Enable debug mode
export JAVA_OPTS="-Djacoco.gosu.filter.debug=true"

# 2. Verify injection succeeded
grep "\[GosuFilterInjector\].*INJECTION" test.log

# 3. Inspect actual bytecode
javap -v -p build/classes/gosu/main/enhancement/PolicyPeriodEnhancement.class
```

#### Issue: High Branch Counts

**Symptoms**: Coverage report shows high branch counts (no reduction)
**Causes**:

- Filter not registered with JaCoCo
- Pattern detection failing
- Wrong bytecode instructions

**Solutions**:

```bash
# 1. Check filter injection logs
grep "\[GosuFilterInjector\].*successful" test.log

# 2. Verify pattern detection
grep "\[GosuNullSafetyFilter\].*PATTERN" test.log

# 3. Compare expected vs actual bytecode
# (See Phase 5: Bytecode Verification)
```

#### Issue: Build Failures

**Symptoms**: Gradle build fails with compilation errors
**Causes**:

- Java version incompatibility
- Missing dependencies
- ASM version conflicts

**Solutions**:

```bash
# 1. Check Java version
java -version  # Should be 11+

# 2. Verify dependencies
./gradlew jacoco-gosu-filter:dependencies

# 3. Clean rebuild
./gradlew clean build --refresh-dependencies
```

#### Issue: Performance Degradation

**Symptoms**: Tests run significantly slower with agent
**Causes**:

- Agent initialization overhead
- Excessive logging
- Inefficient bytecode transformation

**Solutions**:

```bash
# 1. Reduce logging
export JAVA_OPTS="-Djacoco.gosu.filter.debug=false"

# 2. Profile test execution
time ./gradlew test

# 3. Check agent initialization time
grep "\[GosuFilterAgent\]" test.log | head -10
```

### Debug Mode Commands

```bash
# Full debug mode
export JAVA_OPTS="-Djacoco.gosu.filter.debug=true"

# Run with maximum logging
./gradlew test --info --rerun-tasks 2>&1 | tee debug-full.log

# Filter specific log types
grep -E "\[Gosu(FilterAgent|FilterInjector|NullSafetyFilter)\]" debug-full.log

# Extract injection details
grep -A 20 -B 5 "INTERCEPTING FILTERS CLASS" debug-full.log
```

---

## Success Metrics

### Quantitative Metrics

| Metric                 | Target | Measurement Method         |
|------------------------|--------|----------------------------|
| Agent Loading Success  | 100%   | Log verification           |
| Pattern Detection Rate | >95%   | Debug log analysis         |
| Branch Reduction       | 40-60% | Coverage report comparison |
| Performance Impact     | <10%   | Test timing comparison     |
| Build Success Rate     | 100%   | CI/CD pass rate            |

### Qualitative Metrics

- **Visibility**: Complete logging of all filter operations
- **Reliability**: Consistent behavior across multiple runs
- **Maintainability**: Clear code structure and documentation
- **Usability**: Easy integration with existing projects

### Validation Commands

```bash
# Complete validation script
echo "=== JaCoCo Gosu Filter Integration Validation ==="

echo "1. Agent Loading Check..."
./gradlew test --rerun-tasks 2>&1 | grep -q "\[GosuFilterAgent\].*STARTING" && echo "✓ Agent loads" || echo "✗ Agent failed"

echo "2. Injection Success Check..."
./gradlew test --rerun-tasks 2>&1 | grep -q "\[GosuFilterInjector\].*INJECTION SUCCESSFUL" && echo "✓ Injection successful" || echo "✗ Injection failed"

echo "3. Pattern Detection Check..."
JAVA_OPTS="-Djacoco.gosu.filter.debug=true" ./gradlew test --rerun-tasks 2>&1 | grep -q "\[GosuNullSafetyFilter\].*PATTERN 1" && echo "✓ Patterns detected" || echo "✗ No patterns detected"

echo "4. Coverage Reduction Check..."
./gradlew test jacocoTestReport 2>/dev/null
COVERAGE=$(grep "PolicyPeriodEnhancement" build/reports/jacoco/test/jacocoTestReport.csv | awk -F',' '{print $10}')
[ "$COVERAGE" = "31" ] && echo "✓ Expected coverage (31 branches)" || echo "✗ Unexpected coverage ($COVERAGE branches)"

echo "5. Performance Check..."
TIME1=$(time (./gradlew test 2>/dev/null) 2>&1 | grep real | awk '{print $2}')
TIME2=$(time (./gradlew test 2>/dev/null) 2>&1 | grep real | awk '{print $2}')
echo "Times: $TIME1 vs $TIME2"

echo "=== Validation Complete ==="
```

---

## Implementation Timeline

### Day 1: Infrastructure Setup

- Morning: Phase 1 - Build infrastructure verification
- Afternoon: Phase 2 - Agent loading testing

### Day 2: Core Functionality

- Morning: Phase 3 - Pattern detection verification
- Afternoon: Phase 4 - Coverage report validation

### Day 3: Quality Assurance

- Morning: Phase 5 - Bytecode verification
- Afternoon: Phase 6 - Production integration testing

### Day 4: Documentation & Handoff

- Morning: Complete documentation review
- Afternoon: Team training and knowledge transfer

---

## Conclusion

This integration plan provides a comprehensive, step-by-step approach to successfully integrate the jacoco-gosu-filter
using Java agent injection and bytecode manipulation. The plan includes:

1. **Progressive Verification**: Each phase builds on the previous one with clear success criteria
2. **Complete Testing**: Agent loading, pattern detection, coverage reduction, and production readiness
3. **Troubleshooting Guide**: Common issues with specific solutions and debug commands
4. **Success Metrics**: Quantitative and qualitative measures of successful integration

Following this plan will ensure reliable integration of the Gosu null-safety filter, providing accurate code coverage
metrics for Gosu codebases by filtering out compiler-generated null-safety checks while preserving meaningful business
logic branches.

**Next Steps**: Begin with Phase 1 and proceed sequentially through each phase, verifying success criteria before
continuing to the next phase.