#!/usr/bin/env bash

# Simple test script to see enhanced filter pattern detection

echo "=== Testing Enhanced Gosu Null Safety Filter ==="
echo

# Clean and compile
./gradlew clean :jacoco-gosu-filter:compileJava :compileGosu > /dev/null 2>&1

# Run a test with explicit debug output
echo "Running enhanced filter with debug mode..."
echo

java -cp \
  "jacoco-gosu-filter/build/classes/java/main:jacoco-gosu-filter/build/resources/main" \
  "-Djacoco.gosu.filter.debug=true" \
  org.jacoco.gosu.GosuNullSafetyFilter 2>&1 | head -50 || \
echo "Direct execution failed, trying through Gradle test..."

# Alternative: Run through Gradle with explicit output capture
echo
echo "=== Running through Gradle test ==="
echo

./gradlew :jacoco-gosu-filter:test --tests "*BranchAnalysisTest.testAvailableBrandConceptsForProdCodeBranchReduction" \
  --rerun-tasks \
  -Djacoco.gosu.filter.debug=true \
  2>&1 | grep -E "(PATTERN|BRANCH|IGNORED|REDUCTION)" || echo "No debug output captured"

echo
echo "=== Test Complete ==="