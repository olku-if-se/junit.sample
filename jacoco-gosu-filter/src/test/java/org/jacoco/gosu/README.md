# Gosu Null Safety Filter Unit Tests

This directory contains comprehensive unit tests for the `GosuNullSafetyFilter` that verify its behavior against actual compiled Gosu bytecode.

## Test Files

### 1. `GosuNullSafetyFilterTest.java`
**Basic unit tests** that verify the filter can process real bytecode from `PolicyPeriodEnhancement.class`.

**Key Features**:
- Loads actual compiled `PolicyPeriodEnhancement.class` from the build directory
- Tests each method individually for pattern detection
- Verifies ignored bytecode ranges
- Mock `IFilterOutput` and `IFilterContext` for isolated testing

### 2. `GosuNullSafetyFilterDetailedTest.java`
**Deep analysis tests** that perform comprehensive bytecode pattern analysis.

**Key Features**:
- Detailed bytecode instruction analysis
- Pattern sequence verification
- Exact instruction matching for null-safe navigation
- Comprehensive reporting of detected patterns
- Method-by-method breakdown of filter effectiveness

### 3. `FilterIntegrationTest.java`
**Integration tests** that verify the complete setup works end-to-end.

**Key Features**:
- Prerequisites verification (Java version, class availability)
- Integration setup validation
- Basic filter instantiation tests
- Environment validation

## Prerequisites for Running Tests

### 1. Compile the Gosu Source Code
The tests require the compiled `PolicyPeriodEnhancement.class` to be available:

```bash
# From the root project directory
./gradlew compileGosu

# Verify the class exists
ls -la build/classes/gosu/main/enhancement/PolicyPeriodEnhancement.class
```

### 2. Java Version
Tests require Java 11+:

```bash
java -version
# Should show Java 11, 17, or 21
```

### 3. Build Dependencies
Tests will automatically download required dependencies including:
- JUnit 5.10.0
- JaCoCo Core 0.8.14
- ASM libraries 9.7
- Mockito for testing

## Running the Tests

### Basic Test Execution
```bash
# From the jacoco-gosu-filter directory
../gradlew test

# Or from root project directory
./gradlew :jacoco-gosu-filter:test
```

### With Detailed Output
```bash
./gradlew :jacoco-gosu-filter:test --info
```

### Running Specific Test Classes
```bash
# Run basic tests only
./gradlew :jacoco-gosu-filter:test --tests "GosuNullSafetyFilterTest"

# Run detailed analysis tests
./gradlew :jacoco-gosu-filter:test --tests "GosuNullSafetyFilterDetailedTest"

# Run integration tests
./gradlew :jacoco-gosu-filter:test --tests "FilterIntegrationTest"
```

### Running with Debug Mode
```bash
./gradlew :jacoco-gosu-filter:test -Djacoco.gosu.filter.debug=true
```

## Expected Test Output

### Successful Test Run
When tests run successfully, you should see output like:

```
=== COMPREHENSIVE PATTERN ANALYSIS ===
Class: enhancement/PolicyPeriodEnhancement
Methods found: 6

--- Analyzing Method: getFirstPeriodInTermCreateTime_Ext(Lentity/PolicyPeriod;)Ljava/util/Date; ---
Instruction count: 17
Potential null-safe patterns: 2
ALOAD instructions: 4
IFNONNULL instructions: 2
ACONST_NULL instructions: 2
CHECKCAST instructions: 2
Ranges ignored by filter: 4

✓ getFirstPeriodInTermCreateTime_Ext has 2 potential patterns
✓ getAvailableBrandConceptsForProdCode has 4 potential patterns
✓ getFirstPeriodProducerCodeName has 5 potential patterns

Summary:
Total methods analyzed: 5
Methods with patterns: 3
Methods without patterns: 2
Total ignored ranges: 22
```

### Bytecode Pattern Analysis
The detailed test shows exact bytecode sequences:

```
=== EXACT NULL-SAFE BYTECODE SEQUENCES ===
Analyzing bytecode for: getFirstPeriodInTermCreateTime_Ext

Bytecode for getFirstPeriodInTermCreateTime_Ext(Lentity/PolicyPeriod;)Ljava/util/Date;:
  0: aload                 var=0
  1: astore                 var=1
  2: aload                 var=1
  3: ifnonnull             label_12345678
  4: aconst_null
  5: checkcast             java/util/Date
  6: goto                  label_23456789
  7: label_12345678:
  8: aload                 var=1
  9: invokevirtual         entity/PolicyPeriod.getFirstPeriodInTerm()Lentity/PolicyPeriod;
 10: label_23456789:
 11: astore                 var=1
 12: aload                 var=1
 13: ifnonnull             label_34567890
 14: aconst_null
 15: checkcast             java/util/Date
 16: areturn

Pattern 1:
  Start: aload_0
  Sequence: aload_0 → ifnonnull → aconst_null → checkcast → goto → label →aload_1 → invokevirtual
  Variable: 1
  Cast Type: java/util/Date
  Invoke Type: INVOKEVIRTUAL
```

## Understanding the Test Results

### Pattern Detection Verification
The tests verify that the filter correctly identifies:

1. **Null-Safe Navigation Patterns** (`?.` operator):
   ```
   aload X           // Load variable
   ifnonnull label   // If not null, jump to method call
   aconst_null       // Push null for null path
   checkcast Type    // Cast to expected return type
   goto endLabel     // Skip method call
   label:
     aload X         // Load variable again
     invokevirtual   // Call method
   endLabel:
   ```

2. **Defensive Null Check Patterns** (throws NPE):
   ```
   aload X           // Load variable
   ifnonnull label   // If not null, continue
   new NPE           // Create NullPointerException
   dup               // Duplicate
   invokespecial     // Initialize NPE
   athrow            // Throw exception
   label:            // Success path
   ```

### Expected Results
For the `PolicyPeriodEnhancement.gsx` file, the filter should detect:

| Method | Expected Patterns | Type |
|--------|-------------------|------|
| `getFirstPeriodInTermCreateTime_Ext` | 2 | Null-safe navigation |
| `getAvailableBrandConceptsForProdCode` | 4+ | Mixed patterns |
| `getFirstPeriodProducerCodeName` | 5+ | Chained null-safe navigation |
| `isProducerCodeExists` | 0 | Simple null check |

### Ignored Ranges
Each detected pattern results in **2 ignored ranges**:
1. **Null-check branch**: `aload → ifnonnull`
2. **Method-call branch**: `label → invoke`

Total ignored ranges = `detected_patterns × 2`

## Troubleshooting

### Test Fails with "PolicyPeriodEnhancement.class not found"
**Solution**: Compile the Gosu source code first:
```bash
./gradlew compileGosu
```

### Tests Show Zero Patterns Detected
**Possible Causes**:
1. Debug mode not enabled
2. Bytecode doesn't match expected patterns
3. Different Gosu compiler version

**Solutions**:
```bash
# Enable debug mode
./gradlew :jacoco-gosu-filter:test -Djacoco.gosu.filter.debug=true

# Verify bytecode patterns
javap -v -p build/classes/gosu/main/enhancement/PolicyPeriodEnhancement.class
```

### Integration Tests Are Skipped
**Solution**: Enable integration tests:
```bash
./gradlew :jacoco-gosu-filter:test -Drun.integration.tests=true
```

### Java Version Incompatible
**Solution**: Use Java 11+:
```bash
# Check current Java version
java -version

# Switch to compatible Java (example with SDKMAN)
sdk use java 11.0.x-tem
```

## Customizing Tests

### Adding New Test Methods
To test additional patterns, add methods to `GosuNullSafetyFilterTest`:

```java
@Test
@DisplayName("Test custom pattern detection")
void testCustomPattern() {
    MethodNode method = findMethod("yourCustomMethod");
    if (method != null) {
        filter.filter(method, filterContext, filterOutput);
        assertTrue(filterOutput.getIgnoredRanges().size() > 0,
                  "Should detect patterns in custom method");
    }
}
```

### Testing Different Gosu Classes
To test other compiled Gosu classes:

1. Update the class loading paths in `loadPolicyPeriodEnhancementClass()`
2. Add new test methods for the specific class
3. Update expected pattern counts accordingly

## Continuous Integration

### CI Configuration
For CI/CD pipelines, ensure:

1. **Java 11+** is available
2. **Gosu compilation** runs before tests
3. **Class files** are accessible to the test runner

### CI Test Command
```bash
# Complete CI test sequence
./gradlew clean compileGosu :jacoco-gosu-filter:test
```

### CI Validation
Add to CI scripts to verify filter effectiveness:
```bash
# Check that tests detect expected patterns
./gradlew :jacoco-gosu-filter:test --tests "GosuNullSafetyFilterDetailedTest"

# Verify test output contains expected pattern counts
if ! grep -q "Total ignored ranges: [2-9][0-9]" build/test-results/test/*/TEST-*.xml; then
    echo "ERROR: Expected pattern counts not detected"
    exit 1
fi
```

## Performance Considerations

### Test Execution Time
- **Basic tests**: < 1 second
- **Detailed analysis**: 2-5 seconds
- **Full test suite**: < 10 seconds

### Memory Usage
Tests load bytecode into memory:
- **PolicyPeriodEnhancement.class**: ~5KB
- **ASM analysis overhead**: ~10MB
- **Total test memory**: < 50MB

### Optimization Tips
1. Run only required test classes during development
2. Use `--no-daemon` for clean test environments
3. Parallel test execution for large test suites

## Further Development

### Extending Test Coverage
1. Add tests for more complex Gosu patterns
2. Test with different Gosu language features
3. Verify filter works with various JaCoCo versions

### Performance Testing
Add performance benchmarks:
```java
@Test
void testFilterPerformance() {
    long startTime = System.currentTimeMillis();
    filter.filter(method, context, output);
    long duration = System.currentTimeMillis() - startTime;

    assertTrue(duration < 100, "Filter should process method in < 100ms");
}
```

### Regression Testing
Create regression tests for known patterns to prevent future breakages.